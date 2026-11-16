package com.convx.music.ui.menu

import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import com.convx.music.ui.component.menuBottomInset
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.convx.music.ui.ios.compat.CircularProgressIndicator
import com.convx.music.ui.ios.compat.HorizontalDivider
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFBellFill
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.navigation.NavController
import com.convx.music.LocalDatabase
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.constants.ListItemHeight
import com.convx.music.extensions.toggleRepeatMode
import com.convx.music.models.MediaMetadata
import com.convx.music.ui.component.BottomSheetState
import com.convx.music.ui.component.ListDialog
import com.convx.music.ui.component.IosMenuGroup
import com.convx.music.ui.component.IosMenuItemData
import com.convx.music.ui.component.NewAction
import com.convx.music.ui.component.NewActionGrid
import com.convx.music.ui.component.VolumeSlider
import com.convx.music.ui.theme.rememberGlobalAccentColors
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OldPlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    playerBottomSheetState: BottomSheetState,
    onShowDetailsDialog: () -> Unit,
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val ringtoneViewModel = com.convx.music.LocalRingtoneViewModel.current
    val database = LocalDatabase.current.get()
    val playerConnection = LocalPlayerConnection.current ?: return
    val playerVolume = playerConnection.service.playerVolume.collectAsStateWithLifecycle()

    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()

    val artists = remember(mediaMetadata.artists) {
        mediaMetadata.artists.filter { it.id != null }
    }

    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectArtistDialog by rememberSaveable { mutableStateOf(false) }
    var showPitchTempoDialog by rememberSaveable { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            database.transaction { insert(mediaMetadata) }
            onDismiss()
            listOf(mediaMetadata.id)
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    if (showSelectArtistDialog) {
        ListDialog(onDismiss = { showSelectArtistDialog = false }) {
            items(artists, key = { it.id!! }) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            playerBottomSheetState.collapseSoft()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showPitchTempoDialog) {
        TempoPitchDialog(onDismiss = { showPitchTempoDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 6.dp),
    ) {
        // "Hide Volume Bar" only affects the player's own inline volume row
        // now — this menu copy always shows regardless of the setting.
        VolumeSlider(
            value = playerVolume.value,
            onValueChange = { volume ->
                playerConnection.service.playerVolume.value = volume
            },
            modifier = Modifier.fillMaxWidth(),
            accentColor = rememberGlobalAccentColors().first
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + menuBottomInset(),
        ),
    ) {
        // Playlist Add
        item {
            NewActionGrid(
                actions = listOf(
                    NewAction(
                        icon = {
                            SymbolIcon(symbol = sf("text.badge.plus"), contentDescription = null, size = 32.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
                        },
                        text = stringResource(R.string.add_to_an_playlist),
                        onClick = { showChoosePlaylistDialog = true }
                    )
                ),
                columns = 1,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            IosMenuGroup(
                items = listOf(
                    IosMenuItemData(
                        title = { Text(stringResource(R.string.shuffle)) },
                        icon = {
                            SymbolIcon(symbol = sf("shuffle"), contentDescription = null, size = 24.dp, tint = if (shuffleModeEnabled) AppTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current)
                        },
                        onClick = {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                            onDismiss()
                        }
                    ),
                    run {
                        val isLiked = currentSong?.song?.liked == true
                        IosMenuItemData(
                            title = { Text(stringResource(if (isLiked) R.string.liked else R.string.like)) },
                            icon = {
                                SymbolIcon(symbol = if (isLiked) sf("heart.fill") else sf("heart"), contentDescription = null, size = 24.dp, tint = if (isLiked) AppTheme.colorScheme.error else androidx.compose.material3.LocalContentColor.current)
                            },
                            onClick = {
                                playerConnection.toggleLike()
                                onDismiss()
                            }
                        )
                    },
                    IosMenuItemData(
                        title = { Text(stringResource(R.string.repeat)) },
                        icon = {
                            Icon(
                                painter = painterResource(
                                    when (repeatMode) {
                                        Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                        else -> R.drawable.repeat
                                    }
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) AppTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current
                            )
                        },
                        onClick = {
                            playerConnection.player.toggleRepeatMode()
                        }
                    )
                )
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // Artist & Add to library
        item {
            IosMenuGroup(
                items = buildList {
                    if (artists.isNotEmpty()) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.view_artist)) },
                                description = {
                                    Text(
                                        text = mediaMetadata.artists.joinToString { it.name },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                icon = {
                                    SymbolIcon(symbol = sf("music.mic"), contentDescription = null, size = 24.dp)
                                },
                                onClick = {
                                    if (mediaMetadata.artists.size == 1) {
                                        navController.navigate("artist/${mediaMetadata.artists[0].id}")
                                        playerBottomSheetState.collapseSoft()
                                        onDismiss()
                                    } else {
                                        showSelectArtistDialog = true
                                    }
                                }
                            )
                        )
                    }

                    if (mediaMetadata.album != null) {
                        add(
                            IosMenuItemData(
                                title = { Text(text = stringResource(R.string.view_album)) },
                                description = {
                                    Text(
                                        text = mediaMetadata.album.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                icon = {
                                    SymbolIcon(symbol = sf("square.stack"), contentDescription = null, size = 24.dp)
                                },
                                onClick = {
                                    navController.navigate("album/${mediaMetadata.album.id}")
                                    playerBottomSheetState.collapseSoft()
                                    onDismiss()
                                }
                            )
                        )
                    }

                    val isInLibrary = librarySong?.song?.inLibrary != null
                    add(
                        IosMenuItemData(
                            title = { 
                                Text(
                                    text = stringResource(
                                        if (isInLibrary) R.string.remove_from_library
                                        else R.string.add_to_library
                                    )
                                )
                            },
                            icon = {
                                SymbolIcon(symbol = 
                                        if (isInLibrary) sf("text.badge.checkmark")
                                        else sf("text.badge.plus")
                                    , contentDescription = null, size = 24.dp)
                            },
                            onClick = {
                                playerConnection.toggleLibrary()
                                onDismiss()
                            }
                        )
                    )
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // details, eq, adv
        item {
            IosMenuGroup(
                items = buildList {
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.details)) },
                            description = { Text(text = stringResource(R.string.details_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("info.circle"), contentDescription = null, size = 24.dp)
                            },
                            onClick = {
                                onShowDetailsDialog()
                                onDismiss()
                            }
                        )
                    )

                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.equalizer)) },
                            description = { Text(text = stringResource(R.string.equalizer_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("slider.vertical.3"), contentDescription = null, size = 24.dp)
                            },
                            onClick = {
                                navController.navigate("settings/equalizer")
                                onDismiss()
                            }
                        )
                    )

                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.advanced)) },
                            description = { Text(text = stringResource(R.string.advanced_desc)) },
                            icon = {
                                SymbolIcon(symbol = sf("slider.horizontal.3"), contentDescription = null, size = 24.dp)
                            },
                            onClick = {
                                showPitchTempoDialog = true
                            }
                        )
                    )
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.set_as_ringtone)) },
                            icon = {
                                SymbolIcon(symbol = sf("SFBellFill"), contentDescription = null, size = 24.dp)
                            },
                            onClick = {
                                ringtoneViewModel.showTrimmer(
                                    mediaMetadata.id,
                                    mediaMetadata.title,
                                    mediaMetadata.artists.joinToString { it.name },
                                    mediaMetadata.duration
                                )
                                onDismiss()
                            }
                        )
                    )
                    add(
                        IosMenuItemData(
                            title = { Text(text = stringResource(R.string.ambient_mode)) },
                            icon = {
                                SymbolIcon(symbol = sf("arrow.up.left.and.arrow.down.right"), contentDescription = null, size = 24.dp)
                            },
                            onClick = {
                                navController.navigate("ambient_mode")
                                playerBottomSheetState.collapseSoft()
                                onDismiss()
                            }
                        )
                    )
                }
            )
        }
    }
}
