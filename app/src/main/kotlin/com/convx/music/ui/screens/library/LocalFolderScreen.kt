/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.convx.music.ui.component.FrostedTopBar
import com.convx.music.ui.component.TopFadeBlur
import com.convx.music.ui.component.topBarContentPadding
import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import androidx.compose.foundation.lazy.rememberLazyListState
import com.convx.music.ui.component.ListScrollRail
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.convx.music.ui.ios.compat.Button
import androidx.compose.material3.ButtonDefaults
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import com.composables.sfsymbols.SfSymbols
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.convx.music.ui.component.rememberTitleCollapseProgressState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.convx.music.LocalDatabase
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.db.entities.Song
import com.convx.music.extensions.toMediaItem
import com.convx.music.playback.queues.ListQueue
import com.convx.music.ui.component.EmptyPlaceholder
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.component.SongListItem
import com.convx.music.ui.component.SeededShimmerHero
import com.convx.music.ui.menu.SongMenu
import com.convx.music.ui.utils.bounceClick
import com.convx.music.ui.theme.rememberGlobalAccentColors
import com.convx.music.utils.LocalFolderIndex
import com.convx.music.utils.listItemShape
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.text.font.FontWeight

/**
 * Every scanned song that lives in one on-device folder.
 *
 * The folder membership comes from MediaStore (the song table has no path
 * column); the rows themselves come from Room by id, so downloads, likes and
 * menus behave exactly as they do everywhere else.
 */
@Composable
fun LocalFolderScreen(
    navController: NavController,
    path: String,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current.get()
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val (accentColor, onAccentColor) = rememberGlobalAccentColors()

    val folderState by produceState(initialValue = false to emptyList<Song>(), path) {
        val ids = LocalFolderIndex.songIdsFor(context, path)
        if (ids.isEmpty()) {
            value = true to emptyList()
            return@produceState
        }
        // MediaStore's order is the folder's order; Room's IN query is not, so
        // restore it rather than showing an arbitrary shuffle.
        val order = ids.withIndex().associate { (index, id) -> id to index }
        database.songsByIds(ids).collect { rows ->
            value = true to rows.sortedBy { order[it.id] ?: Int.MAX_VALUE }
        }
    }
    val (folderLoaded, songs) = folderState

    val lazyListState = rememberLazyListState()

    fun playFolder(shuffle: Boolean) {
        val queueSongs = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(items = queueSongs.map { it.toMediaItem() }, startIndex = 0),
        )
    }

    val folderName = path.substringAfterLast('/').ifBlank { stringResource(R.string.folders) }
    val hazeState = remember { HazeState() }
    val pageInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val titleCollapse by rememberTitleCollapseProgressState(lazyListState, titleItemKey = "title")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(
                start = pageInsets.calculateStartPadding(dir),
                top = topBarContentPadding(),
                end = pageInsets.calculateEndPadding(dir),
                bottom = pageInsets.calculateBottomPadding() + 16.dp,
            ),
        ) {
            if (songs.isNotEmpty()) {
                item(key = "folder_artwork") {
                    SeededShimmerHero(
                        thumbUrl = songs.firstOrNull()?.thumbnailUrl,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            item(key = "title") {
                Text(
                    text = folderName,
                    style = AppTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .semantics { heading() },
                )
            }

            if (songs.isNotEmpty()) {
                item(key = "actions") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { playFolder(shuffle = false) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor,
                            ),
                        ) { Text(stringResource(R.string.play)) }
                        Button(
                            onClick = { playFolder(shuffle = true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor,
                            ),
                        ) { Text(stringResource(R.string.shuffle)) }
                    }
                }
            }

            if (folderLoaded && songs.isEmpty()) {
                item(key = "folder_empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.no_local_files),
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
            ) { index, song ->
                SongListItem(
                    song = song,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    showLikedIcon = false,
                    shape = listItemShape(index, songs.size),
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier = Modifier.size(48.dp),
                        ) {
                            SymbolIcon(
                                bounceOnEnter = true,
                                symbol = sf("ellipsis"),
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    ListQueue(items = songs.map { it.toMediaItem() }, startIndex = index),
                                )
                            }
                        },
                )
            }
        }

        // The folder keeps MediaStore's own order, which is not alphabetical, so the rail
        // is a proportional thumb.
        ListScrollRail(
            lazyListState = lazyListState,
            itemCount = songs.size,
            sectionIndexMap = null,
        )

        TopFadeBlur(
            hazeState = hazeState,
            pageColor = AppTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FrostedTopBar(
            title = folderName,
            titleAlpha = titleCollapse,
            onBack = { navController.navigateUp() },
        )
    }
}
