package com.apprise.music.ads

import com.apprise.music.consent.ConsentScope
import com.apprise.music.entitlement.EntitlementTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Logical ad placement slots configured cleanly in an independent package. */
enum class AdSlot {
    HOME_BANNER,
    LIBRARY_BANNER,
    PLAYLIST_BANNER,
    /** Legacy M1 slot identifier kept disabled so audio playback is never interrupted by interstitials. */
    INTERSTITIAL_TRANSITION,
}

/** Lifecycle state for an ad placement slot. */
enum class AdLoadState {
    DISABLED,
    IDLE,
    LOADING,
    READY,
    FAILED,
}

sealed interface AdRequestDecision {
    data class Allow(
        val adUnitId: String,
        val nonPersonalizedOnly: Boolean,
    ) : AdRequestDecision

    data class Block(val reason: String) : AdRequestDecision
}

/**
 * Pure policy engine enforcing all M4 ad safety, consent, route exclusion, offline handling,
 * and request-throttling rules without Android framework dependencies.
 */
object AdRequestPolicy {
    const val MIN_RELOAD_INTERVAL_MS = 60_000L

    private val prohibitedRoutePrefixes = listOf(
        "player",
        "equalizer",
        "settings/equalizer",
        "premium",
        "onboarding",
        "welcome",
        "sleep_timer",
        "ambient_mode",
    )

    fun isRouteProhibited(route: String?): Boolean {
        if (route.isNullOrBlank()) return false
        return prohibitedRoutePrefixes.any { prefix ->
            route == prefix || route.startsWith("$prefix/") || route.startsWith("$prefix?")
        }
    }

    fun canInitializeMobileAdsSdk(
        tier: EntitlementTier,
        canRequestAds: Boolean,
        anyUnitConfigured: Boolean,
    ): Boolean = tier != EntitlementTier.PRO && canRequestAds && anyUnitConfigured

    fun evaluateRequest(
        slot: AdSlot,
        tier: EntitlementTier,
        canRequestAds: Boolean,
        consentScope: ConsentScope,
        isNetworkConnected: Boolean,
        adUnitId: String,
        currentRoute: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        lastAttemptMillis: Long = 0L,
    ): AdRequestDecision {
        if (slot == AdSlot.INTERSTITIAL_TRANSITION) {
            return AdRequestDecision.Block("Interstitial ads disabled to protect continuous audio playback")
        }
        if (tier == EntitlementTier.PRO) {
            return AdRequestDecision.Block("Ad-free Pro entitlement active")
        }
        if (isRouteProhibited(currentRoute)) {
            return AdRequestDecision.Block("Ads prohibited on route: $currentRoute")
        }
        if (!canRequestAds) {
            return AdRequestDecision.Block("Consent not yet obtained or disallowed")
        }
        if (consentScope == ConsentScope.PRIVACY_ONLY) {
            return AdRequestDecision.Block("Privacy-only consent scope blocks ad request")
        }
        if (adUnitId.isBlank()) {
            return AdRequestDecision.Block("Ad unit ID not configured for slot $slot")
        }
        if (!isNetworkConnected) {
            return AdRequestDecision.Block("Device offline; skipping ad request gracefully")
        }
        if (lastAttemptMillis > 0L && nowMillis - lastAttemptMillis < MIN_RELOAD_INTERVAL_MS) {
            return AdRequestDecision.Block("Throttled duplicate ad request within cooldown")
        }
        return AdRequestDecision.Allow(
            adUnitId = adUnitId,
            nonPersonalizedOnly = consentScope != ConsentScope.PERSONALIZED_ADS,
        )
    }
}

/** Clean boundary for Mobile Ads SDK initialization, consent-aware requests, and slot state. */
interface AdPlacementBoundary {
    fun observeSlotState(slot: AdSlot): StateFlow<AdLoadState>

    fun canShowAds(): Boolean

    fun evaluateSlot(slot: AdSlot, currentRoute: String? = null): AdRequestDecision =
        AdRequestDecision.Block("No-op ads disabled")

    fun markSlotState(slot: AdSlot, state: AdLoadState, loadedAtMillis: Long = System.currentTimeMillis()) = Unit

    fun ensureSdkInitializedIfAllowed() = Unit
}

/** M1 no-op placeholder preserved for isolated boundary tests. */
@Singleton
class NoOpAdPlacementBoundary @Inject constructor() : AdPlacementBoundary {
    private val disabledState = MutableStateFlow(AdLoadState.DISABLED)

    override fun observeSlotState(slot: AdSlot): StateFlow<AdLoadState> = disabledState.asStateFlow()

    override fun canShowAds(): Boolean = false
}
