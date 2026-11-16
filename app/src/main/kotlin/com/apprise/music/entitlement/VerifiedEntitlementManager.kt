package com.apprise.music.entitlement

import android.content.Context
import com.convx.music.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production centralized [EntitlementProvider] consuming Play Billing purchase verifications
 * and signed offline verification envelopes.
 *
 * Security invariants:
 * - Never trusts a locally stored boolean flag.
 * - Fails closed for Pro unlocks if verification fails or cache is tampered.
 * - Keeps [FeatureCapability.LOCAL_PLAYBACK] unlocked across all billing/network states.
 */
@Singleton
class VerifiedEntitlementManager @Inject constructor(
    @ApplicationContext context: Context,
    private val cache: VerifiedEntitlementCache,
) : EntitlementProvider {

    private val mutex = Mutex()
    private val verifier: PurchaseVerifier = PlayPurchaseVerifier(
        expectedPackageName = context.packageName,
        rsaPublicKeyBase64 = BuildConfig.PLAY_LICENSE_PUBLIC_KEY,
    )

    private val _entitlements = MutableStateFlow(initialSnapshot())
    override val entitlements: StateFlow<EntitlementSnapshot> = _entitlements.asStateFlow()

    private fun initialSnapshot(): EntitlementSnapshot {
        val loaded = cache.read()
        return EntitlementPolicy.resolveFromOutcomes(
            outcomes = emptyList(),
            billingAvailable = false,
            cachedRecord = loaded.record,
            cacheTampered = loaded.tampered,
            previousExpiredProductId = loaded.lastExpiredProductId,
            nowMillis = System.currentTimeMillis(),
        )
    }

    suspend fun reconcilePlaySession(
        purchases: List<RawPurchaseRecord>,
        billingAvailable: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): EntitlementSnapshot = mutex.withLock {
        val outcomes = purchases.map { verifier.verify(it, nowMillis) }
        val loadedCache = cache.read()

        val previousActiveSubId = loadedCache.record?.productId
            ?.takeIf { AppriseProducts.isSubscription(it) }
            ?: loadedCache.lastExpiredProductId

        val snapshot = EntitlementPolicy.resolveFromOutcomes(
            outcomes = outcomes,
            billingAvailable = billingAvailable,
            cachedRecord = loadedCache.record,
            cacheTampered = loadedCache.tampered,
            previousExpiredProductId = previousActiveSubId,
            nowMillis = nowMillis,
        )

        when (snapshot.tier) {
            EntitlementTier.PRO -> {
                val primary = outcomes.filterIsInstance<PurchaseVerificationOutcome.Verified>()
                    .firstOrNull { it.record.productId == snapshot.activeProductId }?.record
                if (primary != null) {
                    cache.writeVerified(primary)
                }
            }
            EntitlementTier.EXPIRED -> {
                val expiredId = snapshot.activeProductId
                if (expiredId != null) {
                    cache.writeExpiredMarker(expiredId, nowMillis)
                } else {
                    cache.clear()
                }
            }
            EntitlementTier.FREE,
            EntitlementTier.PENDING -> {
                // Play session confirmed no active Pro entitlement (e.g., refunded, revoked, cancelled).
                if (billingAvailable) {
                    cache.clear()
                }
            }
            EntitlementTier.UNKNOWN -> {
                if (loadedCache.tampered) {
                    cache.clear()
                }
            }
        }

        _entitlements.value = snapshot
        snapshot
    }

    suspend fun markBillingUnavailable(nowMillis: Long = System.currentTimeMillis()): EntitlementSnapshot =
        mutex.withLock {
            val loadedCache = cache.read()
            val snapshot = EntitlementPolicy.resolveFromOutcomes(
                outcomes = emptyList(),
                billingAvailable = false,
                cachedRecord = loadedCache.record,
                cacheTampered = loadedCache.tampered,
                previousExpiredProductId = loadedCache.lastExpiredProductId,
                nowMillis = nowMillis,
            )
            if (loadedCache.tampered) {
                cache.clear()
            }
            _entitlements.value = snapshot
            snapshot
        }
}

