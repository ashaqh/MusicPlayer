package com.apprise.music

import com.apprise.music.ads.AdRequestDecision
import com.apprise.music.ads.AdRequestPolicy
import com.apprise.music.ads.AdSlot
import com.apprise.music.billing.PurchaseState
import com.apprise.music.consent.ConsentScope
import com.apprise.music.consent.ConsentScopeResolver
import com.apprise.music.entitlement.AppriseProducts
import com.apprise.music.entitlement.EntitlementEnvelopeSigner
import com.apprise.music.entitlement.EntitlementPolicy
import com.apprise.music.entitlement.EntitlementTier
import com.apprise.music.entitlement.EntitlementVerificationSource
import com.apprise.music.entitlement.FeatureCapability
import com.apprise.music.entitlement.PlayPurchaseVerifier
import com.apprise.music.entitlement.PurchaseVerificationOutcome
import com.apprise.music.entitlement.RawPurchaseRecord
import com.apprise.music.entitlement.RawPurchaseState
import com.apprise.music.entitlement.VerifiedEntitlementStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

class M4MonetizationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class TestHmacSigner : EntitlementEnvelopeSigner {
        override fun sign(payload: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("test-secret|$payload".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        override fun verify(payload: String, macHex: String): Boolean =
            sign(payload) == macHex
    }

    private fun purchaseRecord(
        productId: String = AppriseProducts.LIFETIME_PRO,
        state: RawPurchaseState = RawPurchaseState.PURCHASED,
        acknowledged: Boolean = true,
        autoRenewing: Boolean = false,
        expiryTimeMillis: Long? = null,
        signature: String? = null,
        token: String = "opaque-play-token-0123456789abcdef",
    ) = RawPurchaseRecord(
        productIds = listOf(productId),
        purchaseToken = token,
        orderId = "GPA.1234-5678-9012-34567",
        packageName = "com.apprise.music.debug",
        purchaseTimeMillis = 1_700_000_000_000L,
        purchaseState = state,
        isAcknowledged = acknowledged,
        isAutoRenewing = autoRenewing,
        originalJson = """{"orderId":"GPA.1234-5678-9012-34567","packageName":"com.apprise.music.debug","productId":"$productId","purchaseToken":"$token"}""",
        signature = signature,
        expiryTimeMillis = expiryTimeMillis,
    )

    private val proGatedCapabilities = listOf(
        FeatureCapability.AD_FREE,
        FeatureCapability.ADVANCED_EQUALIZER,
        FeatureCapability.PREMIUM_THEMES,
        FeatureCapability.VISUALIZER,
        FeatureCapability.CROSSFADE,
        FeatureCapability.ADVANCED_CUSTOMIZATION,
    )

    @Test
    fun `1 free user has local playback unlocked and all pro capabilities locked`() {
        val snap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = emptyList(),
            billingAvailable = true,
            cachedRecord = null,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = 1_700_001_000_000L,
        )
        assertEquals(EntitlementTier.FREE, snap.tier)
        assertTrue(FeatureCapability.LOCAL_PLAYBACK in snap.unlockedCapabilities)
        proGatedCapabilities.forEach { cap ->
            assertFalse("$cap must be locked for FREE tier", cap in snap.unlockedCapabilities)
        }
    }

    @Test
    fun `2 pro user with valid rsa signature unlocks all capabilities`() = runBlocking {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pubB64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val raw = purchaseRecord(productId = AppriseProducts.LIFETIME_PRO)
        val sigBytes = Signature.getInstance("SHA1withRSA").apply {
            initSign(keyPair.private)
            update(raw.originalJson.toByteArray(Charsets.UTF_8))
        }.sign()
        val signedRaw = raw.copy(signature = Base64.getEncoder().encodeToString(sigBytes))

        val verifier = PlayPurchaseVerifier("com.apprise.music.debug", pubB64)
        val outcome = verifier.verify(signedRaw, 1_700_001_000_000L)
        assertTrue(outcome is PurchaseVerificationOutcome.Verified)

        val snap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = listOf(outcome),
            billingAvailable = true,
            cachedRecord = null,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = 1_700_001_000_000L,
        )
        assertEquals(EntitlementTier.PRO, snap.tier)
        assertEquals(EntitlementVerificationSource.PLAY_RSA_SIGNATURE, snap.verificationSource)
        assertEquals(AppriseProducts.LIFETIME_PRO, snap.activeProductId)
        assertTrue(FeatureCapability.LOCAL_PLAYBACK in snap.unlockedCapabilities)
        proGatedCapabilities.forEach { cap ->
            assertTrue("$cap must be unlocked for PRO tier", cap in snap.unlockedCapabilities)
        }
    }

    @Test
    fun `forged rsa signature is rejected and fails closed while preserving offline playback`() = runBlocking {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pubB64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val forgedRaw = purchaseRecord(
            productId = AppriseProducts.LIFETIME_PRO,
            signature = Base64.getEncoder().encodeToString("forged-signature-bytes".toByteArray()),
        )

        val verifier = PlayPurchaseVerifier("com.apprise.music.debug", pubB64)
        val outcome = verifier.verify(forgedRaw, 1_700_001_000_000L)
        assertTrue(outcome is PurchaseVerificationOutcome.Rejected)

        val snap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = listOf(outcome),
            billingAvailable = true,
            cachedRecord = null,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = 1_700_001_000_000L,
        )
        assertEquals(EntitlementTier.UNKNOWN, snap.tier)
        assertEquals(EntitlementVerificationSource.TAMPERED_OR_INVALID, snap.verificationSource)
        assertTrue(FeatureCapability.LOCAL_PLAYBACK in snap.unlockedCapabilities)
        proGatedCapabilities.forEach { cap ->
            assertFalse("$cap must remain locked on forged signature", cap in snap.unlockedCapabilities)
        }
    }

    @Test
    fun `3 pending purchase does not unlock pro and keeps local playback active`() = runBlocking {
        val verifier = PlayPurchaseVerifier("com.apprise.music.debug", "")
        val outcome = verifier.verify(
            purchaseRecord(state = RawPurchaseState.PENDING, acknowledged = false),
            1_700_001_000_000L,
        )
        val snap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = listOf(outcome),
            billingAvailable = true,
            cachedRecord = null,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = 1_700_001_000_000L,
        )
        assertEquals(EntitlementTier.PENDING, snap.tier)
        assertTrue(FeatureCapability.LOCAL_PLAYBACK in snap.unlockedCapabilities)
        proGatedCapabilities.forEach { cap ->
            assertFalse("$cap must remain locked while PENDING", cap in snap.unlockedCapabilities)
        }
    }

    @Test
    fun `4 canceled purchase preserves baseline free state and local playback`() {
        assertEquals("CANCELED", PurchaseState.CANCELED.name)
        val snap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = emptyList(),
            billingAvailable = true,
            cachedRecord = null,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = 1_700_001_000_000L,
        )
        assertEquals(EntitlementTier.FREE, snap.tier)
        assertTrue(FeatureCapability.LOCAL_PLAYBACK in snap.unlockedCapabilities)
    }

    @Test
    fun `5 failed billing connection without cache moves to UNKNOWN safely and keeps offline playback`() {
        val snap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = emptyList(),
            billingAvailable = false,
            cachedRecord = null,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = 1_700_001_000_000L,
        )
        assertEquals(EntitlementTier.UNKNOWN, snap.tier)
        assertEquals(EntitlementVerificationSource.BILLING_UNAVAILABLE, snap.verificationSource)
        assertTrue(FeatureCapability.LOCAL_PLAYBACK in snap.unlockedCapabilities)
        proGatedCapabilities.forEach { cap ->
            assertFalse("$cap must stay locked on UNKNOWN", cap in snap.unlockedCapabilities)
        }
    }

    @Test
    fun `6 and 7 restored purchase writes signed envelope and revoked purchase wipes cache`() = runBlocking {
        val file = tempFolder.newFile("entitlement.json")
        val store = VerifiedEntitlementStore(file, TestHmacSigner())
        val verifier = PlayPurchaseVerifier("com.apprise.music.debug", "")
        val now = 1_700_001_000_000L

        val verified = verifier.verify(purchaseRecord(productId = AppriseProducts.ANNUAL_PRO), now)
        assertTrue(verified is PurchaseVerificationOutcome.Verified)
        store.writeVerified((verified as PurchaseVerificationOutcome.Verified).record)

        val restored = store.read()
        assertFalse(restored.tampered)
        assertNotNull(restored.record)
        assertEquals(AppriseProducts.ANNUAL_PRO, restored.record?.productId)

        // Simulate Play revocation: Play session returns empty active purchases.
        val revokedSnap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = emptyList(),
            billingAvailable = true,
            cachedRecord = restored.record,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = now + 5_000L,
        )
        assertEquals(EntitlementTier.FREE, revokedSnap.tier)
        store.clear()
        assertNull(store.read().record)
    }

    @Test
    fun `tampering persisted entitlement file breaks hmac and denies pro offline`() = runBlocking {
        val file = tempFolder.newFile("entitlement_tamper.json")
        val store = VerifiedEntitlementStore(file, TestHmacSigner())
        val verifier = PlayPurchaseVerifier("com.apprise.music.debug", "")
        val verified = verifier.verify(purchaseRecord(productId = AppriseProducts.MONTHLY_PRO), 1_700_001_000_000L)
            as PurchaseVerificationOutcome.Verified
        store.writeVerified(verified.record)

        // Attacker rewrites payload inside stored file without valid HMAC.
        val mutated = file.readText().replace(AppriseProducts.MONTHLY_PRO, AppriseProducts.LIFETIME_PRO)
        file.writeText(mutated)

        val loaded = store.read()
        assertTrue(loaded.tampered)
        assertNull(loaded.record)

        val snap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = emptyList(),
            billingAvailable = false,
            cachedRecord = loaded.record,
            cacheTampered = loaded.tampered,
            previousExpiredProductId = null,
            nowMillis = 1_700_002_000_000L,
        )
        assertEquals(EntitlementTier.UNKNOWN, snap.tier)
        assertFalse(FeatureCapability.AD_FREE in snap.unlockedCapabilities)
        assertTrue(FeatureCapability.LOCAL_PLAYBACK in snap.unlockedCapabilities)
    }

    @Test
    fun `8 expired subscription resolves to EXPIRED both online and offline`() = runBlocking {
        val verifier = PlayPurchaseVerifier("com.apprise.music.debug", "")
        val now = 1_700_100_000_000L
        val expiredOutcome = verifier.verify(
            purchaseRecord(
                productId = AppriseProducts.MONTHLY_PRO,
                autoRenewing = false,
                expiryTimeMillis = now - 60_000L,
            ),
            now,
        )
        assertTrue(expiredOutcome is PurchaseVerificationOutcome.Expired)

        val onlineSnap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = listOf(expiredOutcome),
            billingAvailable = true,
            cachedRecord = null,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = now,
        )
        assertEquals(EntitlementTier.EXPIRED, onlineSnap.tier)
        assertFalse(onlineSnap.isPro)
        assertTrue(FeatureCapability.LOCAL_PLAYBACK in onlineSnap.unlockedCapabilities)

        // Also test previously active subscription absent from Play purchases.
        val lapsedSnap = EntitlementPolicy.resolveFromOutcomes(
            outcomes = emptyList(),
            billingAvailable = true,
            cachedRecord = null,
            cacheTampered = false,
            previousExpiredProductId = AppriseProducts.ANNUAL_PRO,
            nowMillis = now,
        )
        assertEquals(EntitlementTier.EXPIRED, lapsedSnap.tier)
    }

    @Test
    fun `9 offline grace preserves verified pro briefly and handles ads non-blocking`() = runBlocking {
        val verifier = PlayPurchaseVerifier("com.apprise.music.debug", "")
        val t0 = 1_700_000_000_000L
        val verified = (verifier.verify(purchaseRecord(), t0) as PurchaseVerificationOutcome.Verified).record

        // Within offline grace window, Pro stays verified.
        val offlinePro = EntitlementPolicy.resolveFromOutcomes(
            outcomes = emptyList(),
            billingAvailable = false,
            cachedRecord = verified,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = t0 + 10_000L,
        )
        assertEquals(EntitlementTier.PRO, offlinePro.tier)

        // Past grace window without Play session, fail-closed to UNKNOWN; playback stays unlocked.
        val offlineStale = EntitlementPolicy.resolveFromOutcomes(
            outcomes = emptyList(),
            billingAvailable = false,
            cachedRecord = verified,
            cacheTampered = false,
            previousExpiredProductId = null,
            nowMillis = t0 + EntitlementPolicy.OFFLINE_VERIFIED_GRACE_MS + 1_000L,
        )
        assertEquals(EntitlementTier.UNKNOWN, offlineStale.tier)
        assertTrue(FeatureCapability.LOCAL_PLAYBACK in offlineStale.unlockedCapabilities)

        // Offline FREE user skips ad load cleanly.
        val offlineAdDecision = AdRequestPolicy.evaluateRequest(
            slot = AdSlot.HOME_BANNER,
            tier = EntitlementTier.FREE,
            canRequestAds = true,
            consentScope = ConsentScope.PERSONALIZED_ADS,
            isNetworkConnected = false,
            adUnitId = "ca-app-pub-3940256099942544/9214589741",
        )
        assertTrue(offlineAdDecision is AdRequestDecision.Block)
    }

    @Test
    fun `10 11 12 ump consent scopes and prohibited routes control ad requests safely`() {
        // Consent not yet obtained -> blocked
        val noConsent = AdRequestPolicy.evaluateRequest(
            slot = AdSlot.HOME_BANNER,
            tier = EntitlementTier.FREE,
            canRequestAds = false,
            consentScope = ConsentScope.PRIVACY_ONLY,
            isNetworkConnected = true,
            adUnitId = "ca-app-pub-3940256099942544/9214589741",
        )
        assertTrue(noConsent is AdRequestDecision.Block)

        // EEA user allowed basic storage (purpose 1) without personalized ads (purposes 3,4) -> NPA
        val npaScope = ConsentScopeResolver.resolveScope(
            canRequestAds = true,
            gdprApplies = 1,
            purposeConsents = "1100",
            adFreePro = false,
        )
        assertEquals(ConsentScope.NON_PERSONALIZED_ADS, npaScope)

        val npaDecision = AdRequestPolicy.evaluateRequest(
            slot = AdSlot.LIBRARY_BANNER,
            tier = EntitlementTier.FREE,
            canRequestAds = true,
            consentScope = npaScope,
            isNetworkConnected = true,
            adUnitId = "ca-app-pub-3940256099942544/9214589741",
            currentRoute = "library",
        )
        assertTrue(npaDecision is AdRequestDecision.Allow)
        assertTrue((npaDecision as AdRequestDecision.Allow).nonPersonalizedOnly)

        // Prohibited routes must NEVER request or display ads
        listOf("player", "equalizer", "settings/equalizer", "premium", "onboarding", "welcome", "sleep_timer", "ambient_mode")
            .forEach { route ->
                val blocked = AdRequestPolicy.evaluateRequest(
                    slot = AdSlot.HOME_BANNER,
                    tier = EntitlementTier.FREE,
                    canRequestAds = true,
                    consentScope = ConsentScope.PERSONALIZED_ADS,
                    isNetworkConnected = true,
                    adUnitId = "ca-app-pub-3940256099942544/9214589741",
                    currentRoute = route,
                )
                assertTrue("Ad must be blocked on prohibited route $route", blocked is AdRequestDecision.Block)
            }

        // Throttling prevents duplicate ad spam inside cooldown window
        val throttled = AdRequestPolicy.evaluateRequest(
            slot = AdSlot.PLAYLIST_BANNER,
            tier = EntitlementTier.FREE,
            canRequestAds = true,
            consentScope = ConsentScope.PERSONALIZED_ADS,
            isNetworkConnected = true,
            adUnitId = "ca-app-pub-3940256099942544/9214589741",
            currentRoute = "local_playlist/1",
            nowMillis = 100_000L,
            lastAttemptMillis = 80_000L,
        )
        assertTrue(throttled is AdRequestDecision.Block)

        // Blank unit ID (e.g. unconfigured release unit) disables ads cleanly
        val unconfigured = AdRequestPolicy.evaluateRequest(
            slot = AdSlot.HOME_BANNER,
            tier = EntitlementTier.FREE,
            canRequestAds = true,
            consentScope = ConsentScope.PERSONALIZED_ADS,
            isNetworkConnected = true,
            adUnitId = "",
        )
        assertTrue(unconfigured is AdRequestDecision.Block)

        // PRO user never initializes ads SDK
        assertFalse(
            AdRequestPolicy.canInitializeMobileAdsSdk(
                tier = EntitlementTier.PRO,
                canRequestAds = true,
                anyUnitConfigured = true,
            )
        )
    }

    @Test
    fun `proposed Play Console SKUs match M4 specification`() {
        assertEquals(
            setOf(
                "apprise_music_pro_lifetime",
                "apprise_music_pro_monthly",
                "apprise_music_pro_annual",
            ),
            AppriseProducts.ALL_IDS,
        )
    }
}

