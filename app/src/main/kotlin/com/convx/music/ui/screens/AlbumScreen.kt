/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens

import com.convx.music.ui.utils.sharedTitleTarget

import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import com.valentinilk.shimmer.shimmer
import com.convx.music.ui.utils.Motion
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.convx.music.ui.utils.bounceClick
import com.convx.music.ui.utils.combinedBounceClick

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.convx.music.ui.ios.compat.Checkbox
import com.convx.music.ui.ios.compat.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.ripple
import com.convx.music.ui.ios.compat.Surface
import androidx.compose.foundation.shape.CircleShape
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color
import com.convx.music.constants.AppBarHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.convx.music.ui.utils.resize
import com.convx.music.LocalDatabase
import com.convx.music.LocalTabView
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.composables.sfsymbols.SfSymbols
import androidx.compose.ui.res.painterResource
import com.composables.sfsymbols.monochrome.SFECircle
import com.composables.sfsymbols.monochrome.SFXmark
import com.composables.sfsymbols.monochrome.SFShuffle
import com.composables.sfsymbols.monochrome.SFSquareStack
import com.composables.sfsymbols.monochrome.SFStar
import com.convx.music.constants.HideExplicitKey
import com.convx.music.db.entities.Album
import com.convx.music.extensions.toMediaItem
import com.convx.music.playback.queues.ListQueue
import com.convx.music.playback.queues.LocalAlbumRadio
import com.convx.music.ui.component.AlbumGradient
import com.convx.music.ui.component.ExpandableText
import com.convx.music.ui.component.ContrastCircleButton
import com.convx.music.ui.component.GlassCircleButton
import com.convx.music.ui.component.HeroCardHeader
import com.convx.music.ui.component.IconButton
import com.convx.music.ui.component.LinkSegment
import com.convx.music.ui.component.LocalAppBackdrop
import com.convx.music.ui.component.GlassComponent
import com.convx.music.ui.component.LocalGlassEffectConfig
import com.convx.music.ui.component.resolvedTextColor
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.component.isGlassAllowed
import androidx.compose.ui.graphics.isSpecified
import com.convx.music.LocalAmbientBackgroundColor
import com.convx.music.ui.component.asSoftIconTint
import com.convx.music.ui.utils.additiveChrome
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.convx.music.ui.component.EmptyPlaceholder
import com.convx.music.ui.component.NavigationTitle
import com.convx.music.ui.component.SongListItem
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.menu.AlbumMenu
import com.convx.music.ui.menu.SelectionSongMenu
import com.convx.music.ui.menu.SongMenu
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.utils.backToMain
import com.convx.music.ui.utils.albumRoute
import com.convx.music.ui.utils.artistRoute
import com.convx.music.ui.utils.pendingSharedArtworkThumbFor
import com.convx.music.ui.component.SongRowSkeleton
import com.convx.music.ui.component.ShimmerLine
import com.convx.music.ui.component.ThumbThenFullHero
import com.convx.music.ui.component.SeededShimmerHero
import com.convx.music.ui.component.GlassPillRow
import com.convx.music.ui.component.pillGlassBackground
import com.convx.music.ui.component.backExitModifier
import com.convx.music.ui.component.rememberDetailChromeEntrance
import com.convx.music.ui.utils.rememberHeroZoom
import com.convx.music.ui.utils.heroPullZoom
import com.convx.music.ui.utils.listOverscroll
import com.convx.music.ui.utils.fadingEdge
import com.convx.music.ui.theme.HeroTintedContent
import com.convx.music.ui.component.rememberHeroTint
import com.convx.music.ui.component.rememberHeroTopBlur
import com.convx.music.utils.listItemShape
import com.convx.music.utils.makeTimeString
import com.convx.music.utils.rememberPreference
import com.convx.music.ui.component.AnimatedPlayPauseIcon
import com.convx.music.viewmodels.AlbumViewModel
import com.convx.music.ui.component.HeroMergeBand
import com.convx.music.ui.component.HeroMergeBandHeight
import com.convx.music.ui.component.HeroScrollBlur
import com.convx.music.ui.component.HeroTopBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current.get()
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val albumWithSongs by viewModel.albumWithSongs.collectAsState()
    val description by viewModel.description.collectAsState()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val filteredSongs = remember(albumWithSongs, hideExplicit) {
        var songs = albumWithSongs?.songs ?: emptyList()
        if (hideExplicit) {
            songs = songs.filter { !it.song.explicit }
        }
        songs
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }
    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    val hasExplicitContent = remember(albumWithSongs) {
        albumWithSongs?.album?.explicit == true
    }

    val lazyListState = rememberLazyListState()

    // Per-screen Haze source: the artwork head + its tint plane together become the
    // backdrop that HeroMergeBand/HeroTopBlur sample through, so the hero visibly
    // dissolves into the page and under the status bar. Self-contained per screen —
    // no global threading through the composition tree.
    val hazeState = remember { HazeState() }

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset < 100
        }
    }

    // How far scrolled past the hero art, 0..1 — same progressive dark scrim
    // treatment as the Artist screen's floating chrome.
    val chromeScrimAlpha by remember {
        derivedStateOf {
            val scrolledPx = if (lazyListState.firstVisibleItemIndex == 0) {
                lazyListState.firstVisibleItemScrollOffset.toFloat()
            } else {
                300f
            }
            (scrolledPx / 300f).coerceIn(0f, 1f)
        }
    }

    // Scroll progress driving the heroart progressive blur + the top blur strip:
    // hoisted so the header artwork and the floating chrome share one value.
    val heroTopBlurProgress = rememberHeroTopBlur(lazyListState)
    val chrome = com.convx.music.ui.component.rememberDetailChromeEntrance(iconCount = 2)

    val instantThumbUrl = viewModel.thumbArg
        ?: pendingSharedArtworkThumbFor(viewModel.albumId)
        ?: albumWithSongs?.album?.thumbnailUrl
    val bgUrl = instantThumbUrl ?: albumWithSongs?.album?.thumbnailUrl

    val heroTint = rememberHeroTint(bgUrl)
    val tint by androidx.compose.animation.animateColorAsState(
        heroTint,
        label = "albumPage",
    )
    com.convx.music.PublishAmbientBackgroundColor(tint)
    val onTint = androidx.compose.ui.graphics.Color.White

    val glassConfig = LocalGlassEffectConfig.current
    val useGlass = glassConfig.isEnabledFor(GlassComponent.NAV_BAR) && isGlassAllowed()
    val chromeShape = ContinuousRoundedRectangle(percent = 50)
    val chromeContentColor = if (useGlass) glassConfig.resolvedTextColor() else AppTheme.colorScheme.onSurface

    // The unattached backdrop avoids a RenderNode cycle in the floating chrome.
    val heroBackdrop = rememberLayerBackdrop()

    // The chrome samples this attached sibling backdrop, not its own draw pass.
    // Include the page tint so the blurred surface matches the visible page.
    val listBackdrop = rememberLayerBackdrop(
        onDraw = remember(tint) {
            val bg = tint
            { drawRect(bg); drawContent() }
        }
    )
    val backdropFreeze = rememberBackdropFreeze()
    val heroZoom = rememberHeroZoom()

    // Blend the artwork tint into the page background below the hero to hide the color seam.
    val screenBackground = AppTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tint)
    ) {
    HeroTintedContent(tint = tint, backdrop = heroBackdrop) {
    // Capture through a plain Box; LazyColumn's recycled item layers do not flatten reliably.
    Box(modifier = Modifier
            .nestedScroll(backdropFreeze.connection)

            // Cache list content so sibling redraws do not re-record the backdrop.
            .graphicsLayer()
            .layerBackdrop(listBackdrop, frozen = backdropFreeze.frozen)
            .graphicsLayer()
            .hazeSource(hazeState)) {
    LazyColumn(
        state = lazyListState,
        overscrollEffect = heroZoom.listOverscroll(),
        modifier = Modifier.heroPullZoom(heroZoom),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        val albumWithSongs = albumWithSongs
        if (albumWithSongs != null && albumWithSongs.songs.isNotEmpty()) {
             item(key = "album_header", contentType = "header") {
                val title = albumWithSongs.album.title
                val artistName = albumWithSongs.artists.joinToString { it.name }
                val albumInfo = buildString {
                    albumWithSongs.album.year?.let { append(it).append(" \u00b7 ") }
                    append(pluralStringResource(R.plurals.n_song, filteredSongs.size, filteredSongs.size))
                    val duration = filteredSongs.sumOf { it.song.duration }
                    if (duration > 0) append(" \u00b7 ").append(makeTimeString(duration * 1000L))
                }
                com.convx.music.ui.component.CollectionDetailHeader(
                    title = title,
                    subtitle = artistName,
                    artwork = instantThumbUrl ?: albumWithSongs.album.thumbnailUrl,
                    metadata = albumInfo,
                    description = description,
                    foreground = onTint,
                    playing = isPlaying && filteredSongs.any { it.id == mediaMetadata?.id },
                    enabled = filteredSongs.isNotEmpty(),
                    onPlay = {
                        if (filteredSongs.any { it.id == mediaMetadata?.id }) playerConnection.togglePlayPause()
                        else playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                    },
                    onShuffle = {
                        playerConnection.playQueue(LocalAlbumRadio(albumWithSongs.copy(songs = albumWithSongs.songs.shuffled())))
                    },
                )
            }

            if (filteredSongs.isNotEmpty()) {


                itemsIndexed(
                    items = filteredSongs,
                    key = { _, song -> song.id },
                    contentType = { _, _ -> "song_row" },
                ) { index, song ->
                    val onCheckedChange: (Boolean) -> Unit = {
                        if (it) {
                            selection.add(song.id)
                        } else {
                            selection.remove(song.id)
                        }
                    }

                    SongListItem(
                        song = song,
                        albumIndex = index + 1,
                        subtitleColor = onTint.copy(alpha = 0.78f),
                        subtitleAdditiveChrome = false,
                        leadingDivider = index == 0,
                        thumbnailStartInset = 0.dp,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        showInLibraryIcon = true,
                        shape = listItemShape(index, filteredSongs.size),
                        flat = true,
                        trailingContent = {
                            if (inSelectMode) {
                                Checkbox(
                                    checked = song.id in selection,
                                    onCheckedChange = onCheckedChange
                                )
                            } else {
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
                                ) {
                                    SymbolIcon(
                                        symbol = sf("ellipsis"),
                                        contentDescription = stringResource(R.string.more_options),
                                    )
                                }
                            }
                        },
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .combinedBounceClick(
                                onClick = {
                                    if (inSelectMode) {
                                        onCheckedChange(song.id !in selection)
                                    } else if (song.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            LocalAlbumRadio(albumWithSongs, startIndex = index),
                                        )
                                    }
                                },
                                onLongClick = {
                                    if (!inSelectMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        inSelectMode = true
                                        onCheckedChange(true)
                                    }
                                },
                            ),
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(50.dp))
            }
        } else {
            // Delay empty-state reveal briefly so initial DB emission swaps content
            // seamlessly, while deleted or empty albums show a clear offline fallback.
            item(key = "album_missing_or_loading") {
                val timedOut by androidx.compose.runtime.produceState(initialValue = false, viewModel.albumId) {
                    kotlinx.coroutines.delay(1_200L)
                    value = true
                }
                if (albumWithSongs != null || timedOut) {
                    EmptyPlaceholder(
                        icon = R.drawable.album,
                        text = stringResource(R.string.library_album_empty),
                        modifier = Modifier.padding(top = 96.dp),
                    )
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        com.convx.music.ui.component.CollectionDetailHeader(
                            title = com.convx.music.ui.utils.selectedArtworkTitle().orEmpty(),
                            artwork = instantThumbUrl,
                            metadata = "",
                            foreground = onTint,
                            playing = false,
                            enabled = false,
                            onPlay = {},
                            onShuffle = {},
                            titleContent = {
                                Box(Modifier.fillMaxWidth().height(75.dp), contentAlignment = Alignment.Center) {
                                    ShimmerLine(.55f, 34.dp)
                                }
                            },
                        )
                        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                            ShimmerLine(fraction = 0.25f, height = 20.dp)
                        }
                        repeat(6) { index ->
                            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                                SongRowSkeleton(index = index)
                            }
                        }
                    }
                }
            }
        }
    }
    }

        // Float navigation and selection actions over the hero; the scrim darkens as the list scrolls.
        CompositionLocalProvider(LocalAppBackdrop provides listBackdrop) {
        // Draw the blur before the chrome row so it stays behind the buttons.
        com.convx.music.ui.component.TopFadeBlur(
            hazeState = hazeState,
            pageColor = tint,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppBarHeight * 2.5f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f * chromeScrimAlpha),
                            0.35f to Color.Black.copy(alpha = 0.4f * chromeScrimAlpha),
                            0.7f to Color.Black.copy(alpha = 0.12f * chromeScrimAlpha),
                            1f to Color.Transparent,
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(appTopBarWindowInsets())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
            val ambientColor = LocalAmbientBackgroundColor.current.value
            val pillIconTint = if (useGlass && ambientColor.isSpecified) ambientColor.asSoftIconTint() else chromeContentColor
            if (inSelectMode) {
                GlassCircleButton(onClick = onExitSelectionMode) {
                    SymbolIcon(symbol = sf("SFXmark"), contentDescription = stringResource(R.string.dismiss))
                }

                Text(
                    text = pluralStringResource(R.plurals.n_selected, selection.size, selection.size),
                    style = AppTheme.typography.titleMedium,
                    color = chromeContentColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                GlassPillRow(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(chromeShape)
                        .then(pillGlassBackground(chromeShape))
                        .padding(horizontal = 8.dp),
                ) {
                    Checkbox(
                        checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == filteredSongs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(filteredSongs.map { it.id })
                            }
                        }
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection = selection.mapNotNull { songId ->
                                        filteredSongs.find { it.id == songId }
                                    },
                                    onDismiss = menuState::dismiss,
                                    clearAction = onExitSelectionMode
                                )
                            }
                        }
                    ) {
                        SymbolIcon(symbol = sf("ellipsis"), contentDescription = stringResource(R.string.more_options), modifier = Modifier.additiveChrome(useGlass && ambientColor.isSpecified), tint = pillIconTint)
                    }
                }
            } else {
                GlassCircleButton(
                    onClick = { navController.navigateUp() },
                    onLongClick = { navController.backToMain() },
                    modifier = with(chrome) { Modifier.backEnter().then(backExitModifier()) },
                ) {
                    SymbolIcon(symbol = sf("chevron.left"), contentDescription = stringResource(R.string.back_button_desc))
                }

                Spacer(Modifier.weight(1f))

                albumWithSongs?.let { albumWithSongs ->
                    GlassPillRow(
                        modifier = with(chrome) { Modifier.containerEnter() }
                            .height(48.dp)
                            .clip(chromeShape)
                            .then(pillGlassBackground(chromeShape))
                            .padding(horizontal = 8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = Album(
                                            albumWithSongs.album,
                                            albumWithSongs.artists
                                        ),
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            SymbolIcon(symbol = sf("ellipsis"), contentDescription = stringResource(R.string.more_options), size = 20.dp, modifier = with(chrome) {
                                    Modifier.iconEnter(0)
                                        .additiveChrome(useGlass && ambientColor.isSpecified)
                                }, tint = pillIconTint)
                        }
                    }
                }
            }
        }
        }
        }
    }
    }
}
