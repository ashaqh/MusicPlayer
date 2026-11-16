/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.library

import com.convx.music.constants.SongSortType
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.Album
import com.convx.music.db.entities.Artist
import com.convx.music.db.entities.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Immutable UI/pagination state for large library collections (10,000+ items). */
data class PagedLibraryState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

/**
 * Reusable coroutine-based SQL keyset/offset page loader for large Room collections.
 * Prevents loading tens of thousands of entities into memory at once during initial list display.
 */
class PagedCollectionController<T>(
    private val scope: CoroutineScope,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val keySelector: (T) -> String,
    private val pageLoader: suspend (limit: Int, offset: Int) -> List<T>,
) {
    private val _state = MutableStateFlow(PagedLibraryState<T>())
    val state: StateFlow<PagedLibraryState<T>> = _state.asStateFlow()

    private var currentOffset = 0
    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        currentOffset = 0
        _state.value = PagedLibraryState(isLoading = true)
        fetchNextInternal(reset = true)
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isLoading || current.endReached) return
        _state.value = current.copy(isLoading = true, error = null)
        fetchNextInternal(reset = false)
    }

    private fun fetchNextInternal(reset: Boolean) {
        val requestOffset = if (reset) 0 else currentOffset
        loadJob = scope.launch(Dispatchers.IO) {
            try {
                val page = pageLoader(pageSize, requestOffset)
                currentOffset = requestOffset + page.size
                val combined = if (reset) {
                    page
                } else {
                    (_state.value.items + page).distinctBy(keySelector)
                }
                _state.value = PagedLibraryState(
                    items = combined,
                    isLoading = false,
                    endReached = page.size < pageSize,
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = error.message ?: "Failed to load page",
                )
            }
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 200
    }
}

/** Convenience Room paging queries backed by SQLite LIMIT/OFFSET and schema v41 indexes. */
object RoomLibraryPager {
    suspend fun loadSongsPage(
        database: MusicDatabase,
        sortType: SongSortType,
        descending: Boolean,
        limit: Int,
        offset: Int,
    ): List<Song> = withContext(Dispatchers.IO) {
        when (sortType) {
            SongSortType.NAME ->
                if (descending) database.localSongsPageByNameDesc(limit, offset)
                else database.localSongsPageByNameAsc(limit, offset)

            SongSortType.CREATE_DATE ->
                if (descending) database.localSongsPageByCreateDateDesc(limit, offset)
                else database.localSongsPageByCreateDateAsc(limit, offset)

            SongSortType.PLAY_TIME ->
                database.localSongsPageByPlayTimeDesc(limit, offset)

            SongSortType.ARTIST ->
                database.localSongsPageByNameAsc(limit, offset)
        }
    }

    suspend fun loadAlbumsPage(
        database: MusicDatabase,
        bySongCount: Boolean,
        limit: Int,
        offset: Int,
    ): List<Album> = withContext(Dispatchers.IO) {
        if (bySongCount) database.albumsLocalPageBySongCountDesc(limit, offset)
        else database.albumsLocalPageByNameAsc(limit, offset)
    }

    suspend fun loadArtistsPage(
        database: MusicDatabase,
        limit: Int,
        offset: Int,
    ): List<Artist> = withContext(Dispatchers.IO) {
        database.artistsLocalPageByNameAsc(limit, offset)
    }
}

