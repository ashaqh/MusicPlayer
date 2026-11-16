/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.convx.music.constants.SongSortType
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.Album
import com.convx.music.db.entities.Artist
import com.convx.music.db.entities.Song
import com.convx.music.library.PagedCollectionController
import com.convx.music.library.PagedLibraryState
import com.convx.music.library.RoomLibraryPager
import com.convx.music.library.scan.LocalLibraryScanEngine
import com.convx.music.library.scan.LocalScanProgress
import com.convx.music.utils.LocalAudioScanner
import com.convx.music.utils.LocalFolderIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Feeds the Local Music screen: live Room local-songs stream, active media folder index,
 * and coordinated background scan progress/cancellation from [LocalLibraryScanEngine].
 */
@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val scanEngine: LocalLibraryScanEngine,
) : ViewModel() {

    val songs: StateFlow<List<Song>> = database.localSongsByNameAsc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Album>> = database.albumsLocalByNameAsc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Artist>> = database.artistsLocalByNameAsc()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val songPager = PagedCollectionController<Song>(
        scope = viewModelScope,
        pageSize = PagedCollectionController.DEFAULT_PAGE_SIZE,
        keySelector = { it.id },
        pageLoader = { limit, offset ->
            RoomLibraryPager.loadSongsPage(database, SongSortType.NAME, descending = false, limit = limit, offset = offset)
        },
    )
    val pagedSongs: StateFlow<PagedLibraryState<Song>> = songPager.state

    fun loadNextSongsPage() = songPager.loadNextPage()

    private val _folders = MutableStateFlow<List<LocalFolderIndex.Folder>>(emptyList())
    val folders: StateFlow<List<LocalFolderIndex.Folder>> = _folders.asStateFlow()

    val scanProgress: StateFlow<LocalScanProgress> = scanEngine.progress

    val isScanning: StateFlow<Boolean> = scanEngine.progress
        .map { it.isScanning }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), scanEngine.progress.value.isScanning)

    val scanResult: StateFlow<LocalAudioScanner.ScanResult?> = scanEngine.lastResult

    init {
        songPager.refresh()
        viewModelScope.launch(Dispatchers.IO) {
            _folders.value = LocalFolderIndex.load(context)
        }
        viewModelScope.launch(Dispatchers.IO) {
            scanEngine.lastResult.collect { result ->
                if (result != null) {
                    _folders.value = LocalFolderIndex.load(context)
                    songPager.refresh()
                }
            }
        }
    }

    fun scanDevice(context: Context, force: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            scanEngine.scanAndAwait(force = force)
            _folders.value = LocalFolderIndex.load(context.applicationContext)
        }
    }

    fun cancelScan() {
        scanEngine.cancelScan()
    }
}
