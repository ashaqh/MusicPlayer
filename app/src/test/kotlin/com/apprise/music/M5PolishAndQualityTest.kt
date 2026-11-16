package com.apprise.music

import androidx.compose.ui.graphics.Color
import androidx.media3.common.PlaybackException
import com.convx.music.playback.PlaybackErrorClassifier
import com.convx.music.playback.PlaybackErrorType
import com.convx.music.ui.ios.foundation.darkIosColors
import com.convx.music.ui.ios.foundation.lightIosColors
import com.convx.music.ui.ios.foundation.meetsContrast
import com.convx.music.ui.theme.AppleTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

class M5PolishAndQualityTest {

    @Test
    fun `missing local file classifies as LocalFileUnavailable even when offline`() {
        val error = PlaybackException(
            "File not found",
            FileNotFoundException("/storage/emulated/0/Music/deleted.mp3"),
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        )
        assertEquals(
            PlaybackErrorType.LocalFileUnavailable,
            PlaybackErrorClassifier.classify(error, isNetworkConnected = false),
        )
    }

    @Test
    fun `permission error classifies as LocalFileUnavailable even when offline`() {
        val error = PlaybackException(
            "No permission",
            null,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        )
        assertEquals(
            PlaybackErrorType.LocalFileUnavailable,
            PlaybackErrorClassifier.classify(error, isNetworkConnected = false),
        )
    }

    @Test
    fun `corrupt audio decoding failure classifies as CorruptOrUnsupportedMedia when offline`() {
        val error = PlaybackException(
            "Decoder failed",
            null,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
        )
        assertEquals(
            PlaybackErrorType.CorruptOrUnsupportedMedia,
            PlaybackErrorClassifier.classify(error, isNetworkConnected = false),
        )
    }

    @Test
    fun `dark and amoled themes have distinct surface tokens`() {
        val light = lightIosColors()
        val dark = darkIosColors(pureBlack = false)
        val amoled = darkIosColors(pureBlack = true)

        assertEquals(Color(0xFFFFFFFF), light.systemBackground)
        assertEquals(Color(0xFF121212), dark.systemBackground)
        assertEquals(Color(0xFF000000), amoled.systemBackground)
        assertEquals(Color(0xFF000000), amoled.systemGroupedBackground)
        assertNotEquals(dark.systemBackground, amoled.systemBackground)
        assertNotEquals(light.systemBackground, dark.systemBackground)
    }

    @Test
    fun `adaptive hero contrast passes WCAG 4_5 ratio across light dark and amoled surfaces`() {
        val surfaces = listOf(
            Color(0xFF000000),
            Color(0xFF121212),
            Color(0xFFFFFFFF),
            Color(0xFFFA2D48),
            Color(0xFFFFD60A),
            Color(0xFF1B4332),
        )
        surfaces.forEach { bg ->
            val primaryFg = AppleTokens.onColor(bg)
            val secondaryFg = AppleTokens.onColorSecondary(bg)
            assertTrue(
                "Primary text failed 4.5:1 on $bg (ratio=${AppleTokens.contrastRatio(primaryFg, bg)})",
                meetsContrast(primaryFg, bg, 4.5f),
            )
            assertTrue(
                "Secondary text failed 4.5:1 on $bg (ratio=${AppleTokens.contrastRatio(secondaryFg, bg)})",
                meetsContrast(secondaryFg, bg, 4.5f),
            )
        }
    }
}
