package com.apprise.music.library

import com.convx.music.constants.SongSortType
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.Song
import com.convx.music.library.RoomLibraryPager
import com.convx.music.library.scan.LocalLibraryScanEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Domain representation of an on-device or user-owned music track exposed across module boundaries. */
data class LocalLibraryTrack(
    val id: String,
    val title: String,
    val artistNames: List<String>,
    val albumTitle: String?,
    val durationSeconds: Int,
    val thumbnailUri: String?,
    val isLocal: Boolean,
)

/** Summary returned after scanning local storage volumes for audio tracks. */
data class LibraryScanSummary(
    val totalFound: Int,
    val newSongsAdded: Int,
    val skippedExisting: Int,
)

/** Clean boundary over music library indexing and queries, decoupled from Room DAOs and MediaStore details. */
interface LibraryCatalogBoundary {
    fun observeLocalTracks(
        sortType: SongSortType = SongSortType.NAME,
        descending: Boolean = false,
    ): Flow<List<LocalLibraryTrack>>

    fun searchLocalTracks(query: String, limit: Int = Int.MAX_VALUE): Flow<List<LocalLibraryTrack>>

    suspend fun loadLocalTracksPage(
        limit: Int,
        offset: Int,
        sortType: SongSortType = SongSortType.NAME,
        descending: Boolean = false,
    ): List<LocalLibraryTrack> = emptyList()

    suspend fun scanDeviceAudio(): LibraryScanSummary
}

@Singleton
class RoomLibraryCatalogBoundary @Inject constructor(
    private val database: MusicDatabase,
    private val scanEngine: LocalLibraryScanEngine,
) : LibraryCatalogBoundary {

    override fun observeLocalTracks(
        sortType: SongSortType,
        descending: Boolean,
    ): Flow<List<LocalLibraryTrack>> =
        database.localSongs(sortType, descending).map { songs ->
            songs.map { song ->
                LocalLibraryTrack(
                    id = song.id,
                    title = song.title,
                    artistNames = song.artists.map { it.name },
                    albumTitle = song.album?.title ?: song.song.albumName,
                    durationSeconds = song.song.duration,
                    thumbnailUri = song.thumbnailUrl,
                    isLocal = song.song.isLocal,
                )
            }
        }

    override fun searchLocalTracks(query: String, limit: Int): Flow<List<LocalLibraryTrack>> =
        database.searchSongs(query = query, previewSize = limit, localOnly = true).map { songs ->
            songs.map { it.toBoundaryTrack() }
        }

    override suspend fun loadLocalTracksPage(
        limit: Int,
        offset: Int,
        sortType: SongSortType,
        descending: Boolean,
    ): List<LocalLibraryTrack> =
        RoomLibraryPager.loadSongsPage(database, sortType, descending, limit, offset)
            .map { it.toBoundaryTrack() }

    private fun Song.toBoundaryTrack(): LocalLibraryTrack =
        LocalLibraryTrack(
            id = id,
            title = title,
            artistNames = artists.map { it.name },
            albumTitle = album?.title ?: song.albumName,
            durationSeconds = song.duration,
            thumbnailUri = thumbnailUrl,
            isLocal = song.isLocal,
        )

    override suspend fun scanDeviceAudio(): LibraryScanSummary {
        val result = scanEngine.scanAndAwait(force = true)
        return LibraryScanSummary(
            totalFound = result.totalFound,
            newSongsAdded = result.newSongs,
            skippedExisting = result.skippedExisting,
        )
    }
}

