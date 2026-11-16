package com.apprise.music

import com.apprise.music.ads.AdLoadState
import com.apprise.music.ads.AdSlot
import com.apprise.music.ads.NoOpAdPlacementBoundary
import com.apprise.music.billing.BillingAvailability
import com.apprise.music.billing.NoOpBillingClientBoundary
import com.apprise.music.billing.PurchaseState
import com.apprise.music.consent.ConsentScope
import com.apprise.music.consent.ConsentStatus
import com.apprise.music.consent.NoOpConsentManagerBoundary
import com.apprise.music.entitlement.DefaultFreeEntitlementProvider
import com.apprise.music.entitlement.EntitlementTier
import com.apprise.music.entitlement.FeatureCapability
import com.apprise.music.playback.PlaybackBoundaryStateStore
import com.apprise.music.playback.PlaybackSnapshot
import com.apprise.music.playback.PlaybackSourceKind
import com.apprise.music.playback.PlaybackTransportState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationBoundariesTest {

    @Test
    fun `billing placeholder stays unconfigured in M1`() = runBlocking {
        val billing = NoOpBillingClientBoundary()
        assertEquals(BillingAvailability.UNCONFIGURED, billing.availability.value)
        assertTrue(billing.queryAvailableProducts().isEmpty())
        assertEquals(PurchaseState.NONE, billing.restorePurchases())
    }

    @Test
    fun `ads placeholder disables all slots in M1`() {
        val ads = NoOpAdPlacementBoundary()
        assertFalse(ads.canShowAds())
        AdSlot.entries.forEach { slot ->
            assertEquals(AdLoadState.DISABLED, ads.observeSlotState(slot).value)
        }
    }

    @Test
    fun `entitlement placeholder keeps existing features unlocked in M1`() {
        val provider = DefaultFreeEntitlementProvider()
        assertEquals(EntitlementTier.FREE, provider.entitlements.value.tier)
        assertTrue(provider.isEnabled(FeatureCapability.LOCAL_PLAYBACK))
        assertTrue(provider.isEnabled(FeatureCapability.PARAMETRIC_EQ))
        assertTrue(provider.isEnabled(FeatureCapability.AUTOMIX_DSP))
        assertTrue(provider.isEnabled(FeatureCapability.NETWORK_STORAGE))
        assertTrue(provider.isEnabled(FeatureCapability.THEMING_PRESETS))
        assertFalse(provider.isEnabled(FeatureCapability.AD_FREE))
    }

    @Test
    fun `consent placeholder defaults to privacy-only in M1`() {
        val consent = NoOpConsentManagerBoundary()
        assertEquals(ConsentStatus.NOT_REQUIRED, consent.status.value)
        assertEquals(ConsentScope.PRIVACY_ONLY, consent.activeScope.value)
    }

    @Test
    fun `playback boundary store tracks local playback state updates`() {
        val store = PlaybackBoundaryStateStore()
        assertEquals(PlaybackTransportState.IDLE, store.snapshot.value.transportState)
        assertEquals(PlaybackSourceKind.NONE, store.snapshot.value.sourceKind)

        val playing = PlaybackSnapshot(
            activeTrackId = "local_1",
            title = "Test Track",
            artist = "Test Artist",
            sourceKind = PlaybackSourceKind.LOCAL_DEVICE,
            transportState = PlaybackTransportState.PLAYING,
            positionMs = 12_000L,
            durationMs = 180_000L,
        )
        store.updateSnapshot(playing)
        assertEquals(playing, store.snapshot.value)
    }
}

