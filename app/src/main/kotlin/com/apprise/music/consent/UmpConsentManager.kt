package com.apprise.music.consent

import android.app.Activity
import android.content.Context
import android.preference.PreferenceManager
import com.apprise.music.entitlement.EntitlementProvider
import com.apprise.music.entitlement.FeatureCapability
import com.convx.music.BuildConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google User Messaging Platform (UMP SDK 4.0.0) coordinator supporting:
 * - Startup consent info updates
 * - Required EEA / UK / Switzerland consent form presentation (suppressed for Pro users)
 * - In-app Privacy Options entry point when required
 * - Non-blocking offline/error fallback so local audio playback never stalls
 */
@Singleton
class UmpConsentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementProvider: EntitlementProvider,
) : ConsentManagerBoundary {

    private val consentInfo: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)

    private val _status = MutableStateFlow(mapConsentStatus(consentInfo.consentStatus))
    override val status: StateFlow<ConsentStatus> = _status.asStateFlow()

    private val _canRequestAds = MutableStateFlow(consentInfo.canRequestAds())
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _isPrivacyOptionsRequired = MutableStateFlow(
        consentInfo.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    )
    override val isPrivacyOptionsRequired: StateFlow<Boolean> =
        _isPrivacyOptionsRequired.asStateFlow()

    private val _activeScope = MutableStateFlow(computeActiveScope())
    override val activeScope: StateFlow<ConsentScope> = _activeScope.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    override fun refreshConsent(
        activity: Activity,
        debugEeaGeography: Boolean,
        onCompleted: () -> Unit,
    ) {
        val paramsBuilder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)

        if (BuildConfig.DEBUG && debugEeaGeography) {
            val debugSettings = ConsentDebugSettings.Builder(context)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .build()
            paramsBuilder.setConsentDebugSettings(debugSettings)
        }

        val params = paramsBuilder.build()

        runCatching {
            consentInfo.requestConsentInfoUpdate(
                activity,
                params,
                {
                    syncFromSdk(clearError = true)
                    // Do not show ad consent modal to verified Pro users; allow non-Pro users to answer if required.
                    if (!entitlementProvider.isEnabled(FeatureCapability.AD_FREE) && !activity.isFinishing) {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                Timber.tag(TAG).w(
                                    "UMP form error code=%d msg=%s",
                                    formError.errorCode,
                                    formError.message,
                                )
                                _lastErrorMessage.value = formError.message
                            }
                            syncFromSdk(clearError = formError == null)
                            onCompleted()
                        }
                    } else {
                        onCompleted()
                    }
                },
                { requestError ->
                    Timber.tag(TAG).w(
                        "UMP requestConsentInfoUpdate failed code=%d msg=%s",
                        requestError.errorCode,
                        requestError.message,
                    )
                    _status.value = ConsentStatus.ERROR
                    _lastErrorMessage.value = requestError.message
                    // Retain cached canRequestAds() cleanly; offline users remain unaffected.
                    syncFromSdk(clearError = false, preserveErrorStatus = true)
                    onCompleted()
                },
            )
        }.onFailure { err ->
            Timber.tag(TAG).w(err, "UMP initialization failure; offline playback remains unaffected")
            _status.value = ConsentStatus.ERROR
            _lastErrorMessage.value = err.message ?: "Consent SDK unavailable"
            onCompleted()
        }
    }

    override fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: (String?) -> Unit,
    ) {
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                if (formError != null) {
                    Timber.tag(TAG).w(
                        "UMP privacy options error code=%d msg=%s",
                        formError.errorCode,
                        formError.message,
                    )
                    _lastErrorMessage.value = formError.message
                }
                syncFromSdk(clearError = formError == null)
                onDismissed(formError?.message)
            }
        }.onFailure { err ->
            val msg = err.message ?: "Unable to open privacy options"
            _lastErrorMessage.value = msg
            onDismissed(msg)
        }
    }

    fun syncScopeForEntitlement() {
        syncFromSdk(clearError = false)
    }

    private fun syncFromSdk(
        clearError: Boolean,
        preserveErrorStatus: Boolean = false,
    ) {
        if (clearError) {
            _lastErrorMessage.value = null
        }
        if (!preserveErrorStatus) {
            _status.value = mapConsentStatus(consentInfo.consentStatus)
        }
        val canAds = consentInfo.canRequestAds()
        _canRequestAds.value = canAds
        _isPrivacyOptionsRequired.value =
            consentInfo.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        _activeScope.value = computeActiveScope(canAds)
    }

    @Suppress("DEPRECATION")
    private fun computeActiveScope(canAds: Boolean = consentInfo.canRequestAds()): ConsentScope {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val gdprApplies = prefs.getInt(KEY_IAB_GDPR_APPLIES, -1)
        val purposeConsents = prefs.getString(KEY_IAB_PURPOSE_CONSENTS, null)
        return ConsentScopeResolver.resolveScope(
            canRequestAds = canAds,
            gdprApplies = gdprApplies,
            purposeConsents = purposeConsents,
            adFreePro = entitlementProvider.isEnabled(FeatureCapability.AD_FREE),
        )
    }

    private fun mapConsentStatus(sdkStatus: Int): ConsentStatus = when (sdkStatus) {
        ConsentInformation.ConsentStatus.REQUIRED -> ConsentStatus.REQUIRED
        ConsentInformation.ConsentStatus.OBTAINED -> ConsentStatus.OBTAINED
        ConsentInformation.ConsentStatus.NOT_REQUIRED -> ConsentStatus.NOT_REQUIRED
        else -> ConsentStatus.NOT_REQUIRED
    }

    private companion object {
        const val TAG = "UmpConsentManager"
        const val KEY_IAB_GDPR_APPLIES = "IABTCF_gdprApplies"
        const val KEY_IAB_PURPOSE_CONSENTS = "IABTCF_PurposeConsents"
    }
}

