/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.convx.music.constants.LocalExcludedFoldersKey
import com.convx.music.db.LocalSongScanRow
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.AlbumArtistMap
import com.convx.music.db.entities.AlbumEntity
import com.convx.music.db.entities.ArtistEntity
import com.convx.music.db.entities.SongAlbumMap
import com.convx.music.db.entities.SongArtistMap
import com.convx.music.db.entities.SongEntity
import com.convx.music.library.scan.AlbumAggregate
import com.convx.music.library.scan.ExistingLocalSongSnapshot
import com.convx.music.library.scan.LocalScanPhase
import com.convx.music.library.scan.LocalScanPlan
import com.convx.music.library.scan.LocalScanPlanner
import com.convx.music.library.scan.LocalScanProgress
import com.convx.music.library.scan.NormalizedLocalTrack
import com.convx.music.library.scan.ScannedMediaStoreTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime

/**
 * MediaStore scanner and batched incremental Room importer for local audio files.
 */
object LocalAudioScanner {
    private const val TAG = "LocalAudioScanner"
    private const val MIN_DURATION_MS = 3_000L
    internal const val DB_BATCH_SIZE = 500

    data class ScanResult(
        val totalFound: Int,
        val newSongs: Int,
        val skippedExisting: Int,
        val updatedSongs: Int = 0,
        val movedSongs: Int = 0,
        val removedSongs: Int = 0,
        val inaccessibleCount: Int = 0,
        val duplicatesSkipped: Int = 0,
    )

    suspend fun scanAndInsert(
        context: Context,
        database: MusicDatabase,
        onProgress: ((LocalScanProgress) -> Unit)? = null,
    ): ScanResult = withContext(Dispatchers.IO) {
        onProgress?.invoke(LocalScanProgress(phase = LocalScanPhase.QUERYING_MEDIASTORE))

        val excludedFolders = runCatching {
            val raw = context.dataStore.data.first()[LocalExcludedFoldersKey].orEmpty()
            decodeExcludedFolders(raw)
        }.getOrDefault(emptySet())

        val (rawTracks, queryHadErrors) = queryMediaStoreTracks(context)
        currentCoroutineContext().ensureActive()

        onProgress?.invoke(
            LocalScanProgress(
                phase = LocalScanPhase.PLANNING_CHANGES,
                processed = 0,
                total = rawTracks.size,
            ),
        )

        val existingRows = database.localSongScanRows()
        val existingSnapshots = existingRows.map { it.toSnapshot() }

        // Check readability on new or changed tracks via ContentResolver / File descriptor
        // rather than opening thousands of unchanged URIs on every incremental scan.
        val existingById = existingSnapshots.associateBy { it.id }
        val checkedTracks = rawTracks.map { row ->
            currentCoroutineContext().ensureActive()
            val prev = existingById[row.contentUri]
            val isUnchangedQuick = prev != null &&
                prev.fileSize == row.fileSize &&
                prev.dateModified?.let { it.epochSecondCompat() == row.dateModifiedSec } == true
            if (isUnchangedQuick) {
                row
            } else {
                row.copy(isReadable = checkTrackAccessible(context.contentResolver, row))
            }
        }

        val plan = LocalScanPlanner.plan(
            rawTracks = checkedTracks,
            existingSnapshots = existingSnapshots,
            excludedFolders = excludedFolders,
            // Only prune missing files if every storage volume query succeeded cleanly.
            allowRemovals = !queryHadErrors,
        )

        applyPlanInBatches(database, plan, onProgress)

        val result = ScanResult(
            totalFound = plan.totalRawRows,
            newSongs = plan.tracksToInsert.size,
            skippedExisting = plan.unchangedCount,
            updatedSongs = plan.tracksToUpdate.size,
            movedSongs = plan.movedTracks.size,
            removedSongs = plan.removedSongIds.size,
            inaccessibleCount = plan.inaccessibleCount,
            duplicatesSkipped = plan.duplicatesSkipped,
        )

        onProgress?.invoke(
            LocalScanProgress(
                phase = LocalScanPhase.COMPLETED,
                processed = plan.totalRawRows,
                total = plan.totalRawRows,
                added = result.newSongs,
                updated = result.updatedSongs,
                moved = result.movedSongs,
                removed = result.removedSongs,
                skippedUnchanged = result.skippedExisting,
                skippedInaccessible = result.inaccessibleCount,
                skippedDuplicates = result.duplicatesSkipped,
            ),
        )

        Timber.tag(TAG).i(
            "Scan complete: total=${result.totalFound}, new=${result.newSongs}, " +
                "updated=${result.updatedSongs}, moved=${result.movedSongs}, " +
                "removed=${result.removedSongs}, unchanged=${result.skippedExisting}, " +
                "inaccessible=${result.inaccessibleCount}, duplicates=${result.duplicatesSkipped}",
        )
        result
    }

    internal suspend fun applyPlanInBatches(
        database: MusicDatabase,
        plan: LocalScanPlan,
        onProgress: ((LocalScanProgress) -> Unit)? = null,
    ) {
        val totalOperations = (
            plan.tracksToInsert.size +
                plan.tracksToUpdate.size +
                plan.movedTracks.size +
                plan.removedSongIds.size
            ).coerceAtLeast(1)

        var processed = 0
        fun reportWriteProgress() {
            onProgress?.invoke(
                LocalScanProgress(
                    phase = LocalScanPhase.WRITING_DATABASE,
                    processed = processed,
                    total = totalOperations,
                    added = plan.tracksToInsert.size,
                    updated = plan.tracksToUpdate.size,
                    moved = plan.movedTracks.size,
                    removed = plan.removedSongIds.size,
                    skippedUnchanged = plan.unchangedCount,
                    skippedInaccessible = plan.inaccessibleCount,
                    skippedDuplicates = plan.duplicatesSkipped,
                ),
            )
        }

        reportWriteProgress()

        val albumsById = plan.activeAlbums.associateBy { it.albumId }
        val touchedAlbumIds = HashSet<String>()
        plan.tracksToInsert.forEach { touchedAlbumIds.add(it.albumId) }
        plan.tracksToUpdate.forEach { touchedAlbumIds.add(it.track.albumId) }
        plan.movedTracks.forEach { touchedAlbumIds.add(it.track.albumId) }

        val artistIdsWritten = HashSet<String>()
        val albumIdsWritten = HashSet<String>()

        fun ensureAlbumAndArtistsInTxn(
            albumId: String,
            track: NormalizedLocalTrack,
            albumsMap: Map<String, AlbumAggregate>,
        ) {
            val now = LocalDateTime.now()
            if (artistIdsWritten.add(track.artistId)) {
                database.upsert(
                    ArtistEntity(
                        id = track.artistId,
                        name = track.artistName,
                        lastUpdateTime = now,
                        isLocal = true,
                    ),
                )
            }
            if (artistIdsWritten.add(track.albumArtistId)) {
                database.upsert(
                    ArtistEntity(
                        id = track.albumArtistId,
                        name = track.albumArtistName,
                        lastUpdateTime = now,
                        isLocal = true,
                    ),
                )
            }
            if (albumIdsWritten.add(albumId)) {
                val agg = albumsMap[albumId]
                database.upsert(
                    AlbumEntity(
                        id = albumId,
                        title = agg?.title ?: track.albumTitle,
                        year = agg?.year ?: track.year,
                        thumbnailUrl = agg?.thumbnailUrl ?: track.thumbnailUrl,
                        songCount = agg?.songCount ?: 1,
                        duration = agg?.totalDurationSec ?: track.durationSec,
                        lastUpdateTime = now,
                        inLibrary = track.dateAdded,
                        isLocal = true,
                    ),
                )
                database.upsert(
                    AlbumArtistMap(
                        albumId = albumId,
                        artistId = agg?.artistId ?: track.albumArtistId,
                        order = 0,
                    ),
                )
            }
        }

        // 1. Insert newly discovered tracks in batched transactions
        for (chunk in plan.tracksToInsert.chunked(DB_BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            database.withTransaction {
                for (track in chunk) {
                    ensureAlbumAndArtistsInTxn(track.albumId, track, albumsById)
                    database.upsert(track.toSongEntity())
                    database.upsert(SongArtistMap(songId = track.id, artistId = track.artistId, position = 0))
                    database.upsert(SongAlbumMap(songId = track.id, albumId = track.albumId, index = track.sortOrderIndex()))
                }
            }
            processed += chunk.size
            reportWriteProgress()
        }

        // 2. Update modified tracks in place (preserving liked state, play count, history, playlists)
        for (chunk in plan.tracksToUpdate.chunked(DB_BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            database.withTransaction {
                for (item in chunk) {
                    val track = item.track
                    ensureAlbumAndArtistsInTxn(track.albumId, track, albumsById)
                    database.upsert(
                        track.toSongEntity(
                            liked = item.previous.liked,
                            likedDate = item.previous.likedDate,
                            totalPlayTime = item.previous.totalPlayTime,
                            inLibrary = item.previous.inLibrary ?: track.dateAdded,
                        ),
                    )
                    database.deleteSongArtistMaps(track.id)
                    database.upsert(SongArtistMap(songId = track.id, artistId = track.artistId, position = 0))
                    database.deleteSongAlbumMaps(track.id)
                    database.upsert(SongAlbumMap(songId = track.id, albumId = track.albumId, index = track.sortOrderIndex()))
                }
            }
            processed += chunk.size
            reportWriteProgress()
        }

        // 3. Remap moved/renamed tracks in batches without cascading playlist/history loss
        for (chunk in plan.movedTracks.chunked(DB_BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            database.withTransaction {
                for (moved in chunk) {
                    val track = moved.track
                    ensureAlbumAndArtistsInTxn(track.albumId, track, albumsById)
                    val preservedEntity = track.toSongEntity(
                        liked = moved.previous.liked,
                        likedDate = moved.previous.likedDate,
                        totalPlayTime = moved.previous.totalPlayTime,
                        inLibrary = moved.previous.inLibrary ?: track.dateAdded,
                    )
                    database.remapMovedLocalSong(moved.oldSongId, preservedEntity)
                    database.deleteSongArtistMaps(track.id)
                    database.upsert(SongArtistMap(songId = track.id, artistId = track.artistId, position = 0))
                    database.deleteSongAlbumMaps(track.id)
                    database.upsert(SongAlbumMap(songId = track.id, albumId = track.albumId, index = track.sortOrderIndex()))
                }
            }
            processed += chunk.size
            reportWriteProgress()
        }

        // 4. Prune removed/excluded songs in chunks, then update album aggregates & clean orphans
        for (chunk in plan.removedSongIds.chunked(DB_BATCH_SIZE)) {
            currentCoroutineContext().ensureActive()
            database.withTransaction {
                database.deleteSongsByIds(chunk)
            }
            processed += chunk.size
            reportWriteProgress()
        }

        if (plan.tracksToInsert.isNotEmpty() ||
            plan.tracksToUpdate.isNotEmpty() ||
            plan.movedTracks.isNotEmpty() ||
            plan.removedSongIds.isNotEmpty()
        ) {
            currentCoroutineContext().ensureActive()
            val now = LocalDateTime.now()
            for (albumChunk in plan.activeAlbums.chunked(DB_BATCH_SIZE)) {
                currentCoroutineContext().ensureActive()
                database.withTransaction {
                    for (album in albumChunk) {
                        database.updateLocalAlbumMetadata(
                            albumId = album.albumId,
                            title = album.title,
                            songCount = album.songCount,
                            duration = album.totalDurationSec,
                            year = album.year,
                            thumbnailUrl = album.thumbnailUrl,
                            lastUpdateTime = now,
                        )
                    }
                }
            }
            if (plan.removedSongIds.isNotEmpty()) {
                database.withTransaction {
                    database.pruneOrphanLocalAlbums()
                    database.pruneOrphanLocalArtists()
                }
            }
        }
    }

    private suspend fun queryMediaStoreTracks(context: Context): Pair<List<ScannedMediaStoreTrack>, Boolean> {
        val resolver = context.contentResolver
        val collections = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                MediaStore.getExternalVolumeNames(context).map { MediaStore.Audio.Media.getContentUri(it) }
            }.getOrElse { listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) }
        } else {
            listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.IS_PENDING)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.ALBUM_ARTIST)
                add(MediaStore.Audio.Media.CD_TRACK_NUMBER)
                add(MediaStore.Audio.Media.DISC_NUMBER)
                add(MediaStore.Audio.Media.IS_TRASHED)
            }
        }.toTypedArray()

        val selection = buildString {
            append("(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')")
            append(" AND ${MediaStore.Audio.Media.DURATION} >= ?")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" AND ${MediaStore.Audio.Media.IS_PENDING} = 0")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                append(" AND ${MediaStore.Audio.Media.IS_TRASHED} = 0")
            }
        }
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString())

        val results = ArrayList<ScannedMediaStoreTrack>()
        var hadQueryError = false

        for (collection in collections) {
            currentCoroutineContext().ensureActive()
            try {
                resolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
                )?.use { cursor ->
                    readCursorRows(cursor, collection, results)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: SecurityException) {
                hadQueryError = true
                Timber.tag(TAG).w(e, "Permission denied querying $collection")
            } catch (e: Exception) {
                hadQueryError = true
                Timber.tag(TAG).w(e, "Failed querying MediaStore collection $collection")
            }
        }
        return results to hadQueryError
    }

    private suspend fun readCursorRows(
        cursor: Cursor,
        collection: Uri,
        out: MutableList<ScannedMediaStoreTrack>,
    ) {
        val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
        if (idCol < 0) return
        val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
        val displayCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
        val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
        val albumArtistCol = cursor.getColumnIndex("album_artist")
        val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
        val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
        val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val addedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        val modifiedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
        val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
        val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
        val cdTrackCol = cursor.getColumnIndex("cd_track_number")
        val discCol = cursor.getColumnIndex("disc_number")
        val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
        val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
        } else {
            -1
        }
        @Suppress("DEPRECATION")
        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
            currentCoroutineContext().ensureActive()
            val mediaId = cursor.getLong(idCol)
            val contentUri = ContentUris.withAppendedId(collection, mediaId).toString()

            out.add(
                ScannedMediaStoreTrack(
                    mediaStoreId = mediaId,
                    contentUri = contentUri,
                    title = titleCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    displayName = displayCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    artist = artistCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    albumArtist = albumArtistCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    album = albumCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    mediaStoreAlbumId = albumIdCol.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: -1L,
                    durationMs = durationCol.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
                    dateAddedSec = addedCol.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
                    dateModifiedSec = modifiedCol.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
                    fileSize = sizeCol.takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L,
                    mimeType = mimeCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    rawTrack = trackCol.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getInt(it) },
                    cdTrackNumber = cdTrackCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    discNumber = discCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    relativePath = relPathCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    absolutePath = dataCol.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    year = yearCol.takeIf { it >= 0 && !cursor.isNull(it) }?.let { cursor.getInt(it) },
                ),
            )
        }
    }

    private fun checkTrackAccessible(
        resolver: ContentResolver,
        row: ScannedMediaStoreTrack,
    ): Boolean {
        if (row.fileSize <= 0L) return false
        val path = row.absolutePath
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return file.length() > 0L
            }
        }
        return runCatching {
            resolver.openAssetFileDescriptor(Uri.parse(row.contentUri), "r")?.use { afd ->
                afd.length != 0L
            } ?: false
        }.getOrDefault(false)
    }

    private fun NormalizedLocalTrack.sortOrderIndex(): Int =
        ((discNumber ?: 1).coerceAtLeast(1) * 1000) + (trackNumber ?: 999).coerceIn(0, 999)

    private fun NormalizedLocalTrack.toSongEntity(
        liked: Boolean = false,
        likedDate: LocalDateTime? = null,
        totalPlayTime: Long = 0L,
        inLibrary: LocalDateTime = dateAdded,
    ) = SongEntity(
        id = id,
        title = title,
        duration = durationSec,
        thumbnailUrl = thumbnailUrl,
        albumId = albumId,
        albumName = albumTitle,
        year = year,
        dateModified = dateModified,
        liked = liked,
        likedDate = likedDate,
        totalPlayTime = totalPlayTime,
        inLibrary = inLibrary,
        isLocal = true,
        trackNumber = trackNumber,
        discNumber = discNumber,
        fileSize = fileSize,
        mimeType = mimeType,
        localPath = localPath,
    )

    private fun LocalSongScanRow.toSnapshot() = ExistingLocalSongSnapshot(
        id = id,
        title = title,
        durationSec = duration,
        albumId = albumId,
        albumName = albumName,
        dateModified = dateModified,
        inLibrary = inLibrary,
        fileSize = fileSize,
        mimeType = mimeType,
        trackNumber = trackNumber,
        discNumber = discNumber,
        localPath = localPath,
        liked = liked,
        likedDate = likedDate,
        totalPlayTime = totalPlayTime,
    )

    private fun LocalDateTime.epochSecondCompat(): Long =
        atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
}
