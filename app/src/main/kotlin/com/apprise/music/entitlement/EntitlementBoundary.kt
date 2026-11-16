package com.apprise.music.entitlement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Centralized entitlement lifecycle states for hybrid Free + Pro monetization. */
enum class EntitlementTier {
    FREE,
    PRO,
    PENDING,
    EXPIRED,
    UNKNOWN,
}

/** Source explaining how the current [EntitlementSnapshot] was resolved. */
enum class EntitlementVerificationSource {
    NONE,
    PLAY_BILLING_SESSION,
    PLAY_RSA_SIGNATURE,
    BACKEND_VERIFIED,
    CACHED_VERIFIED_OFFLINE,
    TAMPERED_OR_INVALID,
    BILLING_UNAVAILABLE,
}

/** Named product capabilities evaluated against [EntitlementProvider]. */
enum class FeatureCapability {
    // Always available offline in every billing state.
    LOCAL_PLAYBACK,
    NETWORK_STORAGE,

    // Commercial Pro capabilities (Task 3).
    AD_FREE,
    ADVANCED_EQUALIZER,
    PREMIUM_THEMES,
    VISUALIZER,
    CROSSFADE,
    ADVANCED_CUSTOMIZATION,

    // M1 legacy capability aliases kept for source & test compatibility.
    PARAMETRIC_EQ,
    AUTOMIX_DSP,
    THEMING_PRESETS,
}

/** Immutable snapshot of verified user entitlements. */
data class EntitlementSnapshot(
    val tier: EntitlementTier = EntitlementTier.FREE,
    val unlockedCapabilities: Set<FeatureCapability> = DEFAULT_FREE_CAPABILITIES,
    val activeProductId: String? = null,
    val isAcknowledged: Boolean = false,
    val expiryTimeMillis: Long? = null,
    val verificationSource: EntitlementVerificationSource = EntitlementVerificationSource.NONE,
    val statusMessage: String? = null,
) {
    val isPro: Boolean
        get() = tier == EntitlementTier.PRO

    companion object {
        val DEFAULT_FREE_CAPABILITIES: Set<FeatureCapability> = setOf(
            FeatureCapability.LOCAL_PLAYBACK,
            FeatureCapability.NETWORK_STORAGE,
        )

        val PRO_CAPABILITIES: Set<FeatureCapability> = FeatureCapability.entries.toSet()
    }
}

/**
 * Centralized boundary for feature gating and verified entitlement evaluation.
 *
 * Consumers never trust raw booleans in preference storage; they observe [entitlements]
 * or check [isEnabled]. Core local playback remains enabled across every billing state.
 */
interface EntitlementProvider {
    val entitlements: StateFlow<EntitlementSnapshot>

    fun isEnabled(capability: FeatureCapability): Boolean =
        capability in entitlements.value.unlockedCapabilities

    val isPro: Boolean
        get() = entitlements.value.isPro
}

/** M1 baseline placeholder preserved for isolated boundary tests. */
@Singleton
class DefaultFreeEntitlementProvider @Inject constructor() : EntitlementProvider {
    private val _entitlements = MutableStateFlow(
        EntitlementSnapshot(
            tier = EntitlementTier.FREE,
            unlockedCapabilities = setOf(
                FeatureCapability.LOCAL_PLAYBACK,
                FeatureCapability.PARAMETRIC_EQ,
                FeatureCapability.AUTOMIX_DSP,
                FeatureCapability.NETWORK_STORAGE,
                FeatureCapability.THEMING_PRESETS,
            ),
        )
    )
    override val entitlements: StateFlow<EntitlementSnapshot> = _entitlements.asStateFlow()
}
