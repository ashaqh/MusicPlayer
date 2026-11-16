/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.menu

import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import com.convx.music.ui.component.menuBottomInset
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import com.convx.music.ui.ios.compat.CircularProgressIndicator
import com.convx.music.ui.ios.compat.HorizontalDivider
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.ListItem
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.convx.music.LocalDatabase
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.constants.ListItemHeight
import com.convx.music.constants.ListThumbnailSize
import com.convx.music.db.entities.ArtistEntity
import com.convx.music.db.entities.Event
import com.convx.music.db.entities.SpeedDialItem
import com.convx.music.db.entities.PlaylistSong
import com.convx.music.db.entities.Song
import com.convx.music.extensions.toMediaItem
import com.convx.music.ui.component.ListDialog
import com.convx.music.ui.component.LocalBottomSheetPageState
import com.convx.music.ui.component.IosMenuGroup
import com.convx.music.ui.component.IosMenuItemData
import com.convx.music.ui.component.NewAction
import com.convx.music.ui.component.NewActionGrid
import com.convx.music.ui.component.SongListItem
import com.convx.music.ui.component.TextFieldDialog
import android.widget.Toast
import com.convx.music.utils.ExternalTagEditor
import com.convx.music.utils.listItemShape
import com.convx.music.ui.utils.ShowMediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SongMenu(
    originalSong: Song,
    event: Event? = null,
    navController: NavController,
    playlistSong: PlaylistSong? = null,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val ringtoneViewModel = com.convx.music.LocalRingtoneViewModel.current
    val database = LocalDatabase.current.get()
    val playerConnection = LocalPlayerConnection.current ?: return
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong
    val coroutineScope = rememberCoroutineScope()

    val isPinned by database.speedDialDao.isPinned(song.id).collectAsState(initial = false)

    val orderedArtists by produceState(initialValue = emptyList<ArtistEntity>(), song) {
        withContext(Dispatchers.IO) {
            val artistMaps = database.songArtistMap(song.id).sortedBy { it.position }
            val sorted = artistMaps.mapNotNull { map ->
                song.artists.firstOrNull { it.id == map.artistId }
            }
            value = sorted
        }
    }

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val TextFieldValueSaver: Saver<TextFieldValue, *> = Saver(
        save = { it.text },
        restore = { text -> TextFieldValue(text, TextRange(text.length)) }
    )

    var titleField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.song.title))
    }

    var artistField by rememberSaveable(stateSaver = TextFieldValueSaver) {
        mutableStateOf(TextFieldValue(song.artists.firstOrNull()?.name.orEmpty()))
    }

    var showSplitNotice by rememberSaveable { mutableStateOf(false) }
    val (stemQuality) = com.convx.music.utils.rememberEnumPreference(com.convx.music.constants.StemQualityKey, com.convx.music.playback.smart.StemQuality.FAST)
    val stemCount = if (stemQuality == com.convx.music.playback.smart.StemQuality.DEMUCS && com.convx.music.playback.smart.DemucsModel.isInstalled(context)) 4 else 2
    if (showSplitNotice) {
        com.convx.music.ui.component.StemNoticeDialog(
            bytes = com.convx.music.playback.smart.StemStore.estimatedBytes(song.song.duration, stemCount),
            onDismiss = { showSplitNotice = false },
            onConfirm = {
                showSplitNotice = false
                com.convx.music.playback.dj.LibraryAnalysis.analyze(
                    context,
                    listOf(com.convx.music.playback.dj.LibraryAnalysis.Target(song.id, song.song.duration, song.song.title, stems = true)),
                )
                onDismiss()
            },
        )
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = {
                SymbolIcon(symbol = sf("pencil"), contentDescription = null)
            },
            title = {
                Text(text = stringResource(R.string.edit_song))
            },
            textFields = listOf(
                stringResource(R.string.song_title) to titleField,
                stringResource(R.string.artist_name) to artistField
            ),
            onTextFieldsChange = { index, newValue ->
                if (index == 0) titleField = newValue
                else artistField = newValue
            },
            onDoneMultiple = { values ->
                val newTitle = values[0]
                val newArtist = values[1]

                coroutineScope.launch {
                    database.query {
                        update(song.song.copy(title = newTitle))
                        val artist = song.artists.firstOrNull()
                        if (artist != null) {
                            update(artist.copy(name = newArtist))
                        }
                    }

                    showEditDialog = false
                    onDismiss()
                }
            },
            onDismiss = { showEditDialog = false }
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var showErrorPlaylistAddDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            listOf(song.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        },
    )

    if (showErrorPlaylistAddDialog) {
        ListDialog(
            onDismiss = {
                showErrorPlaylistAddDialog = false
                onDismiss()
            },
        ) {
            item {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.already_in_playlist)) },
                    leadingContent = {
                        Image(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(AppTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize),
                        )
                    },
                    modifier = Modifier.clickable { showErrorPlaylistAddDialog = false },
                )
            }

            items(listOf(song)) { song ->
                SongListItem(song = song)
            }
        }
    }

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false },
        ) {
            items(
                items = song.artists.distinctBy { it.id },
                key = { it.id },
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape),
                        )
                    }
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }

    SongListItem(
        song = song,
        badges = {},
        shape = listItemShape(0, 2),
        trailingContent = {
            IconButton(
                onClick = {
                    val s = song.song.toggleLike()
                    database.query {
                        update(s)
                    }
                },
            ) {
                SymbolIcon(symbol = if (song.song.liked) sf("heart.fill") else sf("heart"), contentDescription = null, tint = if (song.song.liked) AppTheme.colorScheme.error else LocalContentColor.current)
            }
        },
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    val bottomSheetPageState = LocalBottomSheetPageState.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val isLocal = song.song.isLocal

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
                actions = listOfNotNull(
                    NewAction(
                        icon = { Icon(painterResource(R.drawable.sparks), contentDescription = null, modifier = Modifier.size(28.dp)) },
                        text = stringResource(R.string.song_insight_title),
                        onClick = {
                            onDismiss()
                            navController.navigate("song_insight/${android.net.Uri.encode(song.id)}")
                        },
                    ),
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("pencil"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.edit),
                        onClick = { showEditDialog = true }
                    ),
                    if (isLocal) NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("waveform"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.analyze_song),
                        onClick = {
                            com.convx.music.playback.dj.LibraryAnalysis.analyze(
                                context,
                                listOf(com.convx.music.playback.dj.LibraryAnalysis.Target(song.id, song.song.duration, song.song.title)),
                            )
                            onDismiss()
                        }
                    ) else null,
                    if (isLocal && song.song.duration in 1..com.convx.music.playback.smart.StemSeparator.MAX_SECONDS) NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("waveform.path"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.split_stems),
                        onClick = { showSplitNotice = true }
                    ) else null,
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.add_to_an_playlist),
                        onClick = { showChoosePlaylistDialog = true }
                    ),
                    NewAction(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.sparks),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = AppTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = stringResource(R.string.ai_dj_mix_from_song),
                        onClick = {
                            onDismiss()
                            navController.navigate("ai_dj_set?seedSongId=${song.id}")
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
                            playerConnection.playNext(song.toMediaItem())
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
                            playerConnection.addToQueue(song.toMediaItem())
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
                                    text = if (isPinned) "Unpin from Speed dial" else "Pin to Speed dial"
                                )
                            },
                            icon = {
                                SymbolIcon(symbol = if (isPinned) sf("minus") else sf("plus"), contentDescription = null)
                            },
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    if (isPinned) {
                                        database.speedDialDao.delete(song.id)
                                    } else {
                                        database.speedDialDao.insert(
                                            SpeedDialItem(
                                                id = song.id,
                                                title = song.song.title,
                                                subtitle = song.artists.joinToString(", ") { it.name },
                                                thumbnailUrl = song.song.thumbnailUrl,
                                                type = "SONG",
                                                explicit = song.song.explicit
                                            )
                                        )
                                    }
                                }
                                onDismiss()
                            }
                        )
                    )
                    add(
                        IosMenuItemData(
                            title = {
                                Text(
                                    text = stringResource(
                                        if (song.song.inLibrary == null) R.string.add_to_library
                                        else R.string.remove_from_library
                                    )
                                )
                            },
                            description = { Text(text = stringResource(R.string.add_to_library_desc)) },
                            icon = {
                                SymbolIcon(symbol =
                                        if (song.song.inLibrary == null) sf("text.badge.plus")
                                        else sf("text.badge.checkmark")
                                    , contentDescription = null)
                            },
                            onClick = {
                                database.query {
                                    update(song.song.toggleLibrary())
                                }
                            }
                        )
                    )
                    if (event != null) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_history)) },
                                icon = {
                                    SymbolIcon(symbol = sf("trash"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    database.query {
                                        delete(event)
                                    }
                                }
                            )
                        )
                    }
                    if (playlistSong != null) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.remove_from_playlist)) },
                                icon = {
                                    SymbolIcon(symbol = sf("trash"), contentDescription = null)
                                },
                                onClick = {
                                    database.query {
                                        removePlaylistEntry(playlistSong.map)
                                    }
                                    onDismiss()
                                }
                            )
                        )
                    }
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.set_as_ringtone)) },
                            icon = {
                                SymbolIcon(symbol = sf("bell"), contentDescription = null)
                            },
                            onClick = {
                                ringtoneViewModel.showTrimmer(
                                    song.id,
                                    song.song.title,
                                    song.artists.joinToString { it.name },
                                    song.song.duration
                                )
                                onDismiss()
                            }
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            IosMenuGroup(
                items = buildList {
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.view_artist)) },
                            description = { Text(text = song.artists.joinToString { it.name }) },
                            icon = {
                                SymbolIcon(symbol = sf("music.mic"), contentDescription = null)
                            },
                            onClick = {
                                if (song.artists.size == 1) {
                                    navController.navigate("artist/${song.artists[0].id}")
                                    onDismiss()
                                } else {
                                    showSelectArtistDialog = true
                                }
                            }
                        )
                    )
                    val albumId = song.song.albumId?.takeIf { it.isNotBlank() } ?: song.album?.id?.takeIf { it.isNotBlank() }
                    val albumName = song.song.albumName ?: song.album?.title
                    if (!albumId.isNullOrBlank()) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.view_album)) },
                                description = albumName?.let { name ->
                                    { Text(text = name) }
                                },
                                icon = {
                                    SymbolIcon(symbol = sf("square.stack"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    navController.navigate("album/$albumId")
                                }
                            )
                        )
                    }
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.details)) },
                            description = { Text(text = stringResource(R.string.details_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("info.circle"), contentDescription = null)
                            },
                            onClick = {
                                onDismiss()
                                bottomSheetPageState.showFloating {
                                    ShowMediaInfo(song.id)
                                }
                            }
                        )
                    )
                    // Local files only: a song's id is its MediaStore content URI, which
                    // is the one thing an external editor can actually be handed. There
                    // is nothing on disk to edit for a YouTube track.
                    if (song.song.isLocal) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.edit_tags)) },
                                description = {
                                    Text(text = stringResource(R.string.edit_tags_desc))
                                },
                                icon = {
                                    SymbolIcon(symbol = sf("pencil"), contentDescription = null)
                                },
                                onClick = {
                                    onDismiss()
                                    val opened = ExternalTagEditor.launch(
                                        context = context,
                                        contentUri = song.id,
                                        mimeType = null,
                                    )
                                    if (!opened) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.edit_tags_no_app),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            )
                        )
                    }
                }
            )
        }
    }
}
