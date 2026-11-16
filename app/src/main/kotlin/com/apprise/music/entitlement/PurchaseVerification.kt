package com.apprise.music.entitlement

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Proposed Google Play Console product identifiers (verify in Play Console prior to release). */
object AppriseProducts {
    const val LIFETIME_PRO = "apprise_music_pro_lifetime"
    const val MONTHLY_PRO = "apprise_music_pro_monthly"
    const val ANNUAL_PRO = "apprise_music_pro_annual"

    val INAPP_IDS: List<String> = listOf(LIFETIME_PRO)
    val SUBS_IDS: List<String> = listOf(MONTHLY_PRO, ANNUAL_PRO)
    val ALL_IDS: Set<String> = setOf(LIFETIME_PRO, MONTHLY_PRO, ANNUAL_PRO)

    fun isSubscription(productId: String): Boolean = productId in SUBS_IDS
    fun isLifetime(productId: String): Boolean = productId == LIFETIME_PRO
}

enum class RawPurchaseState {
    PURCHASED,
    PENDING,
    UNSPECIFIED,
}

/** Structured representation of a Google Play Billing purchase record for verification. */
data class RawPurchaseRecord(
    val productIds: List<String>,
    val purchaseToken: String,
    val orderId: String?,
    val packageName: String,
    val purchaseTimeMillis: Long,
    val purchaseState: RawPurchaseState,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val originalJson: String,
    val signature: String?,
    val expiryTimeMillis: Long? = null,
)

data class VerifiedEntitlementRecord(
    val productId: String,
    val purchaseTokenHash: String,
    val orderId: String?,
    val purchaseTimeMillis: Long,
    val expiryTimeMillis: Long?,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val verifiedAtMillis: Long,
    val verificationSource: EntitlementVerificationSource,
)

sealed interface PurchaseVerificationOutcome {
    data class Verified(val record: VerifiedEntitlementRecord) : PurchaseVerificationOutcome
    data class Pending(val productId: String, val purchaseTokenHash: String) : PurchaseVerificationOutcome
    data class Expired(val productId: String, val expiredAtMillis: Long) : PurchaseVerificationOutcome
    data class Rejected(val reason: String) : PurchaseVerificationOutcome
}

/**
 * Optional backend verification seam for Google Play Developer API (`purchases.products.get` /
 * `purchases.subscriptionsv2.get`). When absent, [PlayPurchaseVerifier] falls back to
 * Play RSA signature verification (if configured) and Play Billing IPC session integrity checks.
 */
interface BackendPurchaseVerifier {
    suspend fun verifyPurchase(raw: RawPurchaseRecord, nowMillis: Long): PurchaseVerificationOutcome
}

interface PurchaseVerifier {
    suspend fun verify(raw: RawPurchaseRecord, nowMillis: Long): PurchaseVerificationOutcome
}

class PlayPurchaseVerifier(
    private val expectedPackageName: String,
    private val rsaPublicKeyBase64: String,
    private val backendVerifier: BackendPurchaseVerifier? = null,
) : PurchaseVerifier {

    override suspend fun verify(raw: RawPurchaseRecord, nowMillis: Long): PurchaseVerificationOutcome {
        val productId = raw.productIds.firstOrNull { it in AppriseProducts.ALL_IDS }
            ?: return PurchaseVerificationOutcome.Rejected("Unknown or missing Apprise product ID")

        if (raw.packageName != expectedPackageName) {
            return PurchaseVerificationOutcome.Rejected("Package mismatch: ${raw.packageName}")
        }
        if (raw.purchaseToken.isBlank() || raw.purchaseToken.length < MIN_TOKEN_LENGTH) {
            return PurchaseVerificationOutcome.Rejected("Invalid purchase token format")
        }
        if (raw.purchaseTimeMillis <= 0L) {
            return PurchaseVerificationOutcome.Rejected("Invalid purchase timestamp")
        }

        val tokenHash = sha256Hex(raw.purchaseToken)

        when (raw.purchaseState) {
            RawPurchaseState.PENDING -> {
                return PurchaseVerificationOutcome.Pending(
                    productId = productId,
                    purchaseTokenHash = tokenHash,
                )
            }
            RawPurchaseState.UNSPECIFIED -> {
                return PurchaseVerificationOutcome.Rejected("Unspecified purchase state")
            }
            RawPurchaseState.PURCHASED -> Unit
        }

        if (AppriseProducts.isSubscription(productId) &&
            raw.expiryTimeMillis != null &&
            raw.expiryTimeMillis <= nowMillis &&
            !raw.isAutoRenewing
        ) {
            return PurchaseVerificationOutcome.Expired(
                productId = productId,
                expiredAtMillis = raw.expiryTimeMillis,
            )
        }

        if (backendVerifier != null) {
            return backendVerifier.verifyPurchase(raw, nowMillis)
        }

        if (rsaPublicKeyBase64.isNotBlank()) {
            val sig = raw.signature?.takeIf { it.isNotBlank() }
                ?: return PurchaseVerificationOutcome.Rejected("Missing Play purchase signature")
            if (!verifyRsaSha1Signature(raw.originalJson, sig, rsaPublicKeyBase64)) {
                return PurchaseVerificationOutcome.Rejected("Play RSA signature verification failed")
            }
            return PurchaseVerificationOutcome.Verified(
                VerifiedEntitlementRecord(
                    productId = productId,
                    purchaseTokenHash = tokenHash,
                    orderId = raw.orderId,
                    purchaseTimeMillis = raw.purchaseTimeMillis,
                    expiryTimeMillis = raw.expiryTimeMillis,
                    isAcknowledged = raw.isAcknowledged,
                    isAutoRenewing = raw.isAutoRenewing,
                    verifiedAtMillis = nowMillis,
                    verificationSource = EntitlementVerificationSource.PLAY_RSA_SIGNATURE,
                )
            )
        }

        // Play Billing IPC session integrity: verify originalJson binds productId cleanly when present.
        if (raw.originalJson.isBlank() || !raw.originalJson.contains(productId)) {
            return PurchaseVerificationOutcome.Rejected("Purchase payload missing expected product binding")
        }

        return PurchaseVerificationOutcome.Verified(
            VerifiedEntitlementRecord(
                productId = productId,
                purchaseTokenHash = tokenHash,
                orderId = raw.orderId,
                purchaseTimeMillis = raw.purchaseTimeMillis,
                expiryTimeMillis = raw.expiryTimeMillis,
                isAcknowledged = raw.isAcknowledged,
                isAutoRenewing = raw.isAutoRenewing,
                verifiedAtMillis = nowMillis,
                verificationSource = EntitlementVerificationSource.PLAY_BILLING_SESSION,
            )
        )
    }

    private fun verifyRsaSha1Signature(
        signedData: String,
        signatureBase64: String,
        publicKeyBase64: String,
    ): Boolean = runCatching {
        val keyBytes = Base64.getDecoder().decode(publicKeyBase64)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)
        val verifier = Signature.getInstance("SHA1withRSA")
        verifier.initVerify(publicKey)
        verifier.update(signedData.toByteArray(Charsets.UTF_8))
        verifier.verify(Base64.getDecoder().decode(signatureBase64))
    }.getOrDefault(false)

    companion object {
        private const val MIN_TOKEN_LENGTH = 16

        fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

/** Pure resolver translating verified purchases, billing connectivity, and offline caches into [EntitlementSnapshot]. */
object EntitlementPolicy {

    /**
     * Maximum window an intact offline HMAC-verified subscription or lifetime cache stays trusted
     * when Google Play Billing cannot connect (e.g. prolonged airplane mode).
     */
    const val OFFLINE_VERIFIED_GRACE_MS = 14L * 24L * 60L * 60L * 1000L

    fun resolveFromOutcomes(
        outcomes: List<PurchaseVerificationOutcome>,
        billingAvailable: Boolean,
        cachedRecord: VerifiedEntitlementRecord?,
        cacheTampered: Boolean,
        previousExpiredProductId: String?,
        nowMillis: Long,
    ): EntitlementSnapshot {
        // 1. Live Play Billing session resolved at least one valid purchase.
        val verifiedRecords = outcomes.filterIsInstance<PurchaseVerificationOutcome.Verified>()
            .map { it.record }
        if (verifiedRecords.isNotEmpty()) {
            // Prefer lifetime over subscriptions if multiple active entitlements exist.
            val primary = verifiedRecords.firstOrNull { AppriseProducts.isLifetime(it.productId) }
                ?: verifiedRecords.maxByOrNull { it.purchaseTimeMillis }!!
            return EntitlementSnapshot(
                tier = EntitlementTier.PRO,
                unlockedCapabilities = EntitlementSnapshot.PRO_CAPABILITIES,
                activeProductId = primary.productId,
                isAcknowledged = primary.isAcknowledged,
                expiryTimeMillis = primary.expiryTimeMillis,
                verificationSource = primary.verificationSource,
                statusMessage = null,
            )
        }

        // 2. Pending purchase awaiting delayed payment confirmation (Pro remains locked).
        val pending = outcomes.filterIsInstance<PurchaseVerificationOutcome.Pending>().firstOrNull()
        if (pending != null) {
            return EntitlementSnapshot(
                tier = EntitlementTier.PENDING,
                unlockedCapabilities = EntitlementSnapshot.DEFAULT_FREE_CAPABILITIES,
                activeProductId = pending.productId,
                isAcknowledged = false,
                verificationSource = EntitlementVerificationSource.PLAY_BILLING_SESSION,
                statusMessage = "Purchase awaiting Google Play confirmation",
            )
        }

        // 3. Explicit expiration detected in purchase verification.
        val expired = outcomes.filterIsInstance<PurchaseVerificationOutcome.Expired>().firstOrNull()
        if (expired != null) {
            return EntitlementSnapshot(
                tier = EntitlementTier.EXPIRED,
                unlockedCapabilities = EntitlementSnapshot.DEFAULT_FREE_CAPABILITIES,
                activeProductId = expired.productId,
                isAcknowledged = false,
                expiryTimeMillis = expired.expiredAtMillis,
                verificationSource = EntitlementVerificationSource.PLAY_BILLING_SESSION,
                statusMessage = "Subscription expired",
            )
        }

        // 4. Billing connected cleanly and reported zero active purchases -> FREE or EXPIRED.
        if (billingAvailable) {
            val rejected = outcomes.filterIsInstance<PurchaseVerificationOutcome.Rejected>().firstOrNull()
            if (rejected != null) {
                return EntitlementSnapshot(
                    tier = EntitlementTier.UNKNOWN,
                    unlockedCapabilities = EntitlementSnapshot.DEFAULT_FREE_CAPABILITIES,
                    verificationSource = EntitlementVerificationSource.TAMPERED_OR_INVALID,
                    statusMessage = rejected.reason,
                )
            }
            if (previousExpiredProductId != null && AppriseProducts.isSubscription(previousExpiredProductId)) {
                return EntitlementSnapshot(
                    tier = EntitlementTier.EXPIRED,
                    unlockedCapabilities = EntitlementSnapshot.DEFAULT_FREE_CAPABILITIES,
                    activeProductId = previousExpiredProductId,
                    verificationSource = EntitlementVerificationSource.PLAY_BILLING_SESSION,
                    statusMessage = "Previous subscription is no longer active",
                )
            }
            return EntitlementSnapshot(
                tier = EntitlementTier.FREE,
                unlockedCapabilities = EntitlementSnapshot.DEFAULT_FREE_CAPABILITIES,
                verificationSource = EntitlementVerificationSource.PLAY_BILLING_SESSION,
                statusMessage = null,
            )
        }

        // 5. Billing offline/unavailable: fail-closed on tamper, check bounded intact verified cache.
        if (cacheTampered) {
            return EntitlementSnapshot(
                tier = EntitlementTier.UNKNOWN,
                unlockedCapabilities = EntitlementSnapshot.DEFAULT_FREE_CAPABILITIES,
                verificationSource = EntitlementVerificationSource.TAMPERED_OR_INVALID,
                statusMessage = "Cached entitlement integrity check failed",
            )
        }

        if (cachedRecord != null) {
            val expiry = cachedRecord.expiryTimeMillis
            if (expiry != null && expiry <= nowMillis && !cachedRecord.isAutoRenewing) {
                return EntitlementSnapshot(
                    tier = EntitlementTier.EXPIRED,
                    unlockedCapabilities = EntitlementSnapshot.DEFAULT_FREE_CAPABILITIES,
                    activeProductId = cachedRecord.productId,
                    expiryTimeMillis = expiry,
                    verificationSource = EntitlementVerificationSource.CACHED_VERIFIED_OFFLINE,
                    statusMessage = "Cached subscription expired offline",
                )
            }
            if (nowMillis - cachedRecord.verifiedAtMillis <= OFFLINE_VERIFIED_GRACE_MS) {
                return EntitlementSnapshot(
                    tier = EntitlementTier.PRO,
                    unlockedCapabilities = EntitlementSnapshot.PRO_CAPABILITIES,
                    activeProductId = cachedRecord.productId,
                    isAcknowledged = cachedRecord.isAcknowledged,
                    expiryTimeMillis = cachedRecord.expiryTimeMillis,
                    verificationSource = EntitlementVerificationSource.CACHED_VERIFIED_OFFLINE,
                    statusMessage = "Offline verified grace active",
                )
            }
        }

        return EntitlementSnapshot(
            tier = EntitlementTier.UNKNOWN,
            unlockedCapabilities = EntitlementSnapshot.DEFAULT_FREE_CAPABILITIES,
            verificationSource = EntitlementVerificationSource.BILLING_UNAVAILABLE,
            statusMessage = "Play Billing unavailable; core offline player active",
        )
    }
}

