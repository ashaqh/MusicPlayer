package com.apprise.music.entitlement

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Cryptographic signer protecting persisted entitlement envelopes against boolean tampering. */
interface EntitlementEnvelopeSigner {
    fun sign(payload: String): String
    fun verify(payload: String, macHex: String): Boolean
}

class AndroidKeystoreHmacSigner(
    private val keyAlias: String,
    private val packageSeed: String,
) : EntitlementEnvelopeSigner {

    override fun sign(payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGO)
        mac.init(resolveOrCreateKey())
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).toHex()
    }

    override fun verify(payload: String, macHex: String): Boolean {
        if (macHex.isBlank()) return false
        val expected = sign(payload)
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            macHex.toByteArray(Charsets.UTF_8),
        )
    }

    private fun resolveOrCreateKey(): SecretKey = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            ).build()
        )
        keyGenerator.generateKey()
    }.getOrElse {
        // Fallback deterministic HMAC key for non-KeyStore environments (e.g. JVM tests).
        val derived = MessageDigest.getInstance("SHA-256")
            .digest("$keyAlias|$packageSeed".toByteArray(Charsets.UTF_8))
        SecretKeySpec(derived, HMAC_ALGO)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val HMAC_ALGO = "HmacSHA256"
    }
}

@Serializable
private data class SignedEntitlementFileEnvelope(
    val payloadJson: String,
    val hmacHex: String,
)

@Serializable
data class PersistedEntitlementPayload(
    val productId: String,
    val purchaseTokenHash: String,
    val orderId: String? = null,
    val purchaseTimeMillis: Long,
    val expiryTimeMillis: Long? = null,
    val isAcknowledged: Boolean,
    val isAutoRenewing: Boolean,
    val verifiedAtMillis: Long,
    val lastExpiredProductId: String? = null,
)

data class CachedEntitlementLoadResult(
    val record: VerifiedEntitlementRecord? = null,
    val lastExpiredProductId: String? = null,
    val tampered: Boolean = false,
)

/**
 * Stores HMAC-SHA256 integrity-protected entitlement verification envelopes.
 * Never reads or trusts an unverified boolean preference flag.
 */
class VerifiedEntitlementStore(
    private val storageFile: File,
    private val signer: EntitlementEnvelopeSigner,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun read(): CachedEntitlementLoadResult {
        if (!storageFile.exists()) return CachedEntitlementLoadResult()
        return runCatching {
            val raw = storageFile.readText(Charsets.UTF_8)
            val envelope = json.decodeFromString<SignedEntitlementFileEnvelope>(raw)
            if (!signer.verify(envelope.payloadJson, envelope.hmacHex)) {
                return CachedEntitlementLoadResult(tampered = true)
            }
            val payload = json.decodeFromString<PersistedEntitlementPayload>(envelope.payloadJson)
            if (payload.productId.isBlank() && payload.lastExpiredProductId != null) {
                return CachedEntitlementLoadResult(lastExpiredProductId = payload.lastExpiredProductId)
            }
            if (payload.productId !in AppriseProducts.ALL_IDS || payload.purchaseTokenHash.isBlank()) {
                return CachedEntitlementLoadResult(tampered = true)
            }
            CachedEntitlementLoadResult(
                record = VerifiedEntitlementRecord(
                    productId = payload.productId,
                    purchaseTokenHash = payload.purchaseTokenHash,
                    orderId = payload.orderId,
                    purchaseTimeMillis = payload.purchaseTimeMillis,
                    expiryTimeMillis = payload.expiryTimeMillis,
                    isAcknowledged = payload.isAcknowledged,
                    isAutoRenewing = payload.isAutoRenewing,
                    verifiedAtMillis = payload.verifiedAtMillis,
                    verificationSource = EntitlementVerificationSource.CACHED_VERIFIED_OFFLINE,
                ),
                lastExpiredProductId = payload.lastExpiredProductId,
            )
        }.getOrElse {
            CachedEntitlementLoadResult(tampered = true)
        }
    }

    @Synchronized
    fun writeVerified(record: VerifiedEntitlementRecord) {
        val payload = PersistedEntitlementPayload(
            productId = record.productId,
            purchaseTokenHash = record.purchaseTokenHash,
            orderId = record.orderId,
            purchaseTimeMillis = record.purchaseTimeMillis,
            expiryTimeMillis = record.expiryTimeMillis,
            isAcknowledged = record.isAcknowledged,
            isAutoRenewing = record.isAutoRenewing,
            verifiedAtMillis = record.verifiedAtMillis,
        )
        persistPayload(payload)
    }

    @Synchronized
    fun writeExpiredMarker(productId: String, nowMillis: Long) {
        val payload = PersistedEntitlementPayload(
            productId = "",
            purchaseTokenHash = "",
            purchaseTimeMillis = nowMillis,
            isAcknowledged = false,
            isAutoRenewing = false,
            verifiedAtMillis = nowMillis,
            lastExpiredProductId = productId,
        )
        persistPayload(payload)
    }

    @Synchronized
    fun clear() {
        if (storageFile.exists()) {
            storageFile.delete()
        }
    }

    private fun persistPayload(payload: PersistedEntitlementPayload) {
        storageFile.parentFile?.mkdirs()
        val payloadJson = json.encodeToString(payload)
        val envelope = SignedEntitlementFileEnvelope(
            payloadJson = payloadJson,
            hmacHex = signer.sign(payloadJson),
        )
        storageFile.writeText(json.encodeToString(envelope), Charsets.UTF_8)
    }
}

@Singleton
class VerifiedEntitlementCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = VerifiedEntitlementStore(
        storageFile = File(context.noBackupFilesDir, "apprise_verified_entitlement.json"),
        signer = AndroidKeystoreHmacSigner(
            keyAlias = "apprise_entitlement_hmac_v1",
            packageSeed = context.packageName,
        ),
    )

    fun read(): CachedEntitlementLoadResult = store.read()
    fun writeVerified(record: VerifiedEntitlementRecord) = store.writeVerified(record)
    fun writeExpiredMarker(productId: String, nowMillis: Long) = store.writeExpiredMarker(productId, nowMillis)
    fun clear() = store.clear()
}

