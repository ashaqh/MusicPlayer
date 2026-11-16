/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.convx.music.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.convx.music.constants.AlbumFilter
import com.convx.music.constants.AlbumFilterKey
import com.convx.music.constants.AlbumSortDescendingKey
import com.convx.music.constants.AlbumSortType
import com.convx.music.constants.AlbumSortTypeKey
import com.convx.music.constants.ArtistFilter
import com.convx.music.constants.ArtistFilterKey
import com.convx.music.constants.ArtistSongSortDescendingKey
import com.convx.music.constants.ArtistSongSortType
import com.convx.music.constants.ArtistSongSortTypeKey
import com.convx.music.constants.ArtistSortDescendingKey
import com.convx.music.constants.ArtistSortType
import com.convx.music.constants.ArtistSortTypeKey
import com.convx.music.constants.DataSaverEnabledKey
import com.convx.music.constants.HideExplicitKey
import com.convx.music.constants.HideVideoSongsKey
import com.convx.music.constants.LibraryFilter
import com.convx.music.constants.PlaylistSortDescendingKey
import com.convx.music.constants.PlaylistSortType
import com.convx.music.constants.PlaylistSortTypeKey
import com.convx.music.constants.SongFilter
import com.convx.music.constants.SongFilterKey
import com.convx.music.constants.SongSortDescendingKey
import com.convx.music.constants.SongSortType
import com.convx.music.constants.SongSortTypeKey
import com.convx.music.constants.TopSize
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.Song
import com.convx.music.extensions.filterExplicit
import com.convx.music.extensions.filterExplicitAlbums
import com.convx.music.extensions.filterVideoSongs
import com.convx.music.extensions.toEnum
import com.convx.music.library.scan.LocalLibraryScanEngine
import com.convx.music.utils.LocalAudioScanner
import com.convx.music.utils.dataStore
import com.convx.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class SongLibraryOptions(
    val filter: SongFilter,
    val sortType: SongSortType,
    val descending: Boolean,
    val hideExplicit: Boolean,
    val hideVideoSongs: Boolean,
)

data class LibrarySongsSnapshot(
    val filter: SongFilter,
    val sortType: SongSortType,
    val descending: Boolean,
    val hideExplicit: Boolean,
    val hideVideoSongs: Boolean,
    val songs: List<Song>,
)

private data class AlbumLibraryOptions(
    val filter: AlbumFilter,
    val sortType: AlbumSortType,
    val descending: Boolean,
    val hideExplicit: Boolean,
)

private data class PlaylistLibraryOptions(
    val sortType: PlaylistSortType,
    val descending: Boolean,
)

private data class ArtistSongOptions(
    val sortType: ArtistSongSortType,
    val descending: Boolean,
    val hideExplicit: Boolean,
    val hideVideoSongs: Boolean,
)

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val scanEngine: LocalLibraryScanEngine,
) : ViewModel() {
    val isScanning = scanEngine.progress
        .map { it.isScanning }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), scanEngine.progress.value.isScanning)

    val scanResult = scanEngine.lastResult

    val allSongs =
        context.dataStore.data
            .map { preferences ->
                val filter = when (preferences[SongFilterKey].toEnum(SongFilter.LOCAL)) {
                    SongFilter.LIKED -> SongFilter.LIKED
                    else -> SongFilter.LOCAL
                }
                SongLibraryOptions(
                    filter = filter,
                    sortType = preferences[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                    descending = preferences[SongSortDescendingKey] ?: true,
                    hideExplicit = preferences[HideExplicitKey] ?: false,
                    hideVideoSongs = (preferences[HideVideoSongsKey] ?: false) ||
                        (preferences[DataSaverEnabledKey] ?: false),
                )
            }.distinctUntilChanged()
            .flatMapLatest { options ->
                val songs = when (options.filter) {
                    SongFilter.LIKED -> database.likedSongs(options.sortType, options.descending)
                    else -> database.localSongs(options.sortType, options.descending)
                }
                songs
                    .map {
                        LibrarySongsSnapshot(
                            filter = options.filter,
                            sortType = options.sortType,
                            descending = options.descending,
                            hideExplicit = options.hideExplicit,
                            hideVideoSongs = options.hideVideoSongs,
                            songs = it.filterExplicit(options.hideExplicit)
                                .filterVideoSongs(options.hideVideoSongs),
                        )
                    }
                    .onStart {
                        emit(
                            LibrarySongsSnapshot(
                                filter = options.filter,
                                sortType = options.sortType,
                                descending = options.descending,
                                hideExplicit = options.hideExplicit,
                                hideVideoSongs = options.hideVideoSongs,
                                songs = emptyList(),
                            ),
                        )
                    }
            }.stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                LibrarySongsSnapshot(
                    filter = SongFilter.LOCAL,
                    sortType = SongSortType.CREATE_DATE,
                    descending = true,
                    hideExplicit = false,
                    hideVideoSongs = false,
                    songs = emptyList(),
                ),
            )

    fun scanLocalFiles() {
        scanEngine.triggerScan(force = true)
    }

    fun clearScanResult() {
        scanEngine.clearLastResult()
    }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    when (it[ArtistFilterKey].toEnum(ArtistFilter.LOCAL)) {
                        ArtistFilter.LIKED -> ArtistFilter.LIKED
                        else -> ArtistFilter.LOCAL
                    },
                    it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                    it[ArtistSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    else -> database.artistsLocal(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    private val albumOptions =
        context.dataStore.data
            .map { preferences ->
                AlbumLibraryOptions(
                    filter = when (preferences[AlbumFilterKey].toEnum(AlbumFilter.LOCAL)) {
                        AlbumFilter.LIKED -> AlbumFilter.LIKED
                        else -> AlbumFilter.LOCAL
                    },
                    sortType = preferences[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                    descending = preferences[AlbumSortDescendingKey] ?: true,
                    hideExplicit = preferences[HideExplicitKey] ?: false,
                )
            }.distinctUntilChanged()

    val localAlbums =
        albumOptions
            .flatMapLatest { options ->
                database.albumsLocal(options.sortType, options.descending)
                    .map { it.filterExplicitAlbums(options.hideExplicit) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allAlbums =
        albumOptions
            .flatMapLatest { options ->
                val albums = when (options.filter) {
                    AlbumFilter.LIKED -> database.albumsLiked(options.sortType, options.descending)
                    else -> database.albumsLocal(options.sortType, options.descending)
                }
                albums.map { it.filterExplicitAlbums(options.hideExplicit) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map { preferences ->
                PlaylistLibraryOptions(
                    sortType = preferences[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE),
                    descending = preferences[PlaylistSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { options ->
                database.playlists(options.sortType, options.descending).map { playlists ->
                    playlists.filter { it.playlist.browseId == null }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = requireNotNull(savedStateHandle.get<String>("artistId")) { "Missing artistId" }
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map { preferences ->
                ArtistSongOptions(
                    sortType = preferences[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE),
                    descending = preferences[ArtistSongSortDescendingKey] ?: true,
                    hideExplicit = preferences[HideExplicitKey] ?: false,
                    hideVideoSongs = (preferences[HideVideoSongsKey] ?: false) ||
                        (preferences[DataSaverEnabledKey] ?: false),
                )
            }.distinctUntilChanged()
            .flatMapLatest { options ->
                database.artistSongs(artistId, options.sortType, options.descending).map {
                    it.filterExplicit(options.hideExplicit).filterVideoSongs(options.hideVideoSongs)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val scanEngine: LocalLibraryScanEngine,
) : ViewModel() {
    val isRefreshing = scanEngine.progress
        .map { it.isScanning }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), scanEngine.progress.value.isScanning)

    fun refresh() {
        scanEngine.triggerScan(force = true)
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()

    var artists = database.artistsLocal(ArtistSortType.NAME, false)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.albumsLocal(AlbumSortType.NAME, false)
                .map { it.filterExplicitAlbums(hideExplicit) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = database.playlists(PlaylistSortType.CREATE_DATE, true)
        .map { list -> list.filter { it.playlist.browseId == null } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}
