/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.menu

import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import com.convx.music.ui.component.menuBottomInset
import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import com.convx.music.ui.ios.compat.CircularProgressIndicator
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.convx.music.LocalDatabase
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.db.entities.PlaylistSongMap
import com.convx.music.db.entities.Song
import com.convx.music.extensions.toMediaItem
import com.convx.music.models.MediaMetadata
import com.convx.music.models.toMediaMetadata
import com.convx.music.playback.queues.ListQueue
import com.convx.music.ui.component.IosMenuGroup
import com.convx.music.ui.component.IosMenuItemData
import com.convx.music.ui.component.NewAction
import com.convx.music.ui.component.NewActionGrid
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SelectionSongMenu(
    songSelection: List<Song>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
    songPosition: List<PlaylistSongMap>? = emptyList(),
) {
    val database = LocalDatabase.current.get()
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return

    val allInLibrary by remember {
        mutableStateOf(
            songSelection.all {
                it.song.inLibrary != null
            },
        )
    }

    val allLiked by remember(songSelection) {
        mutableStateOf(
            songSelection.isNotEmpty() && songSelection.all {
                it.song.liked
            },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            songSelection.map { it.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

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
                            playerConnection.playQueue(
                                ListQueue(
                                    title = "Selection",
                                    items = songSelection.map { it.toMediaItem() },
                                ),
                            )
                            clearAction()
                        }
                    ),
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("shuffle"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.shuffle),
                        onClick = {
                            onDismiss()
                            playerConnection.playQueue(
                                ListQueue(
                                    title = "Selection",
                                    items = songSelection.shuffled().map { it.toMediaItem() },
                                ),
                            )
                            clearAction()
                        }
                    ),
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.add_to_playlist),
                        onClick = {
                            showChoosePlaylistDialog = true
                        }
                    )
                ),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
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
                            onDismiss()
                            playerConnection.playNext(songSelection.map { it.toMediaItem() })
                            clearAction()
                        }
                    ),
                    IosMenuItemData(
                        title = { Text(text = stringResource(R.string.shuffle)) },
                        description = { Text(text = stringResource(R.string.add_to_queue_desc)) },
                        icon = {
                            SymbolIcon(symbol = sf("shuffle"), contentDescription = null)
                        },
                        onClick = {
                            onDismiss()
                            playerConnection.playQueue(
                                ListQueue(
                                    title = "Selection",
                                    items = songSelection.shuffled().map { it.toMediaItem() },
                                ),
                            )
                            clearAction()
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
                            playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                            clearAction()
                        }
                    ),
                    IosMenuItemData(
                        title = { Text(text = stringResource(R.string.add_to_playlist)) },
                        description = { Text(text = stringResource(R.string.add_to_playlist_desc)) },
                        icon = {
                            SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null)
                        },
                        onClick = {
                            showChoosePlaylistDialog = true
                        }
                    ),
                    IosMenuItemData(
                        title = {
                            Text(
                                text = stringResource(
                                    if (allInLibrary) R.string.remove_from_library else R.string.add_to_library
                                )
                            )
                        },
                        icon = {
                            SymbolIcon(symbol =
                                    if (allInLibrary) sf("text.badge.checkmark") else sf("text.badge.plus")
                                , contentDescription = null)
                        },
                        onClick = {
                            if (allInLibrary) {
                                database.query {
                                    songSelection.forEach { song ->
                                        inLibrary(song.id, null)
                                    }
                                }
                            } else {
                                database.transaction {
                                    songSelection.forEach { song ->
                                        insert(song.toMediaMetadata())
                                        inLibrary(song.id, LocalDateTime.now())
                                    }
                                }
                            }
                        }
                    )
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            IosMenuGroup(
                items = buildList {
                    add(
                        IosMenuItemData(
                            title = {
                                Text(
                                    text = stringResource(
                                        if (allLiked) R.string.dislike_all else R.string.like_all
                                    )
                                )
                            },
                            icon = {
                                SymbolIcon(symbol = 
                                        if (allLiked) sf("heart.fill") else sf("heart")
                                    , contentDescription = null)
                            },
                            onClick = {
                                val allLiked = songSelection.all { it.song.liked }
                                onDismiss()
                                database.query {
                                    songSelection.forEach { song ->
                                        if ((!allLiked && !song.song.liked) || allLiked) {
                                            val s = song.song.toggleLike()
                                            update(s)
                                        }
                                    }
                                }
                            }
                        )
                    )
                    if (songPosition?.isNotEmpty() == true) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.delete)) },
                                icon = {
                                    SymbolIcon(symbol = sf("trash"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    val ordered = songPosition.sortedBy { it.position }
                                    database.transaction {
                                        ordered.forEachIndexed { offset, cur ->
                                            removePlaylistEntry(cur.copy(position = cur.position - offset))
                                        }
                                    }
                                    clearAction()
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun SelectionMediaMetadataMenu(
    songSelection: List<MediaMetadata>,
    currentItems: List<Timeline.Window>,
    onDismiss: () -> Unit,
    clearAction: () -> Unit,
) {
    val database = LocalDatabase.current.get()
    val playerConnection = LocalPlayerConnection.current ?: return

    val allLiked by remember(songSelection) {
        mutableStateOf(songSelection.isNotEmpty() && songSelection.all { it.liked })
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            songSelection.map {
                database.insert(it)
                it.id
            }
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + menuBottomInset(),
        ),
    ) {
        item {
            IosMenuGroup(
                items = buildList {
                    if (currentItems.isNotEmpty()) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.delete)) },
                                icon = {
                                    SymbolIcon(symbol = sf("trash"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    var i = 0
                                    currentItems.forEach { cur ->
                                        if (playerConnection.player.availableCommands.contains(
                                                Player.COMMAND_CHANGE_MEDIA_ITEMS
                                            )
                                        ) {
                                            playerConnection.player.removeMediaItem(cur.firstPeriodIndex - i++)
                                        }
                                    }
                                    clearAction()
                                }
                            )
                        )
                    }
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.play)) },
                            icon = {
                                SymbolIcon(symbol = sf("play.fill"), contentDescription = null)
                            },
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = "Selection",
                                        items = songSelection.map { it.toMediaItem() },
                                    ),
                                )
                                clearAction()
                            }
                        )
                    )
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.shuffle)) },
                            icon = {
                                SymbolIcon(symbol = sf("shuffle"), contentDescription = null)
                            },
                            onClick = {
                                onDismiss()
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = "Selection",
                                        items = songSelection.shuffled().map { it.toMediaItem() },
                                    ),
                                )
                                clearAction()
                            }
                        )
                    )
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.add_to_queue)) },
                            icon = {
                                SymbolIcon(symbol = sf("music.note.list"), contentDescription = null)
                            },
                            onClick = {
                                onDismiss()
                                playerConnection.addToQueue(songSelection.map { it.toMediaItem() })
                                clearAction()
                            }
                        )
                    )
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.add_to_playlist)) },
                            icon = {
                                SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null)
                            },
                            onClick = {
                                showChoosePlaylistDialog = true
                            }
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            IosMenuGroup(
                items = listOf(
                    IosMenuItemData(
                        title = {
                            Text(
                                text = stringResource(R.string.like_all)
                            )
                        },
                        icon = {
                            SymbolIcon(symbol =
                                    if (allLiked) sf("heart.fill") else sf("heart")
                                , contentDescription = null)
                        },
                        onClick = {
                            database.query {
                                if (allLiked) {
                                    songSelection.forEach { song ->
                                        update(song.toSongEntity().toggleLike())
                                    }
                                } else {
                                    songSelection.filter { !it.liked }.forEach { song ->
                                        update(song.toSongEntity().toggleLike())
                                    }
                                }
                            }
                        }
                    )
                )
            )
        }
    }
}
