/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.library.apple

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.convx.music.constants.AlbumSortType
import com.convx.music.constants.ArtistSortType
import com.convx.music.constants.AlbumSortTypeKey
import com.convx.music.constants.AlbumSortDescendingKey
import com.convx.music.constants.ArtistSortTypeKey
import com.convx.music.constants.ArtistSortDescendingKey
import com.convx.music.constants.SongSortType
import com.convx.music.constants.SongSortTypeKey
import com.convx.music.constants.SongSortDescendingKey
import com.convx.music.extensions.toEnum
import com.convx.music.constants.PlaylistSortType
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.LocalItem
import com.convx.music.db.entities.Playlist
import com.convx.music.library.scan.LocalLibraryScanEngine
import com.convx.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Apple Music's Library main page data: pinned items on top, and "Recently Added" —
 * device albums and playlists created in this app, newest first.
 */
@HiltViewModel
class AppleLibraryViewModel @Inject constructor(
    private val database: MusicDatabase,
    @ApplicationContext private val context: Context,
    private val scanEngine: LocalLibraryScanEngine,
) : ViewModel() {

    /** Always true in the offline-only player. */
    val localOnly: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    private val playlists = database.playlists(PlaylistSortType.CREATE_DATE, true).map { list ->
        list.filter { it.playlist.browseId == null }.distinctBy { it.id }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val localAlbums = context.dataStore.data
        .map { it[AlbumSortTypeKey].toEnum(AlbumSortType.NAME) to (it[AlbumSortDescendingKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (sort, descending) ->
            database.albumsLocal(sort, descending)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val localArtists = context.dataStore.data
        .map { it[ArtistSortTypeKey].toEnum(ArtistSortType.NAME) to (it[ArtistSortDescendingKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (sort, descending) ->
            database.artistsLocal(sort, descending)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val localSongs = context.dataStore.data
        .map { it[SongSortTypeKey].toEnum(SongSortType.NAME) to (it[SongSortDescendingKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (sort, descending) ->
            database.localSongs(sort, descending)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pinned albums and playlists together, in pin order (Apple's grid order). */
    val pinned: StateFlow<List<LocalItem>> = combine(database.pinnedAlbums(), playlists) { albums, lists ->
        (albums.filter { it.album.isLocal }.map { it to it.album.pinnedAt } +
            lists.filter { it.playlist.pinnedAt != null }.map { it to it.playlist.pinnedAt })
            .sortedBy { it.second }
            .map { it.first }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyAdded: StateFlow<List<LocalItem>> = combine(
        localAlbums,
        playlists,
    ) { albums, lists ->
        (albums.map { it to (it.album.bookmarkedAt ?: it.album.inLibrary ?: it.album.lastUpdateTime) } +
            lists.map { it to (it.playlist.bookmarkedAt ?: it.playlist.createdAt ?: LocalDateTime.MIN) })
            .sortedByDescending { it.second }
            .take(RECENTLY_ADDED_LIMIT)
            .map { it.first }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun scanDevice() = scanDevice(force = false)

    fun scanDevice(force: Boolean) {
        scanEngine.triggerScan(force = force)
    }

    /**
     * Pinned order is the pinnedAt order; a drag-rearrange rewrites the stamps in the new
     * order so it survives (Apple lets pinned items be dragged around).
     */
    fun reorderPinned(ordered: List<LocalItem>) {
        val base = LocalDateTime.now()
        viewModelScope.launch(Dispatchers.IO) {
            database.query {
                ordered.forEachIndexed { i, item ->
                    val at = base.plusNanos(i * 1_000_000L)
                    when (item) {
                        is Playlist -> update(item.playlist.copy(pinnedAt = at))
                        is com.convx.music.db.entities.Album -> update(item.album.copy(pinnedAt = at))
                        else -> Unit
                    }
                }
            }
        }
    }

    private companion object {
        const val RECENTLY_ADDED_LIMIT = 24
    }
}
