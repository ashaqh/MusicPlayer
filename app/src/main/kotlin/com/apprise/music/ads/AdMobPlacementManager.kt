package com.apprise.music.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import com.apprise.music.consent.ConsentManagerBoundary
import com.apprise.music.entitlement.EntitlementProvider
import com.apprise.music.entitlement.EntitlementTier
import com.apprise.music.entitlement.FeatureCapability
import com.convx.music.BuildConfig
import com.convx.music.di.ApplicationScope
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production Google Mobile Ads SDK manager (`play-services-ads:25.5.0`) scoped strictly
 * to permitted banner placements (Home, Library, Playlists) and gated behind [EntitlementProvider]
 * (`AD_FREE`) + [ConsentManagerBoundary] (`canRequestAds`, NPA consent flags).
 */
@Singleton
class AdMobPlacementManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    private val entitlementProvider: EntitlementProvider,
    private val consentManager: ConsentManagerBoundary,
) : AdPlacementBoundary {

    private val sdkInitialized = AtomicBoolean(false)
    private val lastAttemptMs = ConcurrentHashMap<AdSlot, Long>()
    private val slotStates: Map<AdSlot, MutableStateFlow<AdLoadState>> = EnumMap(
        mapOf(
            AdSlot.HOME_BANNER to MutableStateFlow(AdLoadState.IDLE),
            AdSlot.LIBRARY_BANNER to MutableStateFlow(AdLoadState.IDLE),
            AdSlot.PLAYLIST_BANNER to MutableStateFlow(AdLoadState.IDLE),
            AdSlot.INTERSTITIAL_TRANSITION to MutableStateFlow(AdLoadState.DISABLED),
        )
    )

    override fun observeSlotState(slot: AdSlot): StateFlow<AdLoadState> =
        (slotStates[slot] ?: MutableStateFlow(AdLoadState.DISABLED)).asStateFlow()

    override fun canShowAds(): Boolean {
        val tier = entitlementProvider.entitlements.value.tier
        return !entitlementProvider.isEnabled(FeatureCapability.AD_FREE) &&
            tier != EntitlementTier.PRO &&
            consentManager.canRequestAds.value &&
            anyConfiguredUnit()
    }

    override fun ensureSdkInitializedIfAllowed() {
        val allowed = AdRequestPolicy.canInitializeMobileAdsSdk(
            tier = entitlementProvider.entitlements.value.tier,
            canRequestAds = consentManager.canRequestAds.value,
            anyUnitConfigured = anyConfiguredUnit(),
        )
        if (!allowed || !isNetworkConnected()) return

        if (sdkInitialized.compareAndSet(false, true)) {
            appScope.launch(Dispatchers.IO) {
                runCatching {
                    MobileAds.initialize(context) { status ->
                        Timber.tag(TAG).d("MobileAds initialized: adapters=%d", status.adapterStatusMap.size)
                    }
                }.onFailure { err ->
                    sdkInitialized.set(false)
                    Timber.tag(TAG).w(err, "MobileAds.initialize failed; ads disabled without interrupting audio")
                }
            }
        }
    }

    override fun evaluateSlot(slot: AdSlot, currentRoute: String?): AdRequestDecision {
        val now = System.currentTimeMillis()
        val unitId = adUnitIdFor(slot)
        val decision = AdRequestPolicy.evaluateRequest(
            slot = slot,
            tier = entitlementProvider.entitlements.value.tier,
            canRequestAds = consentManager.canRequestAds.value,
            consentScope = consentManager.activeScope.value,
            isNetworkConnected = isNetworkConnected(),
            adUnitId = unitId,
            currentRoute = currentRoute,
            nowMillis = now,
            lastAttemptMillis = lastAttemptMs[slot] ?: 0L,
        )
        if (decision is AdRequestDecision.Allow) {
            ensureSdkInitializedIfAllowed()
        }
        return decision
    }

    override fun markSlotState(slot: AdSlot, state: AdLoadState, loadedAtMillis: Long) {
        if (state == AdLoadState.LOADING || state == AdLoadState.READY || state == AdLoadState.FAILED) {
            lastAttemptMs[slot] = loadedAtMillis
        }
        slotStates[slot]?.value = state
    }

    fun buildAdRequest(nonPersonalizedOnly: Boolean): AdRequest {
        val builder = AdRequest.Builder()
        if (nonPersonalizedOnly) {
            val extras = Bundle().apply { putInt("npa", 1) }
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }
        return builder.build()
    }

    private fun adUnitIdFor(slot: AdSlot): String = when (slot) {
        AdSlot.HOME_BANNER -> BuildConfig.ADMOB_BANNER_HOME_ID
        AdSlot.LIBRARY_BANNER -> BuildConfig.ADMOB_BANNER_LIBRARY_ID
        AdSlot.PLAYLIST_BANNER -> BuildConfig.ADMOB_BANNER_PLAYLIST_ID
        AdSlot.INTERSTITIAL_TRANSITION -> ""
    }

    private fun anyConfiguredUnit(): Boolean =
        BuildConfig.ADMOB_BANNER_HOME_ID.isNotBlank() ||
            BuildConfig.ADMOB_BANNER_LIBRARY_ID.isNotBlank() ||
            BuildConfig.ADMOB_BANNER_PLAYLIST_ID.isNotBlank()

    private fun isNetworkConnected(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    private companion object {
        const val TAG = "AdMobPlacementManager"
    }
}

