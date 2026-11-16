package com.convx.music.ui.screens.settings

import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFMoonFill
import com.composables.sfsymbols.monochrome.SFMoonphaseFirstQuarterInverse
import com.composables.sfsymbols.monochrome.SFSunMinFill
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.R
import com.convx.music.constants.AppFont
import com.convx.music.constants.DarkModeKey
import com.convx.music.constants.DynamicThemeKey
import com.convx.music.constants.PureBlackKey
import com.convx.music.constants.PureBlackMiniPlayerKey
import com.convx.music.constants.SelectedFontKey
import com.convx.music.constants.SelectedThemeColorKey
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Card
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.IconButton
import com.convx.music.ui.ios.compat.Surface
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TextButton
import com.convx.music.ui.ios.compat.ripple
import com.convx.music.ui.component.ColorPickerDialog
import com.convx.music.ui.theme.AppleTokens
import com.convx.music.ui.theme.DefaultThemeColor
import com.convx.music.ui.theme.AppriseTheme
import com.convx.music.ui.utils.bounceClick
import com.convx.music.ui.utils.combinedBounceClick
import com.convx.music.utils.rememberEnumPreference
import com.convx.music.utils.rememberPreference
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    navController: NavController,
) {
    val (darkMode, onDarkModeChange) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val (pureBlack, onPureBlackChangeRaw) = rememberPreference(PureBlackKey, defaultValue = false)
    val (_, onPureBlackMiniPlayerChange) = rememberPreference(PureBlackMiniPlayerKey, defaultValue = false)
    val (selectedThemeColorInt, onSelectedThemeColorChange) = rememberPreference(
        SelectedThemeColorKey,
        DefaultThemeColor.toArgb()
    )
    val (_, onDynamicThemeChange) = rememberPreference(DynamicThemeKey, defaultValue = true)
    val (selectedFontValue) = rememberPreference(SelectedFontKey, defaultValue = AppFont.PLUS_JAKARTA_SANS.value)
    val fontLabel = when (AppFont.fromValue(selectedFontValue)) {
        AppFont.SYSTEM -> stringResource(R.string.font_system)
        AppFont.GOOGLE_SANS, AppFont.SANS_FLEX -> stringResource(R.string.font_sans_flex)
        AppFont.OUTFIT -> stringResource(R.string.font_outfit)
        AppFont.PLUS_JAKARTA_SANS -> stringResource(R.string.font_plus_jakarta_sans)
    }
    val onFontClick: () -> Unit = { navController.navigate("settings/appearance/font") }

    val selectedThemeColor = Color(selectedThemeColorInt)
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val themesUnlocked = com.apprise.music.entitlement.rememberCapabilityEnabled(
        com.apprise.music.entitlement.FeatureCapability.PREMIUM_THEMES
    )
    var showThemeUpgradeDialog by remember { mutableStateOf(false) }

    if (showThemeUpgradeDialog) {
        val (title, desc) = com.apprise.music.entitlement.capabilityDisplaySummary(
            com.apprise.music.entitlement.FeatureCapability.PREMIUM_THEMES
        )
        com.apprise.music.entitlement.ProUpgradeDialog(
            featureName = title,
            description = desc,
            onDismiss = { showThemeUpgradeDialog = false },
            onUpgrade = { navController.navigate("premium") },
        )
    }

    val handleColorSelection: (Color) -> Unit = { color ->
        if (!themesUnlocked && color != DefaultThemeColor) {
            showThemeUpgradeDialog = true
        } else {
            onSelectedThemeColorChange(color.toArgb())
            // The default color follows the system's dynamic palette.
            val isDynamicColor = color == DefaultThemeColor
            onDynamicThemeChange(isDynamicColor)
        }
    }

    // Reset theme colors without deleting the selected home background image.
    val onReset: () -> Unit = {
        onDarkModeChange(DarkMode.AUTO)
        onPureBlackChangeRaw(false)
        onPureBlackMiniPlayerChange(false)
        onSelectedThemeColorChange(DefaultThemeColor.toArgb())
        onDynamicThemeChange(true)
    }

    val onSelectThemeMode: (DarkMode, Boolean) -> Unit = { mode, targetPureBlack ->
        if (targetPureBlack && !themesUnlocked) {
            showThemeUpgradeDialog = true
        } else {
            onDarkModeChange(mode)
            onPureBlackChangeRaw(targetPureBlack)
            onPureBlackMiniPlayerChange(targetPureBlack)
        }
    }

    val gatedFontClick: () -> Unit = {
        if (themesUnlocked) onFontClick() else showThemeUpgradeDialog = true
    }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.theme_colors),
        pageColor = AppTheme.colorScheme.background,
    ) {
        if (!themesUnlocked) {
            val (title, desc) = com.apprise.music.entitlement.capabilityDisplaySummary(
                com.apprise.music.entitlement.FeatureCapability.PREMIUM_THEMES
            )
            com.apprise.music.entitlement.ProLockedBanner(
                title = title,
                description = desc,
                onUpgradeClick = { navController.navigate("premium") },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (isLandscape) {
            LandscapeThemeLayout(
                darkMode = darkMode,
                onSelectThemeMode = onSelectThemeMode,
                pureBlack = pureBlack,
                themesUnlocked = themesUnlocked,
                selectedThemeColor = selectedThemeColor,
                onSelectedThemeColorChange = handleColorSelection,
                onReset = onReset,
                fontLabel = fontLabel,
                onFontClick = gatedFontClick,
                onUpgradeClick = { navController.navigate("premium") },
            )
        } else {
            PortraitThemeLayout(
                darkMode = darkMode,
                onSelectThemeMode = onSelectThemeMode,
                pureBlack = pureBlack,
                themesUnlocked = themesUnlocked,
                selectedThemeColor = selectedThemeColor,
                onSelectedThemeColorChange = handleColorSelection,
                onReset = onReset,
                fontLabel = fontLabel,
                onFontClick = gatedFontClick,
                onUpgradeClick = { navController.navigate("premium") },
            )
        }
    }
}

@Composable
fun PortraitThemeLayout(
    darkMode: DarkMode,
    onSelectThemeMode: (DarkMode, Boolean) -> Unit,
    pureBlack: Boolean,
    themesUnlocked: Boolean,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit,
    onReset: () -> Unit,
    fontLabel: String,
    onFontClick: () -> Unit,
    onUpgradeClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .width(120.dp)
                .height(240.dp),
            contentAlignment = Alignment.Center
        ) {
            ThemeMockupPortrait(
                darkMode = darkMode,
                pureBlack = pureBlack,
                themeColor = selectedThemeColor
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ThemeControls(
            darkMode = darkMode,
            onSelectThemeMode = onSelectThemeMode,
            pureBlack = pureBlack,
            themesUnlocked = themesUnlocked,
            onReset = onReset,
            selectedThemeColor = selectedThemeColor,
            onSelectedThemeColorChange = onSelectedThemeColorChange,
        )

        Spacer(modifier = Modifier.height(16.dp))

        FontQuickLink(fontLabel = fontLabel, onClick = onFontClick)

        Spacer(modifier = Modifier.height(16.dp))

        HomeBackgroundControls(onUpgradeClick = onUpgradeClick)


        Spacer(modifier = Modifier.height(120.dp))
    }
}

/** Opens the app font picker from Theme settings. */
@Composable
private fun FontQuickLink(
    fontLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(0.9f),
) {
    Surface(
        onClick = onClick,
        shape = AppTheme.shapes.large,
        color = AppTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_font), style = AppTheme.typography.bodyLarge)
            Text(
                fontLabel,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LandscapeThemeLayout(
    darkMode: DarkMode,
    onSelectThemeMode: (DarkMode, Boolean) -> Unit,
    pureBlack: Boolean,
    themesUnlocked: Boolean,
    selectedThemeColor: Color,
    onSelectedThemeColorChange: (Color) -> Unit,
    onReset: () -> Unit,
    fontLabel: String,
    onFontClick: () -> Unit,
    onUpgradeClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.4f)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .heightIn(max = 300.dp),
                contentAlignment = Alignment.Center
            ) {
                ThemeMockup(
                    darkMode = darkMode,
                    pureBlack = pureBlack,
                    themeColor = selectedThemeColor
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(0.6f)
                .padding(end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            ThemeControls(
                darkMode = darkMode,
                onSelectThemeMode = onSelectThemeMode,
                pureBlack = pureBlack,
                themesUnlocked = themesUnlocked,
                onReset = onReset,
                selectedThemeColor = selectedThemeColor,
                onSelectedThemeColorChange = onSelectedThemeColorChange,
            )

            Spacer(modifier = Modifier.height(16.dp))

            FontQuickLink(
                fontLabel = fontLabel,
                onClick = onFontClick,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            HomeBackgroundControls(onUpgradeClick = onUpgradeClick)


            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun ThemeControls(
    darkMode: DarkMode,
    onSelectThemeMode: (DarkMode, Boolean) -> Unit,
    pureBlack: Boolean,
    themesUnlocked: Boolean = true,
    onReset: () -> Unit,
    selectedThemeColor: Color = DefaultThemeColor,
    onSelectedThemeColorChange: (Color) -> Unit = {},
) {
    var showAccentPicker by remember { mutableStateOf(false) }
    if (showAccentPicker) {
        ColorPickerDialog(
            initialColor = selectedThemeColor,
            title = stringResource(R.string.theme_colors),
            onDismiss = { showAccentPicker = false },
            onConfirm = { onSelectedThemeColorChange(it); showAccentPicker = false },
            onReset = { onSelectedThemeColorChange(DefaultThemeColor); showAccentPicker = false },
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .bounceClick(onClick = { showAccentPicker = true }),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.theme_colors),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(selectedThemeColor)
                        .border(1.dp, AppTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.theme_mode),
                    style = AppTheme.typography.titleMedium,
                    color = AppTheme.colorScheme.onSurface
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    // System mode (AUTO)
                    ModeCircle(
                        label = "System",
                        isSelected = darkMode == DarkMode.AUTO,
                        targetMode = DarkMode.AUTO,
                        targetPureBlack = false,
                        onClick = { onSelectThemeMode(DarkMode.AUTO, pureBlack) },
                        icon = SfSymbols.Monochrome.SFMoonphaseFirstQuarterInverse
                    )

                    // Light mode
                    ModeCircle(
                        label = "Light",
                        isSelected = darkMode == DarkMode.OFF,
                        targetMode = DarkMode.OFF,
                        targetPureBlack = false,
                        onClick = { onSelectThemeMode(DarkMode.OFF, false) },
                        icon = SfSymbols.Monochrome.SFSunMinFill
                    )

                    // Dark mode (#121212)
                    ModeCircle(
                        label = "Dark",
                        isSelected = darkMode == DarkMode.ON && !pureBlack,
                        targetMode = DarkMode.ON,
                        targetPureBlack = false,
                        onClick = { onSelectThemeMode(DarkMode.ON, false) },
                        icon = SfSymbols.Monochrome.SFMoonFill
                    )

                    // AMOLED Pure Black mode (#000000)
                    ModeCircle(
                        label = if (themesUnlocked) "AMOLED" else "AMOLED • Pro",
                        isSelected = darkMode == DarkMode.ON && pureBlack,
                        targetMode = DarkMode.ON,
                        targetPureBlack = true,
                        onClick = { onSelectThemeMode(DarkMode.ON, true) },
                        icon = SfSymbols.Monochrome.SFMoonFill
                    )
                }
            }

            TextButton(
                onClick = onReset,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.reset))
            }
        }
    }
}

@Composable
fun ModeCircle(
    label: String,
    isSelected: Boolean,
    targetMode: DarkMode,
    targetPureBlack: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    
    val effectiveDark = when (targetMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }
    
    val fillColor = when {
        targetPureBlack -> Color.Black
        effectiveDark -> Color(0xFF1C1C1E)
        else -> Color(0xFFF2F2F7)
    }
    val glyphColor = when {
        targetPureBlack || effectiveDark -> Color.White
        else -> Color(0xFF1C1C1E)
    }
    
    // Animated border width
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 1.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "borderWidth"
    )
    
    // Animated scale for the entire circle
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    
    val contentDesc = when {
        targetPureBlack -> stringResource(R.string.cd_pure_black_mode)
        targetMode == DarkMode.OFF -> stringResource(R.string.cd_light_mode)
        targetMode == DarkMode.ON -> stringResource(R.string.cd_dark_mode)
        else -> stringResource(R.string.cd_system_mode)
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .bounceClick(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                selected = isSelected
                contentDescription = contentDesc
            },
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(fillColor)
                .border(
                    width = borderWidth,
                    color = if (isSelected) AppTheme.colorScheme.primary else AppTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                SymbolIcon(symbol = sf("checkmark"), contentDescription = null, size = 20.dp, tint = glyphColor)
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = glyphColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = label,
            style = AppTheme.typography.labelSmall,
            color = if (isSelected) AppTheme.colorScheme.primary else AppTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun ThemeMockup(
    darkMode: DarkMode,
    pureBlack: Boolean,
    themeColor: Color
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }

    AppriseTheme(
        darkTheme = useDark,
        pureBlack = pureBlack,
        themeColor = themeColor
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(9f / 18f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = AppTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, AppTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(AppTheme.colorScheme.surfaceContainer)
                        .padding(10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(AppTheme.colorScheme.primary, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(AppTheme.colorScheme.secondary, CircleShape)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(AppTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(AppTheme.colorScheme.secondary, RoundedCornerShape(6.dp))
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(AppTheme.colorScheme.tertiary, RoundedCornerShape(6.dp))
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(AppTheme.colorScheme.primaryContainer, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeMockupPortrait(
    darkMode: DarkMode,
    pureBlack: Boolean,
    themeColor: Color
) {
    val isSystemDark = isSystemInDarkTheme()
    val useDark = when (darkMode) {
        DarkMode.AUTO -> isSystemDark
        DarkMode.ON -> true
        DarkMode.OFF -> false
    }

    AppriseTheme(
        darkTheme = useDark,
        pureBlack = pureBlack,
        themeColor = themeColor
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = AppTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, AppTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .background(AppTheme.colorScheme.surfaceContainer)
                        .padding(6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(AppTheme.colorScheme.primary, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(AppTheme.colorScheme.secondary, CircleShape)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(AppTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(AppTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(AppTheme.colorScheme.tertiary, RoundedCornerShape(4.dp))
                        )
                    }
                }

                // FAB Area (20% of height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.2f)
                        .padding(6.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(AppTheme.colorScheme.primaryContainer, CircleShape)
                    )
                }
            }
        }
    }
}
