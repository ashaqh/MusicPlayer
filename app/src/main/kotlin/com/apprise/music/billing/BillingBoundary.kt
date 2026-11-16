package com.apprise.music.billing

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Billing availability state for Google Play Billing interactions. */
enum class BillingAvailability {
    UNCONFIGURED,
    CONNECTING,
    READY,
    DISCONNECTED,
    UNAVAILABLE,
}

/** Purchase lifecycle state for a product or subscription SKU. */
enum class PurchaseState {
    NONE,
    PENDING,
    PURCHASED,
    CANCELED,
    ERROR,
}

enum class BillingProductKind {
    ONE_TIME_INAPP,
    SUBSCRIPTION,
}

enum class RestoreOutcome {
    RESTORED,
    NONE_FOUND,
    PENDING,
    BILLING_UNAVAILABLE,
    ERROR,
}

data class BillingProduct(
    val productId: String,
    val title: String,
    val formattedPrice: String,
    val description: String = "",
    val kind: BillingProductKind = BillingProductKind.ONE_TIME_INAPP,
    val offerToken: String? = null,
    val billingPeriodIso: String? = null,
    val priceAmountMicros: Long? = null,
    val priceCurrencyCode: String? = null,
)

/**
 * Clean boundary for Google Play Billing catalog queries, purchases, acknowledgement,
 * restoration, and entitlement synchronization.
 */
interface BillingClientBoundary {
    val availability: StateFlow<BillingAvailability>
    val products: StateFlow<List<BillingProduct>>
        get() = MutableStateFlow<List<BillingProduct>>(emptyList()).asStateFlow()
    val purchaseState: StateFlow<PurchaseState>
        get() = MutableStateFlow(PurchaseState.NONE).asStateFlow()
    val lastErrorMessage: StateFlow<String?>
        get() = MutableStateFlow<String?>(null).asStateFlow()

    suspend fun queryAvailableProducts(): List<BillingProduct>

    suspend fun restorePurchases(): PurchaseState

    suspend fun restorePurchasesWithOutcome(): RestoreOutcome = when (restorePurchases()) {
        PurchaseState.PURCHASED -> RestoreOutcome.RESTORED
        PurchaseState.PENDING -> RestoreOutcome.PENDING
        PurchaseState.NONE, PurchaseState.CANCELED -> RestoreOutcome.NONE_FOUND
        PurchaseState.ERROR -> RestoreOutcome.ERROR
    }

    suspend fun launchPurchase(activity: Activity, product: BillingProduct): PurchaseState =
        PurchaseState.ERROR

    fun refreshInBackground() = Unit

    fun clearFeedback() = Unit
}

/** M1 no-op placeholder implementation preserved for isolated tests. */
@Singleton
class NoOpBillingClientBoundary @Inject constructor() : BillingClientBoundary {
    private val _availability = MutableStateFlow(BillingAvailability.UNCONFIGURED)
    override val availability: StateFlow<BillingAvailability> = _availability.asStateFlow()

    override suspend fun queryAvailableProducts(): List<BillingProduct> = emptyList()

    override suspend fun restorePurchases(): PurchaseState = PurchaseState.NONE
}
