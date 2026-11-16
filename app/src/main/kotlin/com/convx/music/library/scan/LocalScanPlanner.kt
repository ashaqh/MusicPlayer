/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.library.scan

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Pure Kotlin planner that normalizes MediaStore metadata, filters inaccessible or excluded
 * tracks, deduplicates identical copies, disambiguates album IDs, detects moved/modified/removed
 * files, and builds an incremental Room mutation plan.
 */
object LocalScanPlanner {
    private const val UNKNOWN_TITLE = "Unknown Title"
    private const val UNKNOWN_ARTIST = "Unknown Artist"
    private const val UNKNOWN_ALBUM = "Unknown Album"
    private const val DEFAULT_MIME = "audio/*"

    fun plan(
        rawTracks: List<ScannedMediaStoreTrack>,
        existingSnapshots: List<ExistingLocalSongSnapshot>,
        excludedFolders: Set<String> = emptySet(),
        allowRemovals: Boolean = true,
        deduplicateIdenticalFiles: Boolean = true,
    ): LocalScanPlan {
        var inaccessibleCount = 0
        var excludedCount = 0
        var duplicatesSkipped = 0

        val normalizedExcluded = excludedFolders
            .map { normalizeFolderPath(it).lowercase() }
            .filter { it.isNotEmpty() }

        val validNormalized = ArrayList<NormalizedLocalTrack>(rawTracks.size)
        val seenIds = HashSet<String>(rawTracks.size)
        val seenPaths = HashSet<String>(rawTracks.size)
        val seenContentFingerprints = HashSet<String>(rawTracks.size)

        for (raw in rawTracks) {
            if (!raw.isReadable || raw.fileSize <= 0L) {
                inaccessibleCount++
                continue
            }

            val folder = deriveFolderPath(raw)
            if (isFolderExcluded(folder, normalizedExcluded)) {
                excludedCount++
                continue
            }

            val normalized = normalize(raw, folder)

            if (!seenIds.add(normalized.id)) {
                duplicatesSkipped++
                continue
            }

            val pathKey = normalized.localPath.lowercase()
            if (pathKey.isNotEmpty() && !seenPaths.add(pathKey)) {
                duplicatesSkipped++
                continue
            }

            if (deduplicateIdenticalFiles) {
                val contentKey = "${normalized.title.lowercase()}|${normalized.artistName.lowercase()}|${normalized.albumTitle.lowercase()}|${normalized.fileSize}|${normalized.durationSec}"
                if (!seenContentFingerprints.add(contentKey)) {
                    duplicatesSkipped++
                    continue
                }
            }

            validNormalized.add(normalized)
        }

        val existingById = existingSnapshots.associateBy { it.id }
        val activeScannedIds = HashSet<String>(validNormalized.size)

        val tracksToUpdate = ArrayList<UpdatedLocalTrack>()
        val candidateInserts = ArrayList<NormalizedLocalTrack>()
        var unchangedCount = 0

        for (track in validNormalized) {
            activeScannedIds.add(track.id)
            val existing = existingById[track.id]
            if (existing == null) {
                candidateInserts.add(track)
            } else if (isUnchanged(existing, track)) {
                unchangedCount++
            } else {
                tracksToUpdate.add(UpdatedLocalTrack(track = track, previous = existing))
            }
        }

        // Pair disappeared IDs with newly added tracks of matching (fileSize, duration, title/filename)
        // so moving/renaming files across folders preserves likes, play time, history, and playlists.
        val missingSnapshots = if (allowRemovals) {
            existingSnapshots.filter { it.id !in activeScannedIds }
        } else {
            emptyList()
        }

        val movedTracks = ArrayList<MovedLocalTrack>()
        val matchedOldIds = HashSet<String>()
        val matchedNewIds = HashSet<String>()

        if (missingSnapshots.isNotEmpty() && candidateInserts.isNotEmpty()) {
            val missingBySize = missingSnapshots
                .filter { (it.fileSize ?: 0L) > 0L }
                .groupBy { it.fileSize!! }
                .mapValues { (_, list) -> list.toMutableList() }

            for (newTrack in candidateInserts) {
                val candidates = missingBySize[newTrack.fileSize] ?: continue
                val newName = fileNameOf(newTrack.localPath, null).lowercase()
                val matchedIdx = candidates.indexOfFirst { old ->
                    old.id !in matchedOldIds &&
                        abs(old.durationSec - newTrack.durationSec) <= 1 &&
                        (
                            old.title.equals(newTrack.title, ignoreCase = true) ||
                                (old.localPath != null && fileNameOf(old.localPath, null).lowercase() == newName && newName.isNotEmpty())
                            )
                }
                if (matchedIdx >= 0) {
                    val old = candidates[matchedIdx]
                    matchedOldIds.add(old.id)
                    matchedNewIds.add(newTrack.id)
                    movedTracks.add(
                        MovedLocalTrack(
                            oldSongId = old.id,
                            track = newTrack,
                            previous = old,
                        ),
                    )
                }
            }
        }

        val tracksToInsert = candidateInserts.filter { it.id !in matchedNewIds }
        val removedSongIds = if (allowRemovals) {
            missingSnapshots.mapNotNull { snapshot ->
                snapshot.id.takeUnless { it in matchedOldIds }
            }
        } else {
            emptyList()
        }

        val albums = aggregateAlbums(validNormalized)

        return LocalScanPlan(
            totalRawRows = rawTracks.size,
            tracksToInsert = tracksToInsert,
            tracksToUpdate = tracksToUpdate,
            movedTracks = movedTracks,
            removedSongIds = removedSongIds,
            unchangedCount = unchangedCount,
            inaccessibleCount = inaccessibleCount,
            duplicatesSkipped = duplicatesSkipped,
            excludedCount = excludedCount,
            activeAlbums = albums,
        )
    }

    internal fun normalize(
        raw: ScannedMediaStoreTrack,
        folderPath: String = deriveFolderPath(raw),
    ): NormalizedLocalTrack {
        val cleanTitle = resolveTitle(raw.title, raw.displayName, raw.absolutePath)
        val cleanArtist = sanitizeTag(raw.artist) ?: sanitizeTag(raw.albumArtist) ?: UNKNOWN_ARTIST
        val cleanAlbumArtist = sanitizeTag(raw.albumArtist) ?: cleanArtist
        val folderLeaf = folderPath.substringAfterLast('/').takeIf { it.isNotBlank() }
        val cleanAlbum = sanitizeTag(raw.album) ?: folderLeaf ?: UNKNOWN_ALBUM

        val (discNumber, trackNumber) = parseTrackAndDisc(
            cdTrackNumber = raw.cdTrackNumber,
            discNumber = raw.discNumber,
            rawTrack = raw.rawTrack,
        )

        val durationSec = if (raw.durationMs > 0L) {
            ((raw.durationMs + 500L) / 1000L).toInt().coerceAtLeast(1)
        } else {
            0
        }

        val dateAdded = epochSecondsToLocalDateTime(raw.dateAddedSec)
        val dateModified = epochSecondsToLocalDateTime(
            if (raw.dateModifiedSec > 0L) raw.dateModifiedSec else raw.dateAddedSec,
        )

        val albumId = if (raw.mediaStoreAlbumId > 0L) {
            "local_album_ms_${raw.mediaStoreAlbumId}"
        } else {
            "local_album_${shortHash("${cleanAlbum.lowercase()}|${cleanAlbumArtist.lowercase()}")}"
        }

        val artistId = "local_artist_${shortHash(cleanArtist.lowercase())}"
        val albumArtistId = if (cleanAlbumArtist.equals(cleanArtist, ignoreCase = true)) {
            artistId
        } else {
            "local_artist_${shortHash(cleanAlbumArtist.lowercase())}"
        }

        val thumbnailUrl = if (raw.mediaStoreAlbumId > 0L) {
            "content://media/external/audio/albumart/${raw.mediaStoreAlbumId}"
        } else {
            "content://media/external/audio/media/${raw.mediaStoreId}/albumart"
        }

        val localPath = raw.absolutePath?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                raw.relativePath?.trimEnd('/')?.takeIf { it.isNotBlank() },
                raw.displayName?.takeIf { it.isNotBlank() },
            ).joinToString("/")

        return NormalizedLocalTrack(
            id = raw.contentUri,
            title = cleanTitle,
            artistName = cleanArtist,
            artistId = artistId,
            albumArtistName = cleanAlbumArtist,
            albumArtistId = albumArtistId,
            albumTitle = cleanAlbum,
            albumId = albumId,
            durationSec = durationSec,
            dateAdded = dateAdded,
            dateModified = dateModified,
            trackNumber = trackNumber,
            discNumber = discNumber,
            fileSize = raw.fileSize.coerceAtLeast(0L),
            mimeType = raw.mimeType?.takeIf { it.isNotBlank() } ?: DEFAULT_MIME,
            localPath = localPath,
            folderPath = folderPath,
            thumbnailUrl = thumbnailUrl,
            year = raw.year?.takeIf { it in 1000..9999 },
        )
    }

    internal fun parseTrackAndDisc(
        cdTrackNumber: String?,
        discNumber: String?,
        rawTrack: Int?,
    ): Pair<Int?, Int?> {
        var parsedTrack = parseLeadingPositiveInt(cdTrackNumber)
        var parsedDisc = parseLeadingPositiveInt(discNumber)

        if (rawTrack != null && rawTrack > 0) {
            if (rawTrack >= 1000) {
                val decodedDisc = rawTrack / 1000
                val decodedTrack = rawTrack % 1000
                if (parsedDisc == null && decodedDisc > 0) parsedDisc = decodedDisc
                if (parsedTrack == null && decodedTrack > 0) parsedTrack = decodedTrack
            } else if (parsedTrack == null) {
                parsedTrack = rawTrack
            }
        }
        return parsedDisc to parsedTrack
    }

    private fun parseLeadingPositiveInt(value: String?): Int? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val numberPart = raw.substringBefore('/').trim()
        return numberPart.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun isUnchanged(
        existing: ExistingLocalSongSnapshot,
        track: NormalizedLocalTrack,
    ): Boolean =
        existing.dateModified == track.dateModified &&
            existing.fileSize == track.fileSize &&
            existing.mimeType == track.mimeType &&
            existing.trackNumber == track.trackNumber &&
            existing.discNumber == track.discNumber &&
            existing.localPath == track.localPath &&
            existing.title == track.title &&
            existing.albumId == track.albumId &&
            existing.durationSec == track.durationSec &&
            existing.inLibrary != null

    private fun aggregateAlbums(tracks: List<NormalizedLocalTrack>): List<AlbumAggregate> =
        tracks.groupBy { it.albumId }.map { (albumId, albumTracks) ->
            val first = albumTracks.first()
            val primaryArtist = albumTracks
                .groupingBy { it.albumArtistId to it.albumArtistName }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: (first.albumArtistId to first.albumArtistName)

            val years = albumTracks.mapNotNull { it.year }
            AlbumAggregate(
                albumId = albumId,
                title = first.albumTitle,
                artistId = primaryArtist.first,
                artistName = primaryArtist.second,
                thumbnailUrl = albumTracks.firstOrNull { it.thumbnailUrl.isNotBlank() }?.thumbnailUrl
                    ?: first.thumbnailUrl,
                songCount = albumTracks.size,
                totalDurationSec = albumTracks.sumOf { it.durationSec },
                year = years.maxOrNull(),
            )
        }

    private fun resolveTitle(title: String?, displayName: String?, absolutePath: String?): String {
        sanitizeTag(title)?.let { return it }
        val fileBase = (displayName?.takeIf { it.isNotBlank() }
            ?: absolutePath?.substringAfterLast('/')?.takeIf { it.isNotBlank() })
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return fileBase ?: UNKNOWN_TITLE
    }

    private fun sanitizeTag(value: String?): String? {
        val trimmed = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.equals("<unknown>", ignoreCase = true)) return null
        return trimmed
    }

    private fun deriveFolderPath(raw: ScannedMediaStoreTrack): String {
        val abs = raw.absolutePath?.takeIf { it.isNotBlank() }
        if (abs != null) {
            val normalized = abs.replace('\\', '/')
            val idx = normalized.lastIndexOf('/')
            if (idx > 0) return normalizeFolderPath(normalized.substring(0, idx))
        }
        return normalizeFolderPath(raw.relativePath.orEmpty())
    }

    private fun normalizeFolderPath(path: String): String =
        path.replace('\\', '/').trimEnd('/')

    private fun isFolderExcluded(folderPath: String, excludedLower: List<String>): Boolean {
        if (excludedLower.isEmpty() || folderPath.isEmpty()) return false
        val lower = folderPath.lowercase()
        return excludedLower.any { excluded ->
            lower == excluded || lower.startsWith("$excluded/")
        }
    }

    private fun fileNameOf(path: String?, displayName: String?): String {
        val raw = path?.replace('\\', '/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: displayName?.takeIf { it.isNotBlank() }
            ?: return ""
        return raw.substringBeforeLast('.')
    }

    private fun epochSecondsToLocalDateTime(epochSeconds: Long): LocalDateTime {
        if (epochSeconds <= 0L) return LocalDateTime.ofEpochSecond(0L, 0, ZoneOffset.UTC)
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.systemDefault()).toLocalDateTime()
    }

    private fun shortHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(16) {
            for (i in 0 until 8) {
                val b = digest[i].toInt() and 0xFF
                append(HEX[b ushr 4])
                append(HEX[b and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789abcdef"
}
