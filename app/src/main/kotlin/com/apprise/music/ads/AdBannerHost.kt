package com.apprise.music.ads

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apprise.music.consent.ConsentManagerBoundary
import com.apprise.music.entitlement.FeatureCapability
import com.apprise.music.entitlement.LocalEntitlementProvider
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

val LocalAdPlacementBoundary = staticCompositionLocalOf<AdPlacementBoundary?> { null }
val LocalConsentManager = staticCompositionLocalOf<ConsentManagerBoundary?> { null }

/**
 * Non-blocking adaptive AdMob banner container scoped exclusively to Home, Library, and Playlist
 * screens. Collapses completely to 0 dp on Pro entitlement, missing consent, offline state,
 * prohibited screens, or load errors — and never interrupts playback or steals audio focus.
 */
@Composable
fun AppriseBannerAdSlot(
    slot: AdSlot,
    modifier: Modifier = Modifier,
    currentRoute: String? = null,
) {
    val entitlementProvider = LocalEntitlementProvider.current
    val entitlements by entitlementProvider.entitlements.collectAsStateWithLifecycle()
    val adManager = LocalAdPlacementBoundary.current ?: return
    val consentManager = LocalConsentManager.current

    val isAdFree = FeatureCapability.AD_FREE in entitlements.unlockedCapabilities
    val canRequestAds = consentManager?.canRequestAds?.collectAsStateWithLifecycle()?.value ?: true
    val consentScope = consentManager?.activeScope?.collectAsStateWithLifecycle()?.value

    if (isAdFree || !canRequestAds || AdRequestPolicy.isRouteProhibited(currentRoute)) {
        return
    }

    val decision = remember(slot, entitlements.tier, canRequestAds, consentScope, currentRoute) {
        adManager.evaluateSlot(slot, currentRoute)
    }

    val allowDecision = decision as? AdRequestDecision.Allow ?: return
    val context = LocalContext.current
    var adLoaded by remember(allowDecision.adUnitId) { mutableStateOf(false) }
    var adFailed by remember(allowDecision.adUnitId) { mutableStateOf(false) }

    if (adFailed) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val widthDp = with(LocalDensity.current) { maxWidth.value.toInt().coerceAtLeast(320) }
        val adView = remember(allowDecision.adUnitId, widthDp, allowDecision.nonPersonalizedOnly) {
            AdView(context).apply {
                setAdUnitId(allowDecision.adUnitId)
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
                )
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        adLoaded = true
                        adFailed = false
                        adManager.markSlotState(slot, AdLoadState.READY)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        adLoaded = false
                        adFailed = true
                        adManager.markSlotState(slot, AdLoadState.FAILED)
                    }
                }
            }
        }

        LaunchedEffect(adView, allowDecision) {
            adManager.markSlotState(slot, AdLoadState.LOADING)
            val req = (adManager as? AdMobPlacementManager)
                ?.buildAdRequest(allowDecision.nonPersonalizedOnly)
                ?: AdRequest.Builder().apply {
                    if (allowDecision.nonPersonalizedOnly) {
                        addNetworkExtrasBundle(
                            AdMobAdapter::class.java,
                            android.os.Bundle().apply { putInt("npa", 1) },
                        )
                    }
                }.build()
            adView.loadAd(req)
        }

        DisposableEffect(adView) {
            onDispose {
                runCatching { adView.destroy() }
            }
        }

        AnimatedVisibility(
            visible = adLoaded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ContinuousRoundedRectangle(12.dp))
                    .background(AppTheme.colorScheme.surfaceContainerLow)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Sponsored",
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                AndroidView(
                    factory = { adView },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

