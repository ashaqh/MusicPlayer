@file:OptIn(ExperimentalTextApi::class)

package com.convx.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.convx.music.constants.DarkModeKey
import com.convx.music.constants.IsFirstRunKey
import com.convx.music.constants.PureBlackKey
import com.convx.music.constants.SelectedThemeColorKey
import com.convx.music.ui.component.backdrop.Backdrop
import com.convx.music.ui.component.backdrop.backdrops.layerBackdrop
import com.convx.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.convx.music.ui.component.backdrop.catalog.components.LiquidButton
import com.convx.music.ui.component.backdrop.catalog.components.LiquidToggle
import com.convx.music.ui.component.backdrop.drawBackdrop
import com.convx.music.ui.component.backdrop.effects.blur
import com.convx.music.ui.component.backdrop.effects.lens
import com.convx.music.ui.component.backdrop.effects.vibrancy
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.ListItem
import com.convx.music.ui.ios.compat.Surface
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.screens.settings.DarkMode
import com.convx.music.ui.theme.AppriseTheme
import com.convx.music.ui.theme.DefaultThemeColor
import com.convx.music.ui.theme.SansFlexFontFamily
import com.convx.music.utils.dataStore
import com.convx.music.utils.get
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.utils.rememberPreference
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

// Analog Vinyl + Liquid Aurora Palette
private object VinylAuroraPalette {
    val DeepVelvetBg = Color(0xFF0E0B10)
    val EspressoGlow = Color(0xFF1F1614)
    val AmberGold = Color(0xFFE5A93C)
    val VinylCrimson = Color(0xFFD43F33)
    val AuroraLavender = Color(0xFF8E54E9)
    val AuroraCoral = Color(0xFFFF758C)
    val Champagne = Color(0xFFF5E6CC)
    val WarmMuted = Color(0xFFC7B8A1)
    val GlassRim = Color(0xFFFFFFFF).copy(alpha = 0.22f)
    val GlassFill = Color(0xFF1E1820).copy(alpha = 0.45f)
}

private val BartleEditorialFont = FontFamily(
    Font(R.font.bbh_bartle_regular, FontWeight.Normal)
)

private val JakartaBodyFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val GoogleSansFlex: FontFamily = SansFlexFontFamily

data class OnboardingPageInfo(
    val content: @Composable (onUpdateScrollState: (Boolean) -> Unit) -> Unit
)

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val isFirstRun = dataStore.get(IsFirstRunKey, true)
        val forceShow = intent.getBooleanExtra("FORCE_SHOW", false)

        if (!isFirstRun && !forceShow) {
            finishOnboarding()
            return
        }

        enableEdgeToEdge()
        setContent {
            val darkMode by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val isSystemDark = isSystemInDarkTheme()
            val useDark = when (darkMode) {
                DarkMode.AUTO -> isSystemDark
                DarkMode.ON -> true
                DarkMode.OFF -> false
            }
            val (pureBlackPref) = rememberPreference(PureBlackKey, defaultValue = false)
            val (themeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
            AppriseTheme(
                darkTheme = useDark,
                pureBlack = pureBlackPref && useDark,
                themeColor = Color(themeColorInt),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VinylAuroraPalette.DeepVelvetBg
                ) {
                    WelcomePagerScreen(onFinished = {
                        lifecycleScope.launch {
                            dataStore.edit { it[IsFirstRunKey] = false }
                            if (forceShow) {
                                finish()
                            } else {
                                finishOnboarding()
                            }
                        }
                    })
                }
            }
        }
    }

    private fun finishOnboarding() {
        startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_PLAY_INTRO, true))
        finish()
        // The intro picks up from the black splash; a window slide would break the handoff.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

@Composable
fun WelcomePagerScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()

    val commonAnimSpec = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
    val commonDpAnimSpec = tween<Dp>(durationMillis = 260, easing = FastOutSlowInEasing)

    val topCardShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
    val middleCardShape = RoundedCornerShape(8.dp)
    val bottomCardShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

    val slantedSubHeaderStyle = TextStyle(
        fontFamily = FontFamily(
            Font(
                resId = R.font.sans_flex,
                variationSettings = FontVariation.Settings(
                    FontVariation.slant(-10f),
                    FontVariation.width(108f),
                    FontVariation.weight(320),
                    FontVariation.Setting("GRAD", 80f),
                    FontVariation.Setting("ROND", 100f)
                )
            )
        ),
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp
    )

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPermission = ContextCompat.checkSelfPermission(
                    context,
                    audioPermission
                ) == PackageManager.PERMISSION_GRANTED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasAudioPermission = isGranted }
    )

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasNotificationPermission = isGranted }
    )

    var isLastPageScrolledToEnd by remember { mutableStateOf(false) }

    val pages = listOf(
        // Page 1: Analog Vinyl + Aurora Welcome
        OnboardingPageInfo(
            content = { _ ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(64.dp))

                    Text(
                        text = stringResource(R.string.welcome_to),
                        style = slantedSubHeaderStyle,
                        color = VinylAuroraPalette.Champagne.copy(alpha = 0.85f)
                    )
                    Text(
                        text = stringResource(R.string.brand_wordmark),
                        fontFamily = BartleEditorialFont,
                        fontSize = 44.sp,
                        color = VinylAuroraPalette.AmberGold,
                        lineHeight = 50.sp,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.brand_tagline),
                            fontFamily = JakartaBodyFont,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.2.sp,
                            style = AppTheme.typography.labelLarge,
                            color = VinylAuroraPalette.Champagne.copy(alpha = 0.85f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = CircleShape,
                            color = VinylAuroraPalette.AmberGold.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, VinylAuroraPalette.AmberGold.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                fontFamily = JakartaBodyFont,
                                style = AppTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = VinylAuroraPalette.AmberGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VinylTurntableHero(
                            modifier = Modifier.size(270.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = stringResource(R.string.welcome_preparing_subtitle),
                            fontFamily = JakartaBodyFont,
                            style = AppTheme.typography.bodyLarge,
                            color = VinylAuroraPalette.WarmMuted,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        ),

        // Page 2: Preamp Calibration (Permissions)
        OnboardingPageInfo(
            content = { _ ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(64.dp))

                    Text(
                        text = stringResource(R.string.perm_required),
                        style = slantedSubHeaderStyle,
                        color = VinylAuroraPalette.Champagne.copy(alpha = 0.85f)
                    )
                    Text(
                        text = stringResource(R.string.perm_permissions),
                        fontFamily = BartleEditorialFont,
                        fontSize = 42.sp,
                        color = VinylAuroraPalette.AmberGold,
                        lineHeight = 48.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.perm_intro_text),
                        fontFamily = JakartaBodyFont,
                        style = AppTheme.typography.bodyLarge,
                        color = VinylAuroraPalette.WarmMuted
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    VuSignalBar(
                        activeCount = (if (hasAudioPermission) 1 else 0) + (if (hasNotificationPermission) 1 else 0),
                        backdrop = backdrop
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AuroraGlassPermissionCard(
                            icon = rememberVectorPainter(Icons.Rounded.LibraryMusic),
                            iconColor = VinylAuroraPalette.AmberGold.copy(alpha = 0.22f),
                            iconTint = VinylAuroraPalette.AmberGold,
                            title = "Audio Library",
                            description = "Discover and play your local FLAC, MP3 & lossless tracks.",
                            shape = RoundedCornerShape(24.dp),
                            backdrop = backdrop,
                            checked = hasAudioPermission,
                            onToggle = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (hasAudioPermission) {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                } else {
                                    audioLauncher.launch(audioPermission)
                                }
                            }
                        )

                        AuroraGlassPermissionCard(
                            icon = rememberVectorPainter(Icons.Rounded.Notifications),
                            iconColor = VinylAuroraPalette.AuroraCoral.copy(alpha = 0.22f),
                            iconTint = VinylAuroraPalette.AuroraCoral,
                            title = stringResource(R.string.perm_notif_title),
                            description = stringResource(R.string.perm_notif_desc),
                            shape = RoundedCornerShape(24.dp),
                            backdrop = backdrop,
                            checked = hasNotificationPermission,
                            onToggle = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (hasNotificationPermission) {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        ),

        // Page 3: Liner Notes (Features)
        OnboardingPageInfo(
            content = { onUpdateScroll ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Spacer(modifier = Modifier.height(64.dp))

                    Column(modifier = Modifier.padding(bottom = 16.dp)) {
                        Text(
                            text = stringResource(R.string.feat_discover),
                            style = slantedSubHeaderStyle,
                            color = VinylAuroraPalette.Champagne.copy(alpha = 0.85f)
                        )
                        Text(
                            text = stringResource(R.string.feat_features),
                            fontFamily = BartleEditorialFont,
                            fontSize = 42.sp,
                            color = VinylAuroraPalette.AmberGold,
                            lineHeight = 48.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.feat_intro),
                            fontFamily = JakartaBodyFont,
                            style = AppTheme.typography.bodyLarge,
                            color = VinylAuroraPalette.WarmMuted
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        val scrollState = rememberScrollState()

                        val isAtBottom by remember {
                            derivedStateOf {
                                val max = scrollState.maxValue
                                max == 0 || scrollState.value >= (max - 20)
                            }
                        }

                        LaunchedEffect(isAtBottom) {
                            onUpdateScroll(isAtBottom)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            AuroraGlassFeatureCard(
                                icon = rememberVectorPainter(Icons.Rounded.Lyrics),
                                iconColor = VinylAuroraPalette.AuroraCoral.copy(alpha = 0.22f),
                                iconTint = VinylAuroraPalette.AuroraCoral,
                                title = stringResource(R.string.feat_lyrics_title),
                                description = stringResource(R.string.feat_lyrics_desc),
                                shape = topCardShape,
                                backdrop = backdrop
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            AuroraGlassFeatureCard(
                                icon = rememberVectorPainter(Icons.Rounded.HighQuality),
                                iconColor = VinylAuroraPalette.AmberGold.copy(alpha = 0.22f),
                                iconTint = VinylAuroraPalette.AmberGold,
                                title = stringResource(R.string.feat_quality_title),
                                description = stringResource(R.string.feat_quality_desc),
                                shape = middleCardShape,
                                backdrop = backdrop
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            AuroraGlassFeatureCard(
                                icon = rememberVectorPainter(Icons.Rounded.BlurOn),
                                iconColor = VinylAuroraPalette.AuroraLavender.copy(alpha = 0.25f),
                                iconTint = Color(0xFFCBB2FF),
                                title = stringResource(R.string.feat_glass_title),
                                description = stringResource(R.string.feat_glass_desc),
                                shape = middleCardShape,
                                backdrop = backdrop
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            AuroraGlassFeatureCard(
                                icon = rememberVectorPainter(Icons.Rounded.Gavel),
                                iconColor = VinylAuroraPalette.Champagne.copy(alpha = 0.16f),
                                iconTint = VinylAuroraPalette.Champagne,
                                title = stringResource(R.string.feat_license_title),
                                description = stringResource(R.string.feat_license_desc),
                                shape = bottomCardShape,
                                backdrop = backdrop
                            )

                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val isFirstPage = pagerState.currentPage == 0
    val isLastPage = pagerState.currentPage == pages.size - 1

    BackHandler(enabled = !isFirstPage) {
        scope.launch {
            pagerState.animateScrollToPage(
                pagerState.currentPage - 1,
                animationSpec = commonAnimSpec
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Capturable backdrop with Liquid Aurora mesh + Warm Vinyl ambiance
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
        ) {
            AuroraVinylBackground(pageFraction = pagerState.currentPage.toFloat() + pagerState.currentPageOffsetFraction)
        }

        // Layer 2: Foreground Pager & Liquid Glass Controls
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 700.dp)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    // Three light pages: compose them all up front.
                    beyondViewportPageCount = pages.size - 1,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { index ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val pageOffset = pagerState.getOffsetDistanceInPages(index)
                                val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)
                                alpha = 1f - absOffset
                                translationX = pageOffset * size.width * 0.2f
                                scaleX = 1f - absOffset * 0.08f
                                scaleY = 1f - absOffset * 0.08f
                            }
                    ) {
                        pages[index].content { _ ->
                            if (index == pages.size - 1) {
                                isLastPageScrolledToEnd = true
                            }
                        }
                    }
                }

                // Page Indicator Dots
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.indices.forEach { index ->
                        val selected = index == pagerState.currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (selected) 24.dp else 6.dp,
                            animationSpec = commonDpAnimSpec,
                            label = "dotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(6.dp)
                                .width(dotWidth)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) VinylAuroraPalette.AmberGold
                                    else VinylAuroraPalette.Champagne.copy(alpha = 0.25f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Liquid Glass Navigation Dock
                val backButtonWeight by animateFloatAsState(
                    targetValue = if (isFirstPage) 0.0001f else 1f,
                    animationSpec = commonAnimSpec,
                    label = "backWeight"
                )
                val spacerWeight by animateFloatAsState(
                    targetValue = if (isFirstPage) 0.0001f else 0.06f,
                    animationSpec = commonAnimSpec,
                    label = "spacerWeight"
                )
                val alphaBack by animateFloatAsState(
                    targetValue = if (isFirstPage) 0f else 1f,
                    animationSpec = commonAnimSpec,
                    label = "backAlpha"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .height(58.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(backButtonWeight)
                            .fillMaxHeight()
                            .alpha(alphaBack)
                    ) {
                        LiquidButton(
                            onClick = {
                                if (!isFirstPage) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            pagerState.currentPage - 1,
                                            animationSpec = commonAnimSpec
                                        )
                                    }
                                }
                            },
                            backdrop = backdrop,
                            surfaceColor = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier
                                .fillMaxSize()
                                .border(1.dp, VinylAuroraPalette.GlassRim, CircleShape)
                        ) {
                            Text(
                                text = stringResource(R.string.back_button_desc),
                                fontFamily = JakartaBodyFont,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = VinylAuroraPalette.Champagne
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(spacerWeight))

                    Box(
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                    ) {
                        val isNextEnabled = !isLastPage || isLastPageScrolledToEnd
                        val alphaNext by animateFloatAsState(
                            targetValue = if (isNextEnabled) 1f else 0.45f,
                            label = "nextAlpha"
                        )

                        LiquidButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isLastPage) {
                                    if (isLastPageScrolledToEnd) onFinished()
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            pagerState.currentPage + 1,
                                            animationSpec = commonAnimSpec
                                        )
                                    }
                                }
                            },
                            backdrop = backdrop,
                            tint = VinylAuroraPalette.AmberGold,
                            surfaceColor = VinylAuroraPalette.AmberGold.copy(alpha = 0.25f * alphaNext),
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(alphaNext)
                                .border(
                                    1.dp,
                                    VinylAuroraPalette.AmberGold.copy(alpha = 0.55f),
                                    CircleShape
                                )
                        ) {
                            Text(
                                text = if (isLastPage) {
                                    stringResource(R.string.get_started)
                                } else {
                                    stringResource(R.string.next)
                                },
                                fontFamily = JakartaBodyFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = VinylAuroraPalette.Champagne
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuroraVinylBackground(pageFraction: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "auroraDrift")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auroraPhase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    VinylAuroraPalette.EspressoGlow,
                    VinylAuroraPalette.DeepVelvetBg,
                    Color(0xFF0A080C)
                )
            )
        )

        val w = size.width
        val h = size.height

        // Blob 1: Warm Amber Gold Aurora
        val amberCenter = Offset(
            x = w * (0.25f + 0.12f * cos(phase + pageFraction * 0.6f)),
            y = h * (0.22f + 0.08f * sin(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VinylAuroraPalette.AmberGold.copy(alpha = 0.26f),
                    VinylAuroraPalette.AmberGold.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = amberCenter,
                radius = w * 0.72f
            ),
            radius = w * 0.72f,
            center = amberCenter
        )

        // Blob 2: Aurora Lavender Mist
        val lavenderCenter = Offset(
            x = w * (0.82f - 0.14f * sin(phase * 0.8f + pageFraction * 0.5f)),
            y = h * (0.48f + 0.1f * cos(phase * 0.7f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VinylAuroraPalette.AuroraLavender.copy(alpha = 0.24f),
                    VinylAuroraPalette.AuroraLavender.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = lavenderCenter,
                radius = w * 0.78f
            ),
            radius = w * 0.78f,
            center = lavenderCenter
        )

        // Blob 3: Vinyl Crimson / Coral Glow near bottom dock so Liquid Glass refracts rich color
        val coralCenter = Offset(
            x = w * (0.45f + 0.16f * sin(phase * 1.1f)),
            y = h * (0.84f + 0.05f * cos(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VinylAuroraPalette.VinylCrimson.copy(alpha = 0.24f),
                    VinylAuroraPalette.AuroraCoral.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = coralCenter,
                radius = w * 0.68f
            ),
            radius = w * 0.68f,
            center = coralCenter
        )
    }
}

@Composable
fun VinylTurntableHero(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    var isSpinning by remember { mutableStateOf(true) }

    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val spinDelta by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylAngle"
    )
    var frozenAngle by remember { mutableFloatStateOf(0f) }
    val currentRotation = if (isSpinning) {
        frozenAngle = spinDelta
        spinDelta
    } else {
        frozenAngle
    }

    val tonearmAngle by animateFloatAsState(
        targetValue = if (isSpinning) 22f else 4f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "tonearmAngle"
    )

    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            isSpinning = !isSpinning
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.44f
            val center = Offset(size.width * 0.46f, size.height * 0.52f)

            // Soft ambient halo behind record
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VinylAuroraPalette.AmberGold.copy(alpha = 0.28f),
                        VinylAuroraPalette.AuroraLavender.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.32f
                ),
                radius = radius * 1.32f,
                center = center
            )

            // Rotating Vinyl Record
            rotate(degrees = currentRotation, pivot = center) {
                // Outer disc
                drawCircle(
                    color = Color(0xFF100E12),
                    radius = radius,
                    center = center
                )
                // Specular vinyl sheen
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.0f to Color.White.copy(alpha = 0.14f),
                        0.15f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.10f),
                        0.65f to Color.Transparent,
                        1.0f to Color.White.copy(alpha = 0.14f),
                        center = center
                    ),
                    radius = radius * 0.98f,
                    center = center
                )
                // Grooves
                val grooveFractions = listOf(0.88f, 0.78f, 0.68f, 0.58f, 0.48f)
                grooveFractions.forEach { frac ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = radius * frac,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                // Crimson Center Label
                drawCircle(
                    color = VinylAuroraPalette.VinylCrimson,
                    radius = radius * 0.34f,
                    center = center
                )
                // Amber inner ring on label
                drawCircle(
                    color = VinylAuroraPalette.AmberGold,
                    radius = radius * 0.28f,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // Label alignment notch to make spin obvious
                drawLine(
                    color = VinylAuroraPalette.Champagne.copy(alpha = 0.7f),
                    start = Offset(center.x, center.y - radius * 0.12f),
                    end = Offset(center.x, center.y - radius * 0.26f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                // Center Spindle
                drawCircle(
                    color = VinylAuroraPalette.Champagne,
                    radius = radius * 0.055f,
                    center = center
                )
            }

            // Gold Tonearm
            val pivot = Offset(size.width * 0.88f, size.height * 0.14f)
            rotate(degrees = tonearmAngle, pivot = pivot) {
                // Pivot base
                drawCircle(
                    color = Color(0xFF282226),
                    radius = 12.dp.toPx(),
                    center = pivot
                )
                drawCircle(
                    color = VinylAuroraPalette.AmberGold,
                    radius = 12.dp.toPx(),
                    center = pivot,
                    style = Stroke(width = 2.dp.toPx())
                )
                // Arm shaft
                val armEnd = Offset(pivot.x - radius * 0.16f, pivot.y + radius * 1.18f)
                val path = Path().apply {
                    moveTo(pivot.x, pivot.y)
                    lineTo(pivot.x - 4.dp.toPx(), pivot.y + radius * 0.85f)
                    lineTo(armEnd.x, armEnd.y)
                }
                drawPath(
                    path = path,
                    color = VinylAuroraPalette.AmberGold,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
                // Cartridge head
                drawRoundRect(
                    color = VinylAuroraPalette.Champagne,
                    topLeft = Offset(armEnd.x - 7.dp.toPx(), armEnd.y - 4.dp.toPx()),
                    size = Size(14.dp.toPx(), 18.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun VuSignalBar(
    activeCount: Int,
    backdrop: Backdrop
) {
    val infiniteTransition = rememberInfiniteTransition(label = "vuPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vuPulseVal"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(18.dp) },
                effects = {
                    vibrancy()
                    blur(3.dp.toPx())
                    lens(8.dp.toPx(), 16.dp.toPx())
                },
                onDrawSurface = { drawRect(VinylAuroraPalette.GlassFill) }
            )
            .border(1.dp, VinylAuroraPalette.GlassRim, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (activeCount == 2) "SIGNAL READY • CALIBRATED" else "PREAMP SIGNAL • $activeCount/2 ACTIVE",
            fontFamily = JakartaBodyFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            color = if (activeCount > 0) VinylAuroraPalette.AmberGold else VinylAuroraPalette.WarmMuted
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(18.dp)
        ) {
            val heights = listOf(0.55f, 0.9f, 0.7f, 1f, 0.6f)
            heights.forEachIndexed { idx, base ->
                val active = activeCount > 0
                val barFactor = if (active) (base * pulse).coerceIn(0.25f, 1f) else 0.25f
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight(barFactor)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(
                            if (idx == 3 && active) VinylAuroraPalette.VinylCrimson
                            else if (active) VinylAuroraPalette.AmberGold
                            else VinylAuroraPalette.WarmMuted.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

@Composable
fun AuroraGlassPermissionCard(
    icon: Painter,
    iconColor: Color,
    iconTint: Color,
    title: String,
    description: String,
    shape: Shape,
    backdrop: Backdrop,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val cornerShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cornerShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cornerShape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(10.dp.toPx(), 20.dp.toPx())
                },
                onDrawSurface = { drawRect(VinylAuroraPalette.GlassFill) }
            )
            .border(1.dp, VinylAuroraPalette.GlassRim, cornerShape)
            .clickable(onClick = onToggle)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaBodyFont,
                    color = VinylAuroraPalette.Champagne
                )
            },
            supportingContent = {
                Text(
                    text = description,
                    fontFamily = JakartaBodyFont,
                    color = VinylAuroraPalette.WarmMuted
                )
            },
            leadingContent = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = iconColor,
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.4f)),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            trailingContent = {
                LiquidToggle(
                    selected = { checked },
                    onSelect = { onToggle() },
                    backdrop = backdrop
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
fun AuroraGlassFeatureCard(
    icon: Painter,
    iconColor: Color,
    iconTint: Color,
    title: String,
    description: String,
    shape: Shape,
    backdrop: Backdrop
) {
    val cornerShape = (shape as? RoundedCornerShape) ?: RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cornerShape)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { cornerShape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(10.dp.toPx(), 20.dp.toPx())
                },
                onDrawSurface = { drawRect(VinylAuroraPalette.GlassFill) }
            )
            .border(1.dp, VinylAuroraPalette.GlassRim, cornerShape)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontFamily = JakartaBodyFont,
                    color = VinylAuroraPalette.Champagne
                )
            },
            supportingContent = {
                Text(
                    text = description,
                    fontFamily = JakartaBodyFont,
                    color = VinylAuroraPalette.WarmMuted
                )
            },
            leadingContent = {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = iconColor,
                    border = BorderStroke(1.dp, iconTint.copy(alpha = 0.4f)),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}
