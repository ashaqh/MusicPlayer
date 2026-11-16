package com.convx.music.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsProviderRegistryTest {
    @Test
    fun upgradingAnOrderPreservesPriorityAndDropsRemovedProviders() {
        val order = LyricsProviderRegistry.completeProviderOrder("Kugou,LrcLib,missing,YouTubeMusic,LrcLib")
        assertEquals(listOf("LrcLib"), order)
        assertEquals(LyricsProviderRegistry.providerIds.toSet(), order.toSet())
    }

    @Test
    fun lrclibDeclaresStableIdAndOptionalMetadata() {
        assertEquals("LrcLib", LrcLibLyricsProvider.id)
        assertEquals("LRCLIB", LrcLibLyricsProvider.displayName)
    }

    @Test
    fun storedDisplayNamesResolveToProviderIds() {
        assertSame(LrcLibLyricsProvider, LyricsProviderRegistry.getProviderForStoredName("LRCLIB"))
        assertSame(LrcLibLyricsProvider, LyricsProviderRegistry.getProviderForStoredName("LrcLib"))
        assertEquals("LRCLIB", LyricsProviderRegistry.getDisplayName("LrcLib"))
    }

    @Test
    fun providerOrderKeepsKnownIdsAndDropsUnknownValues() {
        val order = LyricsProviderRegistry.deserializeProviderOrder("Kugou,LrcLib,YouTubeMusic")

        assertEquals(listOf("LrcLib"), order)
        assertEquals("LrcLib", LyricsProviderRegistry.serializeProviderOrder(order))
    }
}
