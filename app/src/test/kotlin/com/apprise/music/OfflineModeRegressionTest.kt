package com.apprise.music

import com.convx.music.constants.EnableLrcLibKey
import com.convx.music.constants.SearchSource
import com.convx.music.lyrics.LyricsProviderRegistry
import com.convx.music.lyrics.LyricsProviderSettings
import com.convx.music.ui.screens.Screens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies M2 offline-only guarantees across navigation, lyrics providers, search sources,
 * and compiled classpath isolation from removed streaming/scraping engines.
 */
class OfflineModeRegressionTest {

    @Test
    fun `main navigation exposes strictly offline tabs`() {
        val expectedRoutes = listOf("home", "songs", "search_input", "library")
        assertEquals(expectedRoutes, Screens.MainScreens.map { it.route })
        assertEquals(expectedRoutes, Screens.mainScreens(localOnly = true).map { it.route })
        assertEquals(expectedRoutes, Screens.mainScreens(localOnly = false).map { it.route })
        assertEquals(expectedRoutes.toSet(), Screens.MainRoutes)
    }

    @Test
    fun `search source only permits local library search`() {
        assertEquals(listOf(SearchSource.LOCAL), SearchSource.entries)
    }

    @Test
    fun `lyrics registry only includes opt-in lrclib provider`() {
        assertEquals(listOf("LrcLib"), LyricsProviderRegistry.providerIds)
        assertEquals(listOf("LrcLib"), LyricsProviderRegistry.getDefaultProviderOrder())
        assertEquals(EnableLrcLibKey, LyricsProviderSettings.enabledKey("LrcLib"))

        val removedProviders = listOf(
            "YouTubeSubtitle",
            "YouTubeMusic",
            "Musixmatch",
            "PaxSenix",
            "PaxSenixMusixmatch",
            "PaxSenixSpotify",
            "KuGou",
            "BetterLyrics",
            "BetterLyricsPortato",
            "SimpMusic",
            "YouLyPlus",
            "Unison",
            "Genius",
            "LrcRed",
            "BiniLyrics",
            "Megalobiz",
        )
        removedProviders.forEach { providerId ->
            assertNull(
                "Unauthorized lyrics provider '$providerId' must remain removed",
                LyricsProviderRegistry.getProvider(providerId),
            )
        }
    }

    @Test
    fun `removed streaming and updater classes are absent from compiled classpath`() {
        val removedClassNames = listOf(
            "com.music.innertube.YouTube",
            "com.music.spotify.SpotifyManager",
            "com.music.integrations.jiosaavn.SaavnService",
            "com.music.canvas.CanvasProvider",
            "com.music.spine.SpineModuleManager",
            "com.convx.music.playback.DownloadUtil",
            "com.convx.music.playback.ExoDownloadService",
            "com.convx.music.listentogether.ListenTogetherManager",
            "com.convx.music.utils.DiscordRPC",
            "com.convx.music.utils.Wikipedia",
            "com.convx.music.utils.YTPlayerUtils",
            "com.convx.music.utils.updater.ConvxUpdater",
            "com.convx.music.playback.CastConnectionHandler",
        )
        val loader = javaClass.classLoader!!
        removedClassNames.forEach { className ->
            val loaded = runCatching { Class.forName(className, false, loader) }.getOrNull()
            assertTrue(
                "Class '$className' must not exist in the M2 offline classpath",
                loaded == null,
            )
            assertFalse(loaded != null)
        }
    }
}

