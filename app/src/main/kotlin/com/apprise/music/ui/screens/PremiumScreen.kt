package com.apprise.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.apprise.music.ads.findActivity
import com.apprise.music.billing.BillingAvailability
import com.apprise.music.billing.BillingClientBoundary
import com.apprise.music.billing.BillingProduct
import com.apprise.music.billing.BillingProductKind
import com.apprise.music.billing.PurchaseState
import com.apprise.music.billing.RestoreOutcome
import com.apprise.music.entitlement.AppriseProducts
import com.apprise.music.entitlement.EntitlementProvider
import com.apprise.music.entitlement.EntitlementTier
import com.apprise.music.entitlement.FeatureCapability
import com.apprise.music.entitlement.ProBadge
import com.apprise.music.entitlement.capabilityDisplaySummary
import com.convx.music.BuildConfig
import com.convx.music.R
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Button
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.OutlinedButton
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TextButton
import com.convx.music.ui.screens.settings.SettingsPage
import com.convx.music.ui.utils.safeOpenUri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(
    val entitlementProvider: EntitlementProvider,
    val billingClient: BillingClientBoundary,
) : ViewModel()

/**
 * Dedicated Apprise Music Pro upgrade & entitlement management screen.
 * Ads are strictly prohibited here (`AdRequestPolicy.isRouteProhibited("premium") == true`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    navController: NavController,
    viewModel: PremiumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val entitlements by viewModel.entitlementProvider.entitlements.collectAsStateWithLifecycle()
    val availability by viewModel.billingClient.availability.collectAsStateWithLifecycle()
    val products by viewModel.billingClient.products.collectAsStateWithLifecycle()
    val purchaseState by viewModel.billingClient.purchaseState.collectAsStateWithLifecycle()
    val billingError by viewModel.billingClient.lastErrorMessage.collectAsStateWithLifecycle()

    var restoreFeedback by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.billingClient.queryAvailableProducts()
    }

    val proCapabilities = remember {
        listOf(
            FeatureCapability.AD_FREE,
            FeatureCapability.ADVANCED_EQUALIZER,
            FeatureCapability.PREMIUM_THEMES,
            FeatureCapability.VISUALIZER,
            FeatureCapability.CROSSFADE,
            FeatureCapability.ADVANCED_CUSTOMIZATION,
        )
    }

    SettingsPage(
        navController = navController,
        title = "Apprise Pro",
        horizontalPadding = 16.dp,
    ) {
        // 1. Live Entitlement Status Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(20.dp))
                .background(AppTheme.colorScheme.surfaceContainerHigh)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.star),
                    contentDescription = null,
                    tint = AppTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = when (entitlements.tier) {
                        EntitlementTier.PRO -> "Apprise Pro Active"
                        EntitlementTier.PENDING -> "Purchase Pending Confirmation"
                        EntitlementTier.EXPIRED -> "Subscription Expired"
                        EntitlementTier.UNKNOWN -> "Offline / Play Store Unverified"
                        EntitlementTier.FREE -> "Upgrade to MUZIKQUE Pro"
                    },
                    style = AppTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                )
                if (entitlements.isPro) {
                    ProBadge(text = "ACTIVE")
                }
            }

            Text(
                text = when (entitlements.tier) {
                    EntitlementTier.PRO ->
                        "Verified entitlement (${entitlements.activeProductId ?: "Pro"}). All ads are removed and Pro audio, visual, and customization tools are unlocked."
                    EntitlementTier.PENDING ->
                        "Google Play is still confirming your payment. Pro features will unlock automatically once settled. Local playback continues uninterrupted."
                    EntitlementTier.EXPIRED ->
                        "Your previous Pro subscription has expired. Core local music playback remains free, and you can renew or switch to Lifetime below."
                    EntitlementTier.UNKNOWN ->
                        "Google Play Billing could not verify purchase status right now (${entitlements.statusMessage ?: "offline or unavailable"}). All local music playback continues uninterrupted."
                    EntitlementTier.FREE ->
                        "Keep your entire local library free forever, or unlock Apprise Pro to remove advertisements across Home, Library, and Playlists plus all Pro features."
                },
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )

            if (purchaseState == PurchaseState.CANCELED) {
                Text(
                    text = "Purchase canceled. No charges were made.",
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colorScheme.primary,
                )
            } else if (!billingError.isNullOrBlank()) {
                Text(
                    text = billingError.orEmpty(),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colorScheme.error,
                )
            } else if (!restoreFeedback.isNullOrBlank()) {
                Text(
                    text = restoreFeedback.orEmpty(),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 2. What Pro Unlocks (transparent, accurate summary)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(18.dp))
                .background(AppTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "What Apprise Pro Unlocks",
                style = AppTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            proCapabilities.forEach { capability ->
                val (label, desc) = capabilityDisplaySummary(capability)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (entitlements.isPro) R.drawable.check else R.drawable.star
                        ),
                        contentDescription = null,
                        tint = AppTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = AppTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = desc,
                            style = AppTheme.typography.bodySmall,
                            color = AppTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 3. Purchase Options (Lifetime, Annual, Monthly from real Google Play Billing catalog)
        if (!entitlements.isPro) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Purchase Options",
                    style = AppTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
                )

                if (products.isEmpty()) {
                    if (availability == BillingAvailability.CONNECTING) {
                        com.convx.music.ui.component.AppLoadingState(
                            message = "Connecting to Google Play Store...",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ContinuousRoundedRectangle(16.dp))
                                .background(AppTheme.colorScheme.surfaceContainer),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ContinuousRoundedRectangle(16.dp))
                                .background(AppTheme.colorScheme.surfaceContainer)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Google Play product prices are currently unavailable.",
                                style = AppTheme.typography.titleSmall,
                                color = AppTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Plans configured for Play Console verification: Lifetime (${AppriseProducts.LIFETIME_PRO}), Annual (${AppriseProducts.ANNUAL_PRO}), Monthly (${AppriseProducts.MONTHLY_PRO}). Core local playback stays 100% available offline.",
                                style = AppTheme.typography.bodySmall,
                                color = AppTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        viewModel.billingClient.queryAvailableProducts()
                                    }
                                },
                            ) {
                                Text("Retry Play Store Connection")
                            }
                        }
                    }
                } else {
                    products.forEach { product ->
                        ProductOptionCard(
                            product = product,
                            enabled = entitlements.tier != EntitlementTier.PENDING,
                            onPurchaseClick = {
                                val activity = context.findActivity() ?: return@ProductOptionCard
                                viewModel.billingClient.clearFeedback()
                                scope.launch {
                                    viewModel.billingClient.launchPurchase(activity, product)
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // 4. Restore Purchases & Subscription Management
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(18.dp))
                .background(AppTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (isRestoring) return@OutlinedButton
                    isRestoring = true
                    restoreFeedback = null
                    scope.launch {
                        val outcome = viewModel.billingClient.restorePurchasesWithOutcome()
                        restoreFeedback = when (outcome) {
                            RestoreOutcome.RESTORED -> "Verified purchase restored successfully."
                            RestoreOutcome.PENDING -> "Found a pending purchase waiting for Google Play confirmation."
                            RestoreOutcome.NONE_FOUND -> "No active Apprise Pro purchases were found for this Google Play account."
                            RestoreOutcome.BILLING_UNAVAILABLE -> "Google Play Billing is unavailable offline or on this device."
                            RestoreOutcome.ERROR -> "Unable to verify purchases with Google Play right now."
                        }
                        isRestoring = false
                    }
                },
                enabled = !isRestoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isRestoring) "Checking Google Play..." else "Restore Purchases")
            }

            if (entitlements.activeProductId?.let(AppriseProducts::isSubscription) == true ||
                entitlements.tier == EntitlementTier.EXPIRED
            ) {
                TextButton(
                    onClick = {
                        uriHandler.safeOpenUri(context, "https://play.google.com/store/account/subscriptions")
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Manage Subscription in Google Play")
                }
            }

            // Honest billing & privacy disclosures (no hidden terms, no auto-renewal obfuscation)
            Text(
                text = "One-time Lifetime purchase requires a single payment and never auto-renews. Optional Monthly or Annual subscriptions renew automatically through Google Play unless canceled at least 24 hours before the end of the current billing period.",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextButton(
                    onClick = { uriHandler.safeOpenUri(context, BuildConfig.TERMS_URL) },
                ) {
                    Text("Terms of Use")
                }
                TextButton(
                    onClick = { uriHandler.safeOpenUri(context, BuildConfig.PRIVACY_POLICY_URL) },
                ) {
                    Text("Privacy Policy")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProductOptionCard(
    product: BillingProduct,
    enabled: Boolean,
    onPurchaseClick: () -> Unit,
) {
    val isLifetime = product.productId == AppriseProducts.LIFETIME_PRO
    val subtitle = when (product.kind) {
        BillingProductKind.ONE_TIME_INAPP -> "Lifetime unlock • Single one-time payment"
        BillingProductKind.SUBSCRIPTION -> {
            val cadence = when (product.billingPeriodIso) {
                "P1M" -> "Billed monthly"
                "P1Y" -> "Billed annually"
                "P1W" -> "Billed weekly"
                else -> "Recurring Play subscription"
            }
            "$cadence • Cancel anytime in Google Play"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(16.dp))
            .background(
                if (isLifetime) AppTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else AppTheme.colorScheme.surfaceContainer
            )
            .then(
                if (isLifetime) {
                    Modifier.border(
                        width = 1.dp,
                        color = AppTheme.colorScheme.primary.copy(alpha = 0.6f),
                        shape = ContinuousRoundedRectangle(16.dp),
                    )
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onPurchaseClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = product.title.ifBlank {
                        when (product.productId) {
                            AppriseProducts.LIFETIME_PRO -> "Apprise Pro Lifetime"
                            AppriseProducts.ANNUAL_PRO -> "Apprise Pro Annual"
                            AppriseProducts.MONTHLY_PRO -> "Apprise Pro Monthly"
                            else -> product.productId
                        }
                    },
                    style = AppTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colorScheme.onSurface,
                )
                if (isLifetime) {
                    ProBadge(text = "LIFETIME")
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = onPurchaseClick,
            enabled = enabled,
        ) {
            Text(product.formattedPrice)
        }
    }
}

