/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.artist

import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import com.convx.music.ui.utils.appTopBarWindowInsets
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.unit.Dp
import com.convx.music.ui.theme.AppleColors
import com.convx.music.ui.component.GlassPillRow
import com.convx.music.ui.component.StretchPillIcon
import com.convx.music.ui.component.pillGlassBackground
import com.convx.music.ui.component.LocalGlassEffectConfig
import com.convx.music.ui.component.resolvedTextColor
import com.convx.music.ui.utils.bounceClick
import com.convx.music.ui.utils.combinedBounceClick
import com.convx.music.ui.utils.albumRoute
import com.convx.music.ui.utils.artistRoute
import com.convx.music.ui.utils.bleedStart
import com.convx.music.ui.utils.plusStart

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.luminance
import com.convx.music.ui.component.BlurredBackdropImage
import com.convx.music.ui.component.bakeRamp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.convx.music.ui.ios.compat.HorizontalDivider
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.LocalContentColor
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.OutlinedButton
import com.convx.music.ui.ios.compat.ripple
import com.convx.music.ui.ios.compat.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.convx.music.ui.ios.compat.Surface
import com.convx.music.ui.ios.compat.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFHeart
import com.composables.sfsymbols.monochrome.SFHeartFill
import com.composables.sfsymbols.monochrome.SFInfoCircle
import com.composables.sfsymbols.monochrome.SFLink
import com.composables.sfsymbols.monochrome.SFMusicMicrophone
import com.composables.sfsymbols.monochrome.SFPlus
import com.composables.sfsymbols.monochrome.SFStar
import com.composables.sfsymbols.monochrome.SFStarFill
import com.composables.sfsymbols.monochrome.SFInfo
import com.composables.sfsymbols.monochrome.SFChevronRight
import com.convx.music.ui.component.EmptyPlaceholder
import com.convx.music.LocalTabView
import androidx.compose.material.icons.Icons
import com.convx.music.ui.component.ArtistHeroImage
import com.convx.music.ui.component.TopFadeBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import coil3.compose.AsyncImage
import com.convx.music.ui.utils.additiveChrome
import com.convx.music.ui.utils.sharedArtworkSource
import com.convx.music.LocalDatabase
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.constants.AppBarHeight
import com.convx.music.constants.HideExplicitKey
import com.convx.music.db.entities.ArtistEntity
import com.convx.music.extensions.toMediaItem
import com.convx.music.playback.queues.ListQueue
import com.convx.music.ui.component.AnimatedPlayPauseIcon
import com.convx.music.ui.component.AlbumGridItem
import com.convx.music.ui.component.GlassCircleButton
import com.convx.music.ui.component.LocalAppBackdrop
import com.convx.music.ui.component.LocalMenuState
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberBackdropFreeze
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.convx.music.ui.component.NavigationTitle
import com.convx.music.ui.component.SongListItem
import com.convx.music.ui.theme.HeroTintedContent
import com.convx.music.ui.component.rememberHeroTint
import com.convx.music.ui.component.shimmer.ButtonPlaceholder
import com.convx.music.ui.component.shimmer.ListItemPlaceHolder
import com.convx.music.ui.component.shimmer.ShimmerHost
import com.convx.music.ui.component.shimmer.TextPlaceholder
import com.convx.music.ui.menu.AlbumMenu
import com.convx.music.ui.menu.ArtistMenu
import com.convx.music.ui.menu.SongMenu
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.LocalIosColors
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.theme.LocalAccentTextColor
import com.convx.music.ui.theme.LocalMetadataColor
import com.convx.music.ui.theme.rememberBrandFontFamily
import com.convx.music.ui.theme.rememberCustomArtistFontFamily
import com.convx.music.ui.utils.backToMain
import com.convx.music.ui.utils.rememberHeroZoom
import com.convx.music.ui.utils.heroPullZoom
import com.convx.music.ui.utils.listOverscroll
import com.convx.music.ui.utils.fadingEdge
import com.convx.music.ui.utils.resize
import com.convx.music.utils.listItemShape
import com.convx.music.constants.PureBlackHeroBackgroundKey
import com.convx.music.utils.rememberPreference
import com.convx.music.viewmodels.ArtistViewModel
import com.valentinilk.shimmer.shimmer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.convx.music.ui.component.floatingtabbar.gooey
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Apple Music artist page type: white, with the secondary tiers as plain alpha steps. */
private val ArtistTextTiers = listOf(
    Color.White,
    Color.White.copy(alpha = 0.6f),
    Color.White.copy(alpha = 0.35f),
    Color.White.copy(alpha = 0.24f),
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current.get()
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val libraryArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val libraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()

    // Per-screen Haze source: the list content the post-hero TopFadeBlur
    // samples once scrolled past the photo.
    val hazeState = remember { HazeState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current

    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val headerOffset = with(density) {
        -(systemBarsTopPadding + AppBarHeight).roundToPx()
    }

    // Drives the back/actions scrim as the hero scrolls away.
    val chromeScrimThresholdPx = with(density) { 220.dp.toPx() }
    val chromeScrimAlpha by remember {
        derivedStateOf {
            val scrolledPx = if (lazyListState.firstVisibleItemIndex == 0) {
                lazyListState.firstVisibleItemScrollOffset.toFloat()
            } else {
                chromeScrimThresholdPx
            }
            (scrolledPx / chromeScrimThresholdPx).coerceIn(0f, 1f)
        }
    }

    // Seed the hero with the fastest available local artwork while page data loads.
    val artistThumbnail = libraryArtist?.artist?.thumbnailUrl
    val instantArtistThumb = com.convx.music.ui.component.detailSeed(
        viewModel.thumbArg,
        com.convx.music.ui.utils.pendingSharedArtworkThumbFor(viewModel.artistId),
        artistThumbnail,
    )
    val artistChrome = com.convx.music.ui.component.rememberDetailChromeEntrance(iconCount = 1)

    // Avoid attaching a backdrop inside the root NavHost layer; that would capture itself.
    // This unattached backdrop keeps hero chrome translucent without live refraction.
    val heroBackdrop = rememberLayerBackdrop()

    // The floating chrome samples this sibling list backdrop, so it does not capture itself.
    val heroZoom = rememberHeroZoom()

    val heroTint = rememberHeroTint(instantArtistThumb ?: artistThumbnail)
    val tint by androidx.compose.animation.animateColorAsState(heroTint, label = "artistPage")
    val pageBgRgb = tint.toArgb()
    val onTint = Color.White
    val textTiers = ArtistTextTiers
    val chromeTextColor = textTiers[1]

    // Include the page tint in the captured backdrop so blur matches the visible page.
    val listBackdrop = rememberLayerBackdrop(
        onDraw = remember(tint) {
            val bg = tint
            { drawRect(bg); drawContent() }
        }
    )
    val backdropFreeze = rememberBackdropFreeze()

    // A flat fill of `tint` reads as a hard-edged color mismatch once the hero
    // art ends — the eased foot wash inside ArtistHeroImage settles the join
    // before the list begins instead.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(tint)
    ) {
    val wideArtistHeader = maxWidth > maxHeight && maxWidth >= 600.dp
    val heroSide = if (wideArtistHeader) (maxWidth * 0.42f).coerceAtMost(360.dp) else maxWidth
    HeroTintedContent(tint = tint, backdrop = heroBackdrop, darkChrome = true) {
      // HeroTintedContent only sees the flat tint, so provide colors derived from the mesh
      // for Compose text, metadata, accents, and iOS components.
      val baseIosColors = IosTheme.colors
      val meshIosColors = remember(baseIosColors, textTiers, tint) {
          baseIosColors.copy(
              isDark = true,
              systemBackground = tint,
              secondarySystemBackground = tint,
              tertiarySystemBackground = tint,
              systemGroupedBackground = tint,
              secondarySystemGroupedBackground = tint,
              tertiarySystemGroupedBackground = tint,
              label = textTiers[0],
              secondaryLabel = textTiers[1],
              tertiaryLabel = textTiers[2],
              separator = textTiers[0].copy(alpha = 0.18f),
              opaqueSeparator = textTiers[0].copy(alpha = 0.24f),
          )
      }
      CompositionLocalProvider(
          LocalAccentTextColor provides textTiers[0],
          LocalContentColor provides textTiers[0],
          // The engine's secondary tier, not its tertiary: the artist name under a
          // song title is the one piece of row metadata people actually read, and the
          // 3.0:1 tier is a step too far back for it on a bright mesh.
          LocalMetadataColor provides textTiers[1],
          LocalIosColors provides meshIosColors,
      ) {
        val sideInset = LocalPlayerAwareWindowInsets.current
            .asPaddingValues()
            .calculateStartPadding(LocalLayoutDirection.current)

        // Capture through a plain Box; LazyColumn's recycled item layers do not flatten reliably.
        Box(modifier = Modifier
            .fillMaxSize()
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
            if (libraryArtist == null) {
                item(key = "shimmer") {
                    Column(
                        modifier = Modifier
                            .offset {
                                IntOffset(x = 0, y = headerOffset)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .shimmer()
                                    .background(Color.White.copy(alpha = 0.08f)),
                            )
                        }
                    ShimmerHost {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .padding(bottom = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ButtonPlaceholder(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(52.dp)
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ButtonPlaceholder(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(52.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .shimmer()
                                            .background(
                                                AppTheme.colorScheme.onSurface,
                                                RoundedCornerShape(26.dp)
                                            )
                                    )
                                }
                            }
                        }
                        repeat(6) {
                            ListItemPlaceHolder()
                        }
                    }
                    }
                }
            } else {
                item(key = "header") {
                    val thumbnail = libraryArtist?.artist?.thumbnailUrl
                    val artistName = libraryArtist?.artist?.name
                    val displayArtistName = artistName?.titlecaseWords() ?: "Unknown"
                    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
                    // Queue metadata can omit the artist ID, so also compare artist names.
                    val currentArtists = mediaMetadata?.artists.orEmpty()
                    val metadataMatchesArtist = currentArtists.any { artist ->
                        artist.id == viewModel.artistId ||
                            (artistName != null && artist.name.equals(artistName, ignoreCase = true))
                    }
                    val currentIsThisArtist = metadataMatchesArtist ||
                        (artistName != null && queueTitle == artistName)
                    val isPlayingThisArtist = isPlaying && currentIsThisArtist
                    val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null

                    val onPlay: () -> Unit = {
                        when {
                            isPlayingThisArtist -> playerConnection.player.pause()
                            currentIsThisArtist -> playerConnection.play()
                            else -> {
                                val songs = if (hideExplicit) {
                                    librarySongs.filterNot { it.song.explicit }
                                } else {
                                    librarySongs
                                }
                                if (songs.isNotEmpty()) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = artistName ?: "Artist",
                                            items = songs.map { it.toMediaItem() },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    val onSubscribe: () -> Unit = {
                        database.transaction {
                            val artist = libraryArtist?.artist
                            if (artist != null) {
                                update(artist.toggleLike())
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        val fallbackArtworkUrl = thumbnail ?: instantArtistThumb
                        ArtistHeroImage(
                            artworkUrl = fallbackArtworkUrl,
                            appleArtworkUrl = null,
                            pageColor = tint,
                            heroScale = { heroZoom.scale },
                            loading = fallbackArtworkUrl.isNullOrBlank() && libraryArtist == null,
                            scrollBlur = { 0f },
                            videoFrame = { 0 },
                            modifier = Modifier.width(heroSide).offset { IntOffset(x = 0, y = headerOffset) },
                            overlay = {},
                        )

                        // Offset this content so the artwork remains visible above the lazy item.
                        val itemTopOnScreen = with(density) { (-headerOffset).toDp() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = if (wideArtistHeader) heroSide else 0.dp)
                                .padding(
                                    top = if (wideArtistHeader) {
                                        12.dp
                                    } else {
                                        (heroSide - ArtistWordmarkFootInset - ArtistWordmarkHeight - itemTopOnScreen)
                                            .coerceAtLeast(0.dp)
                                    },
                                ),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.BottomCenter,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(ArtistWordmarkHeight)
                                            .padding(horizontal = 20.dp),
                                    ) {
                                        ArtistWordmark(name = displayArtistName)
                                    }
                                    Spacer(modifier = Modifier.height(18.dp))

                                    ArtistActionRow(
                                        pageBgRgb = pageBgRgb,
                                        isPlaying = isPlayingThisArtist,
                                        isSubscribed = isSubscribed,
                                        onPlay = onPlay,
                                        onSubscribe = onSubscribe,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }


                if (librarySongs.isNotEmpty()) {
                        item(key = "local_songs_title") {
                            ArtistSectionHeader(
                                title = stringResource(R.string.songs),
                                modifier = Modifier.animateItem().artistTitleChrome(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/songs")
                                }
                            )
                        }

                        val filteredLibrarySongs = if (hideExplicit) {
                            librarySongs.filter { !it.song.explicit }
                        } else {
                            librarySongs
                        }
                        itemsIndexed(
                            items = filteredLibrarySongs,
                            key = { index, item -> "local_song_${item.id}_$index" }
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                showInLibraryIcon = true,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                shape = listItemShape(index, filteredLibrarySongs.size),
                                flat = true,
                                secondaryAdditiveChrome = true,
                                // Align thumbnails with section titles and inset only the row separators.
                                thumbnailStartInset = 0.dp,
                                leadingDivider = index == 0,
                                trailingDividerInset = if (index == filteredLibrarySongs.lastIndex) {
                                    null
                                } else {
                                    ArtistInterRowDividerInset
                                },
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
                                        modifier = Modifier.additiveChrome(),
                                    ) {
                                        SymbolIcon(symbol = sf("ellipsis"), contentDescription = null, size = 22.dp, tint = Color.White.copy(alpha = 0.6f))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedBounceClick(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = libraryArtist?.artist?.name ?: "Unknown Artist",
                                                        items = filteredLibrarySongs.map { it.toMediaItem() },
                                                        startIndex = index
                                                    )
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )
                                    .animateItem(),
                            )
                        }
                    }

                    if (libraryAlbums.isNotEmpty()) {
                        item(key = "local_albums_title") {
                            ArtistSectionHeader(
                                title = stringResource(R.string.albums),
                                modifier = Modifier.animateItem().artistTitleChrome(),
                                onClick = {
                                    navController.navigate("artist/${viewModel.artistId}/albums")
                                }
                            )
                        }

                        item(key = "local_albums_list") {
                            val filteredLibraryAlbums = if (hideExplicit) {
                                libraryAlbums.filter { !it.album.explicit }
                            } else {
                                libraryAlbums
                            }
                            LazyRow(
                                contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues().plusStart(sideInset),
                                modifier = Modifier.bleedStart(sideInset),
                            ) {
                                itemsIndexed(
                                    // indexOf(it) in the key lambda was a linear scan per
                                    // item; the index is already supplied here.
                                    items = filteredLibraryAlbums,
                                    key = { index, it -> "local_album_${it.id}_$index" }
                                ) { _, album ->
                                    AlbumGridItem(
                                        album = album,
                                        isActive = mediaMetadata?.album?.id == album.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedBounceClick(
                                                onClick = {
                                                    navController.navigate(albumRoute(album.id, album.thumbnailUrl))
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        AlbumMenu(
                                                            originalAlbum = album,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }

                if (librarySongs.isEmpty() && libraryAlbums.isEmpty()) {
                    item(key = "artist_empty") {
                        EmptyPlaceholder(
                            icon = R.drawable.artist,
                            text = stringResource(R.string.library_artist_empty),
                            modifier = Modifier.padding(top = 48.dp),
                        )
                    }
                }
            }
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )

        // Keep the glass buttons readable over both the hero and scrolled content.
        CompositionLocalProvider(LocalAppBackdrop provides listBackdrop) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            // The nav fade follows the app theme, not the (always dark)
            // artwork tint: a light fade behind the chrome in light mode.
            val navScrimBase =
                if (AppTheme.colorScheme.surface.luminance() <= 0.5f) Color.Black
                else Color.White
            val navScrim = remember(navScrimBase) {
                Brush.verticalGradient(
                    0f to navScrimBase.copy(alpha = 0.55f),
                    0.35f to navScrimBase.copy(alpha = 0.4f),
                    0.7f to navScrimBase.copy(alpha = 0.12f),
                    1f to Color.Transparent,
                )
            }
            // Alpha read in the draw phase: scrolling redraws this strip, never recomposes.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppBarHeight * 2.5f)
                    .drawBehind { drawRect(navScrim, alpha = chromeScrimAlpha) }
            )

            // Draw the sampled haze above the navigation scrim so it remains
            // visible over the hero photo as well as the scrolled list.
            TopFadeBlur(
                hazeState = hazeState,
                pageColor = tint,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(appTopBarWindowInsets())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
            GlassCircleButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                modifier = with(artistChrome) {
                    Modifier.backEnter().then(com.convx.music.ui.component.backExitModifier())
                },
            ) {
                SymbolIcon(symbol = sf("chevron.left"), contentDescription = null)
            }

            libraryArtist?.let { artist ->
                GlassPillRow(
                    modifier = with(artistChrome) { Modifier.containerEnter() }
                        .height(44.dp)
                        .then(pillGlassBackground(ArtistCapsuleShape))
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides LocalGlassEffectConfig.current.resolvedTextColor(),
                    ) {
                        StretchPillIcon(
                            entrance = artistChrome,
                            index = 0,
                            modifier = Modifier.clickable {
                                menuState.show {
                                    ArtistMenu(
                                        originalArtist = artist,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ) {
                            SymbolIcon(symbol = sf("ellipsis"), contentDescription = null, size = 24.dp)
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
}

private val ArtistWordmarkHeight = 75.dp
private val ArtistWordmarkMaxWidth = 286.dp

/** Gap between the wordmark box and the hero foot. */
private val ArtistWordmarkFootInset = 4.dp

private val ArtistCapsuleShape = com.convx.music.ui.component.shapes.ContinuousRoundedRectangle(percent = 50)

@Composable
internal fun ArtistWordmark(
    name: String,
) {
    Text(
        text = name,
        color = Color.White,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = rememberCustomArtistFontFamily(),
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ArtistActionRow(
    pageBgRgb: Int,
    isPlaying: Boolean,
    isSubscribed: Boolean,
    onPlay: () -> Unit,
    onSubscribe: () -> Unit,
) {
    val accent = AppleColors.color(AppleColors.playFill(null))
    val controlFill = Color.White.copy(alpha = AppleColors.CONTROL_FILL_ALPHA)
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ArtistControlCircle(size = 73.dp, fill = accent, onClick = onPlay) {
            AnimatedPlayPauseIcon(
                isPlaying = isPlaying,
                tint = AppleColors.color(AppleColors.playGlyph(pageBgRgb)),
                size = 36.dp,
            )
        }
        ArtistControlCircle(size = 50.dp, fill = controlFill, onClick = onSubscribe) {
            SymbolIcon(symbol = if (isSubscribed) sf("SFStarFill") else sf("SFStar"), contentDescription = stringResource(R.string.favorite), size = 24.dp, tint = accent)
        }
    }
}

@Composable
private fun ArtistControlCircle(
    size: Dp,
    fill: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .bounceClick(onClick = onClick),
    ) {
        content()
    }
}

/** "Top Songs ›": 22pt bold on the 20pt gutter, chevron only when the section opens. */
@Composable
private fun ArtistSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    /** Smaller heading used above the song list. */
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(start = 20.dp, end = 20.dp, top = if (compact) 8.dp else 24.dp, bottom = if (compact) 14.dp else 16.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = if (compact) 20.sp else 22.sp,
            lineHeight = if (compact) 25.sp else 28.sp,
            letterSpacing = if (compact) (-0.45).sp else (-0.26).sp,
            fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            SymbolIcon(symbol = sf("SFChevronRight"), contentDescription = null, size = 16.dp, tint = Color.White.copy(alpha = 0.6f))
        }
    }
}

/** Capitalizes each word's first letter while preserving the remaining casing. */
private fun String.titlecaseWords(): String =
    split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercaseChar() }
    }

/** Secondary text on this page: white added onto the mesh, player-style. */
private const val ArtistChromeAlpha = 0.45f

/** Titles only get a hint: brightest line, backdrop glow. */
private const val ArtistTitleChromeAlpha = 0.85f

/**
 * Inter-row rules stop just inside the thumbnails; the section's outer rules
 * (first row's leading, last row's trailing) stay full width with padding.
 */
private val ArtistInterRowDividerInset = 28.dp

// Draw modifiers only, no layout nodes. Alpha after additiveChrome, never before.
private fun Modifier.artistChrome() =
    additiveChrome().graphicsLayer { alpha = ArtistChromeAlpha }

private fun Modifier.artistTitleChrome() =
    additiveChrome().graphicsLayer { alpha = ArtistTitleChromeAlpha }
