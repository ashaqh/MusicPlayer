/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.menu

import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import com.convx.music.ui.component.menuBottomInset
import com.convx.music.utils.reportException
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import com.convx.music.ui.ios.compat.CircularProgressIndicator
import com.convx.music.ui.ios.compat.HorizontalDivider
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import com.convx.music.ui.ios.compat.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.Alignment
import com.convx.music.utils.dataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.convx.music.LocalDatabase
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.db.entities.Playlist
import com.convx.music.db.entities.SpeedDialItem
import com.convx.music.db.entities.PlaylistSong
import com.convx.music.db.entities.Song
import com.convx.music.extensions.toMediaItem
import com.convx.music.playback.queues.ListQueue
import com.convx.music.ui.component.DefaultDialog
import com.convx.music.ui.component.IosMenuGroup
import com.convx.music.ui.component.IosMenuItemData
import com.convx.music.ui.component.rememberPlaylistCoverPicker
import com.convx.music.ui.component.NewAction
import com.convx.music.ui.component.NewActionGrid
import com.convx.music.ui.component.PlaylistListItem
import com.convx.music.ui.component.TextFieldDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
    autoPlaylist: Boolean? = false,
    downloadPlaylist: Boolean? = false,
    songList: List<Song>? = emptyList(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current.get()
    val playerConnection = LocalPlayerConnection.current ?: return
    val dbPlaylist by database.playlist(playlist.id).collectAsState(initial = playlist)
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    LaunchedEffect(Unit) {
        if (autoPlaylist == false) {
            database.playlistSongs(playlist.id).collect {
                songs = it.map(PlaylistSong::song)
            }
        } else {
            if (songList != null) {
                songs = songList
            }
        }
    }

    val editable: Boolean = playlist.playlist.isEditable == true

    val launchCoverPicker = rememberPlaylistCoverPicker(
        playlist = playlist,
        onCoverSaved = { newUrl ->
            if (autoPlaylist == true) {
                coroutineScope.launch {
                    context.dataStore.edit { settings ->
                        settings[stringPreferencesKey("thumbnail_${playlist.id}")] = newUrl
                    }
                }
            }
        }
    )

    val isPinned by database.speedDialDao.isPinned(playlist.id).collectAsState(initial = false)

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = { SymbolIcon(symbol = sf("pencil"), contentDescription = null) },
            title = { Text(text = stringResource(R.string.edit_playlist)) },
            onDismiss = { showEditDialog = false },
            initialTextFieldValue =
            TextFieldValue(
                playlist.playlist.name,
                TextRange(playlist.playlist.name.length),
            ),
            onDone = { name ->
                onDismiss()
                database.query {
                    update(
                        playlist.playlist.copy(
                            name = name,
                            lastUpdateTime = LocalDateTime.now()
                        )
                    )
                }
            },
        )
    }

    var showMergeDialog by remember { mutableStateOf(false) }
    if (showMergeDialog) {
        val targets by database.playlists(com.convx.music.constants.PlaylistSortType.NAME, false)
            .collectAsState(initial = emptyList())
        com.convx.music.ui.component.ListDialog(onDismiss = { showMergeDialog = false }) {
            item { Text(text = stringResource(R.string.merge_playlist_into), modifier = Modifier.padding(16.dp)) }
            targets.filter { it.id != playlist.id && it.playlist.isEditable == true }.forEach { target ->
                item(key = target.id) {
                    Text(
                        text = target.playlist.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMergeDialog = false
                                onDismiss()
                                // This playlist's songs are appended to the target; this one is kept.
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.mergePlaylists(target, listOf(playlist.id))
                                }
                            }
                            .padding(16.dp),
                    )
                }
            }
        }
    }

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }

    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
                    style = AppTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        onDismiss()
                        database.transaction {
                            if (playlist.playlist.bookmarkedAt != null) {
                                // Clear the saved state before deleting a bookmarked playlist.
                                update(playlist.playlist.toggleLike())
                            }
                            delete(playlist.playlist)
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    PlaylistListItem(
        playlist = playlist,
        trailingContent = {
            if (playlist.playlist.isEditable != true) {
                IconButton(
                    onClick = {
                        database.query {
                            dbPlaylist?.playlist?.toggleLike()?.let { update(it) }
                        }
                    }
                ) {
                    SymbolIcon(symbol = if (dbPlaylist?.playlist?.bookmarkedAt != null) sf("heart.fill") else sf("heart"), contentDescription = null, tint = if (dbPlaylist?.playlist?.bookmarkedAt != null) AppTheme.colorScheme.error else LocalContentColor.current)
                }
            }
        },
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + menuBottomInset(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = listOf(
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("play.fill"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.play),
                        onClick = {
                            onDismiss()
                            if (songs.isNotEmpty()) {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = playlist.playlist.name,
                                        items = songs.map(Song::toMediaItem)
                                    )
                                )
                            }
                        }
                    ),
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("shuffle"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.shuffle),
                        onClick = {
                            onDismiss()
                            if (songs.isNotEmpty()) {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = playlist.playlist.name,
                                        items = songs.shuffled().map(Song::toMediaItem)
                                    )
                                )
                            }
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                columns = 2
            )
        }

        item {
            IosMenuGroup(
                items = listOf(
                    IosMenuItemData(
                        title = { Text(text = stringResource(R.string.play_next)) },
                        description = { Text(text = stringResource(R.string.play_next_desc)) },
                        icon = {
                            SymbolIcon(symbol = sf("text.line.first.and.arrowtriangle.forward"), contentDescription = null)
                        },
                        onClick = {
                            coroutineScope.launch {
                                playerConnection.playNext(songs.map { it.toMediaItem() })
                            }
                            onDismiss()
                        }
                    ),
                    IosMenuItemData(
                        title = { Text(text = stringResource(R.string.add_to_queue)) },
                        description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                        icon = {
                            SymbolIcon(symbol = sf("music.note.list"), contentDescription = null)
                        },
                        onClick = {
                            onDismiss()
                            playerConnection.addToQueue(songs.map { it.toMediaItem() })
                        }
                    )
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            IosMenuGroup(
                items = buildList {
                    if (editable && autoPlaylist != true) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.edit)) },
                                description = { Text(text = stringResource(R.string.edit_desc)) },
                                icon = {
                                    SymbolIcon(symbol = sf("pencil"), contentDescription = null)
                                },
                                onClick = {
                                    showEditDialog = true
                                }
                            )
                        )
                    }
                    if (autoPlaylist != true) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.merge_playlist)) },
                                icon = { SymbolIcon(symbol = sf("rectangle.stack"), contentDescription = null) },
                                onClick = { showMergeDialog = true }
                            )
                        )
                    }
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.edit_playlist_cover)) },
                            icon = {
                                SymbolIcon(symbol = sf("photo"), contentDescription = null)
                            },
                            onClick = { launchCoverPicker() }
                        )
                    )
                    if (autoPlaylist == true) {
                        val thumbnailKey = remember(playlist.id) { stringPreferencesKey("thumbnail_${playlist.id}") }
                        val currentThumbnail by remember(thumbnailKey) { context.dataStore.data.map { it[thumbnailKey] } }.collectAsState(initial = null)
                        if (!currentThumbnail.isNullOrBlank()) {
                            add(
                                IosMenuItemData(
                                    title = { Text(text = stringResource(R.string.remove_custom_image)) },
                                    icon = {
                                        SymbolIcon(symbol = sf("xmark"), contentDescription = null)
                                    },
                                    onClick = {
                                        coroutineScope.launch {
                                            context.dataStore.edit { settings ->
                                                settings.remove(thumbnailKey)
                                            }
                                        }
                                        onDismiss()
                                    }
                                )
                            )
                        }
                    }
                    add(
                        IosMenuItemData(
                            title = {
                                Text(
                                    text = if (isPinned) "Unpin from Speed dial" else "Pin to Speed dial"
                                )
                            },
                            icon = {
                                SymbolIcon(symbol = if (isPinned) sf("minus") else sf("plus"), contentDescription = null)
                            },
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (isPinned) {
                                        database.speedDialDao.delete(playlist.id)
                                    } else {
                                        database.speedDialDao.insert(
                                            SpeedDialItem(
                                                id = playlist.id,
                                                title = playlist.playlist.name,
                                                subtitle = null,
                                                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                                                type = "PLAYLIST"
                                            )
                                        )
                                    }
                                }
                                onDismiss()
                            }
                        )
                    )
                    if (autoPlaylist != true) {
                        val isPinnedToLibrary = dbPlaylist?.playlist?.pinnedAt != null
                        add(
                            IosMenuItemData(
                                title = {
                                    Text(
                                        text = if (isPinnedToLibrary) stringResource(R.string.unpin_from_library) else stringResource(R.string.pin_to_library)
                                    )
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(if (isPinnedToLibrary) R.drawable.remove else R.drawable.ic_push_pin),
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    database.query {
                                        dbPlaylist?.playlist?.togglePin()?.let { update(it) }
                                    }
                                    onDismiss()
                                }
                            )
                        )
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.delete)) },
                                description = { Text(text = stringResource(R.string.delete_desc)) },
                                icon = {
                                    SymbolIcon(symbol = sf("trash"), contentDescription = null)
                                },
                                onClick = {
                                    showDeletePlaylistDialog = true
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}
