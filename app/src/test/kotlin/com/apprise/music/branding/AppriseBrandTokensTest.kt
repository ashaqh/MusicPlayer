package com.apprise.music.branding

import com.convx.music.constants.AppFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppriseBrandTokensTest {

    @Test
    fun `brand identity constants match Apprise Music product definition`() {
        assertEquals("Apprise Music", AppriseBrandTokens.APP_NAME)
        assertEquals("Apprise Apps", AppriseBrandTokens.DEVELOPER_NAME)
        assertEquals("com.apprise.music", AppriseBrandTokens.APPLICATION_ID)
        assertEquals("APPRISE MUSIC", AppriseBrandTokens.WORDMARK)
        assertEquals("apprise", AppriseBrandTokens.DEEP_LINK_SCHEME)
    }

    @Test
    fun `brand surface and accent colors are opaque`() {
        val tokens = listOf(
            AppriseBrandTokens.AccentPrimary,
            AppriseBrandTokens.AccentOnDark,
            AppriseBrandTokens.AccentOnLight,
            AppriseBrandTokens.SplashBackground,
            AppriseBrandTokens.LauncherBackground,
            AppriseBrandTokens.DarkBackground,
            AppriseBrandTokens.DarkSurfaceElevated,
            AppriseBrandTokens.DarkCard,
            AppriseBrandTokens.LightBackground,
            AppriseBrandTokens.LightSurfaceSecondary,
        )
        tokens.forEach { color ->
            assertEquals(1.0f, color.alpha, 0.0001f)
        }
    }

    @Test
    fun `typography roles exclude proprietary Apple and Google binary references`() {
        val references = AppriseBrandTokens.TypographyRole.entries.map { it.assetReference.lowercase() }
        assertFalse(references.any { "sf_pro" in it || "google_sans_flex" in it })
        assertTrue(references.any { "plus_jakarta_sans" in it })
        assertTrue(references.any { "sans_flex" in it })
    }

    @Test
    fun `legacy font preference values resolve safely`() {
        assertEquals(AppFont.GOOGLE_SANS, AppFont.fromValue("google_sans"))
        assertEquals(AppFont.PLUS_JAKARTA_SANS, AppFont.fromValue("plus_jakarta_sans"))
        assertEquals(AppFont.SYSTEM, AppFont.fromValue("unknown_font"))
    }
}

