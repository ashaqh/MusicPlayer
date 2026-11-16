package com.apprise.music.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryPurchasesAsync
import com.apprise.music.entitlement.AppriseProducts
import com.apprise.music.entitlement.RawPurchaseRecord
import com.apprise.music.entitlement.RawPurchaseState
import com.apprise.music.entitlement.VerifiedEntitlementManager
import com.convx.music.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.min

/**
 * Google Play Billing Library (PBL 8.0+) integration managing connection lifecycle, product catalog
 * queries, purchase flows, pending transactions, acknowledgements, restore, and revocation/expiry sync.
 */
@Singleton
class PlayBillingClientManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val entitlementManager: VerifiedEntitlementManager,
) : BillingClientBoundary, PurchasesUpdatedListener, BillingClientStateListener {

    private val _availability = MutableStateFlow(BillingAvailability.UNCONFIGURED)
    override val availability: StateFlow<BillingAvailability> = _availability.asStateFlow()

    private val _products = MutableStateFlow<List<BillingProduct>>(emptyList())
    override val products: StateFlow<List<BillingProduct>> = _products.asStateFlow()

    private val _purchaseState = MutableStateFlow(PurchaseState.NONE)
    override val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val connectMutex = Mutex()
    private val productDetailsCache = ConcurrentHashMap<String, ProductDetails>()
    private var connectionDeferred: CompletableDeferred<Boolean>? = null
    private var reconnectAttempt = 0

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        refreshInBackground()
    }

    override fun refreshInBackground() {
        appScope.launch(Dispatchers.IO) {
            if (ensureConnected()) {
                queryAvailableProducts()
                restorePurchasesInternal()
            } else {
                entitlementManager.markBillingUnavailable()
            }
        }
    }

    override fun clearFeedback() {
        if (_purchaseState.value == PurchaseState.CANCELED || _purchaseState.value == PurchaseState.ERROR) {
            _purchaseState.value = if (entitlementManager.isPro) PurchaseState.PURCHASED else PurchaseState.NONE
        }
        _lastErrorMessage.value = null
    }

    private suspend fun ensureConnected(): Boolean {
        if (billingClient.isReady) {
            _availability.value = BillingAvailability.READY
            return true
        }

        val deferred = connectMutex.withLock {
            if (billingClient.isReady) {
                _availability.value = BillingAvailability.READY
                return@withLock null
            }
            connectionDeferred?.takeIf { !it.isCompleted } ?: CompletableDeferred<Boolean>().also { created ->
                connectionDeferred = created
                _availability.value = BillingAvailability.CONNECTING
                runCatching {
                    billingClient.startConnection(this)
                }.onFailure { err ->
                    Timber.tag(TAG).w(err, "BillingClient.startConnection threw")
                    _availability.value = BillingAvailability.UNAVAILABLE
                    _lastErrorMessage.value = err.message ?: "Google Play Billing unavailable"
                    created.complete(false)
                }
            }
        }
        return deferred?.await() ?: true
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val deferred = connectionDeferred
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            reconnectAttempt = 0
            _availability.value = BillingAvailability.READY
            _lastErrorMessage.value = null
            deferred?.complete(true)
        } else {
            val code = billingResult.responseCode
            val msg = billingResult.debugMessage.ifBlank { "Play Billing error ($code)" }
            Timber.tag(TAG).w("Billing setup failed code=%d msg=%s", code, msg)
            _availability.value = BillingAvailability.UNAVAILABLE
            _lastErrorMessage.value = msg
            deferred?.complete(false)
            appScope.launch { entitlementManager.markBillingUnavailable() }
        }
    }

    override fun onBillingServiceDisconnected() {
        Timber.tag(TAG).w("Play Billing service disconnected")
        _availability.value = BillingAvailability.DISCONNECTED
        connectionDeferred?.takeIf { !it.isCompleted }?.complete(false)
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val attempt = reconnectAttempt++
        if (attempt > MAX_RECONNECT_ATTEMPTS) return
        val backoffMs = min(MAX_BACKOFF_MS, BASE_BACKOFF_MS shl attempt.coerceAtMost(5))
        appScope.launch(Dispatchers.IO) {
            delay(backoffMs)
            if (ensureConnected()) {
                queryAvailableProducts()
                restorePurchasesInternal()
            }
        }
    }

    override suspend fun queryAvailableProducts(): List<BillingProduct> {
        if (!ensureConnected()) return _products.value

        val inAppDetails = queryProductType(AppriseProducts.INAPP_IDS, BillingClient.ProductType.INAPP)
        val subsDetails = queryProductType(AppriseProducts.SUBS_IDS, BillingClient.ProductType.SUBS)

        val merged = (inAppDetails + subsDetails)
            .onEach { details -> productDetailsCache[details.productId] = details }
            .mapNotNull(::mapProductDetails)
            .sortedBy { orderForProduct(it.productId) }

        _products.value = merged
        return merged
    }

    private suspend fun queryProductType(
        ids: List<String>,
        productType: String,
    ): List<ProductDetails> {
        if (ids.isEmpty()) return emptyList()
        val products = ids.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(productType)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { result, queryResult ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (queryResult.unfetchedProductList.isNotEmpty()) {
                        Timber.tag(TAG).w("Unfetched Play products: %s", queryResult.unfetchedProductList.map { it.productId })
                    }
                    cont.resume(queryResult.productDetailsList)
                } else {
                    Timber.tag(TAG).w("queryProductDetailsAsync(%s) failed: %d %s", productType, result.responseCode, result.debugMessage)
                    cont.resume(emptyList())
                }
            }
        }
    }

    override suspend fun launchPurchase(activity: Activity, product: BillingProduct): PurchaseState {
        if (!ensureConnected()) {
            _purchaseState.value = PurchaseState.ERROR
            _lastErrorMessage.value = "Google Play Billing is unavailable. Offline playback remains active."
            return PurchaseState.ERROR
        }

        val details = productDetailsCache[product.productId] ?: run {
            queryAvailableProducts()
            productDetailsCache[product.productId]
        }

        if (details == null) {
            _purchaseState.value = PurchaseState.ERROR
            _lastErrorMessage.value = "Product '${product.productId}' is not yet configured in Play Console."
            return PurchaseState.ERROR
        }

        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        if (product.kind == BillingProductKind.SUBSCRIPTION) {
            val selectedOfferToken = product.offerToken
                ?: details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            if (selectedOfferToken.isNullOrBlank()) {
                _purchaseState.value = PurchaseState.ERROR
                _lastErrorMessage.value = "No active Play subscription offer found for ${product.productId}."
                return PurchaseState.ERROR
            }
            productParamsBuilder.setOfferToken(selectedOfferToken)
        }

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
            .build()

        val result = withContext(Dispatchers.Main) {
            billingClient.launchBillingFlow(activity, flowParams)
        }

        // Never unlock Pro just because launchBillingFlow returned OK; wait for verified PurchasesUpdatedListener.
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                return restorePurchases()
            }
            val nextState = if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                PurchaseState.CANCELED
            } else {
                _lastErrorMessage.value = result.debugMessage.ifBlank { "Unable to start purchase flow" }
                PurchaseState.ERROR
            }
            _purchaseState.value = nextState
            return nextState
        }

        return _purchaseState.value
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        appScope.launch(Dispatchers.IO) {
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val list = purchases.orEmpty()
                    val allOwned = fetchAllActivePurchases() + list
                    processAndReconcilePurchases(allOwned.distinctBy { it.purchaseToken }, billingAvailable = true)
                }
                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    _purchaseState.value = PurchaseState.CANCELED
                    _lastErrorMessage.value = null
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    restorePurchasesInternal()
                }
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                    _availability.value = BillingAvailability.DISCONNECTED
                    _lastErrorMessage.value = billingResult.debugMessage.ifBlank { "Google Play service unavailable" }
                    entitlementManager.markBillingUnavailable()
                }
                else -> {
                    _purchaseState.value = PurchaseState.ERROR
                    _lastErrorMessage.value = billingResult.debugMessage.ifBlank {
                        "Purchase failed (code ${billingResult.responseCode})"
                    }
                }
            }
        }
    }

    override suspend fun restorePurchases(): PurchaseState {
        return when (restorePurchasesInternal()) {
            RestoreOutcome.RESTORED -> PurchaseState.PURCHASED
            RestoreOutcome.PENDING -> PurchaseState.PENDING
            RestoreOutcome.NONE_FOUND -> PurchaseState.NONE
            RestoreOutcome.BILLING_UNAVAILABLE,
            RestoreOutcome.ERROR -> PurchaseState.ERROR
        }
    }

    override suspend fun restorePurchasesWithOutcome(): RestoreOutcome = restorePurchasesInternal()

    private suspend fun restorePurchasesInternal(): RestoreOutcome {
        if (!ensureConnected()) {
            entitlementManager.markBillingUnavailable()
            return RestoreOutcome.BILLING_UNAVAILABLE
        }
        val activePurchases = fetchAllActivePurchases()
        val snapshot = processAndReconcilePurchases(activePurchases, billingAvailable = true)
        return when (snapshot.tier) {
            com.apprise.music.entitlement.EntitlementTier.PRO -> RestoreOutcome.RESTORED
            com.apprise.music.entitlement.EntitlementTier.PENDING -> RestoreOutcome.PENDING
            com.apprise.music.entitlement.EntitlementTier.UNKNOWN -> RestoreOutcome.ERROR
            com.apprise.music.entitlement.EntitlementTier.FREE,
            com.apprise.music.entitlement.EntitlementTier.EXPIRED -> RestoreOutcome.NONE_FOUND
        }
    }

    private suspend fun fetchAllActivePurchases(): List<Purchase> {
        val inAppResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        )
        val subsResult = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        )
        val combined = mutableListOf<Purchase>()
        if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            combined.addAll(inAppResult.purchasesList)
        }
        if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            combined.addAll(subsResult.purchasesList)
        }
        return combined
    }

    private suspend fun processAndReconcilePurchases(
        purchases: List<Purchase>,
        billingAvailable: Boolean,
    ): com.apprise.music.entitlement.EntitlementSnapshot {
        // Acknowledge newly purchased & unacknowledged tokens before finalizing Pro state.
        val acknowledgedTokens = mutableSetOf<String>()
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                val ackResult = billingClient.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                )
                if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    acknowledgedTokens += purchase.purchaseToken
                } else {
                    Timber.tag(TAG).w("acknowledgePurchase failed: %d %s", ackResult.responseCode, ackResult.debugMessage)
                }
            }
        }

        val rawRecords = purchases.map { purchase ->
            RawPurchaseRecord(
                productIds = purchase.products,
                purchaseToken = purchase.purchaseToken,
                orderId = purchase.orderId,
                packageName = purchase.packageName.ifBlank { context.packageName },
                purchaseTimeMillis = purchase.purchaseTime,
                purchaseState = when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> RawPurchaseState.PURCHASED
                    Purchase.PurchaseState.PENDING -> RawPurchaseState.PENDING
                    else -> RawPurchaseState.UNSPECIFIED
                },
                isAcknowledged = purchase.isAcknowledged || purchase.purchaseToken in acknowledgedTokens,
                isAutoRenewing = purchase.isAutoRenewing,
                originalJson = purchase.originalJson,
                signature = purchase.signature,
            )
        }

        val snapshot = entitlementManager.reconcilePlaySession(rawRecords, billingAvailable)
        _purchaseState.value = when (snapshot.tier) {
            com.apprise.music.entitlement.EntitlementTier.PRO -> PurchaseState.PURCHASED
            com.apprise.music.entitlement.EntitlementTier.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.NONE
        }
        return snapshot
    }

    private fun mapProductDetails(details: ProductDetails): BillingProduct? {
        return when (details.productType) {
            BillingClient.ProductType.INAPP -> {
                val oneTime = details.oneTimePurchaseOfferDetails ?: return null
                BillingProduct(
                    productId = details.productId,
                    title = details.title,
                    description = details.description,
                    formattedPrice = oneTime.formattedPrice,
                    kind = BillingProductKind.ONE_TIME_INAPP,
                    priceAmountMicros = oneTime.priceAmountMicros,
                    priceCurrencyCode = oneTime.priceCurrencyCode,
                )
            }
            BillingClient.ProductType.SUBS -> {
                val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return null
                val phase = offer.pricingPhases.pricingPhaseList.firstOrNull() ?: return null
                BillingProduct(
                    productId = details.productId,
                    title = details.title,
                    description = details.description,
                    formattedPrice = phase.formattedPrice,
                    kind = BillingProductKind.SUBSCRIPTION,
                    offerToken = offer.offerToken,
                    billingPeriodIso = phase.billingPeriod,
                    priceAmountMicros = phase.priceAmountMicros,
                    priceCurrencyCode = phase.priceCurrencyCode,
                )
            }
            else -> null
        }
    }

    private fun orderForProduct(productId: String): Int = when (productId) {
        AppriseProducts.LIFETIME_PRO -> 0
        AppriseProducts.ANNUAL_PRO -> 1
        AppriseProducts.MONTHLY_PRO -> 2
        else -> 99
    }

    private companion object {
        const val TAG = "PlayBillingManager"
        const val MAX_RECONNECT_ATTEMPTS = 4
        const val BASE_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}

