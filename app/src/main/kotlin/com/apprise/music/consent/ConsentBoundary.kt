package com.apprise.music.consent

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Privacy consent scope resolved from UMP & TCF signals. */
enum class ConsentScope {
    PRIVACY_ONLY,
    PERSONALIZED_ADS,
    NON_PERSONALIZED_ADS,
}

/** Current privacy consent determination state. */
enum class ConsentStatus {
    NOT_REQUIRED,
    REQUIRED,
    OBTAINED,
    ERROR,
}

/**
 * Pure helper interpreting IAB TCF v2 consent strings and UMP flags to distinguish
 * personalized vs non-personalized ad permission cleanly for tests and ad requests.
 */
object ConsentScopeResolver {
    /**
     * Checks TCF v2 `IABTCF_PurposeConsents`:
     * - Empty / null with `gdprApplies != 1`: user outside GDPR/UK/CH or consent obtained via default non-TCF -> [ConsentScope.PERSONALIZED_ADS] if [canRequestAds].
     * - When GDPR applies (`gdprApplies == 1`):
     *   - Purposes 1, 3, 4 granted (`'1'` at indices 0, 2, 3) -> [ConsentScope.PERSONALIZED_ADS].
     *   - Purpose 1 granted (`'1'` at index 0) or non-TCF allowed without 3/4 -> [ConsentScope.NON_PERSONALIZED_ADS].
     *   - Purpose 1 denied or `!canRequestAds` -> [ConsentScope.PRIVACY_ONLY].
     */
    fun resolveScope(
        canRequestAds: Boolean,
        gdprApplies: Int,
        purposeConsents: String?,
        adFreePro: Boolean,
    ): ConsentScope {
        if (adFreePro || !canRequestAds) return ConsentScope.PRIVACY_ONLY
        if (gdprApplies != 1) return ConsentScope.PERSONALIZED_ADS

        val trimmed = purposeConsents.orEmpty()
        if (trimmed.isEmpty()) return ConsentScope.NON_PERSONALIZED_ADS
        val purpose1 = trimmed.getOrNull(0) == '1'
        if (!purpose1) return ConsentScope.PRIVACY_ONLY
        val purpose3 = trimmed.getOrNull(2) == '1'
        val purpose4 = trimmed.getOrNull(3) == '1'
        return if (purpose3 && purpose4) {
            ConsentScope.PERSONALIZED_ADS
        } else {
            ConsentScope.NON_PERSONALIZED_ADS
        }
    }
}

/** Clean boundary for Google UMP privacy consent updates, form display, and privacy options entry. */
interface ConsentManagerBoundary {
    val status: StateFlow<ConsentStatus>
    val activeScope: StateFlow<ConsentScope>
    val canRequestAds: StateFlow<Boolean>
        get() = MutableStateFlow(false).asStateFlow()
    val isPrivacyOptionsRequired: StateFlow<Boolean>
        get() = MutableStateFlow(false).asStateFlow()
    val lastErrorMessage: StateFlow<String?>
        get() = MutableStateFlow<String?>(null).asStateFlow()

    fun refreshConsent(
        activity: Activity,
        debugEeaGeography: Boolean = false,
        onCompleted: () -> Unit = {},
    ) = onCompleted()

    fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: (String?) -> Unit = {},
    ) = onDismissed(null)
}

/** M1 no-op placeholder preserved for isolated boundary tests. */
@Singleton
class NoOpConsentManagerBoundary @Inject constructor() : ConsentManagerBoundary {
    private val _status = MutableStateFlow(ConsentStatus.NOT_REQUIRED)
    private val _scope = MutableStateFlow(ConsentScope.PRIVACY_ONLY)

    override val status: StateFlow<ConsentStatus> = _status.asStateFlow()
    override val activeScope: StateFlow<ConsentScope> = _scope.asStateFlow()
}
