package com.apprise.music.branding

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for Apprise Music product identity and visual branding tokens.
 *
 * Palette colors remain aligned with the existing dark/light iOS-inspired surface tokens in M1
 * while isolating brand ownership from legacy Convx references. Visual assets and colors marked
 * as temporary placeholders can be swapped here without refactoring UI screens.
 */
object AppriseBrandTokens {
    const val APP_NAME = "Apprise Music"
    const val DEVELOPER_NAME = "Apprise Apps"
    const val APPLICATION_ID = "com.apprise.music"
    const val WORDMARK = "APPRISE MUSIC"
    const val DEEP_LINK_SCHEME = "apprise"

    // Brand accent palette (M1 foundation placeholder aligned with DefaultThemeColor)
    val AccentPrimary = Color(0xFFFA2D48)
    val AccentOnDark = Color(0xFFFF4D64)
    val AccentOnLight = Color(0xFFD91E36)

    // Core surface and splash/launcher foundation colors
    val SplashBackground = Color(0xFF0A0B0F)
    val LauncherBackground = Color(0xFF14161D)
    val DarkBackground = Color(0xFF121212)
    val DarkSurfaceElevated = Color(0xFF1A1A1A)
    val DarkCard = Color(0xFF1C1C1E)
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurfaceSecondary = Color(0xFFF2F2F7)

    /** Supported open-licensed typeface roles for the Apprise Music brand identity. */
    enum class TypographyRole(val displayName: String, val assetReference: String) {
        BODY("Plus Jakarta Sans", "res/font/plus_jakarta_sans.ttf (SIL OFL 1.1)"),
        FLEX("Sans Flex", "res/font/sans_flex.ttf (SIL OFL 1.1)"),
        GEOMETRIC("Outfit", "res/font/outfit.ttf (SIL OFL 1.1)"),
        WORDMARK("Chillax", "res/font/chillax_*.otf (Fontshare Free Commercial)"),
        SYSTEM("System Default", "Platform FontFamily.Default"),
    }
}

