// Lightweight application dependency container managing singleton instances.
// Holds Room database, overlay manager, usage repository, suggestion engine, profile state, and PIN authentication.
package com.visionguard

import android.content.Context
import android.util.Base64
import com.visionguard.data.AppDatabase
import com.visionguard.data.EyeGuardEventDao
import com.visionguard.data.EyeGuardEventEntity
import com.visionguard.overlay.SpikeOverlayManager
import com.visionguard.policy.EyeGuardDecision
import com.visionguard.policy.EyeGuardPolicy
import com.visionguard.policy.PinAuthPolicy
import com.visionguard.policy.PinVerificationResult
import com.visionguard.policy.PolicyDecision
import com.visionguard.policy.PrivacyGuardDecision
import com.visionguard.policy.ProfileSwitchRecord
import com.visionguard.policy.ProtectionState
import com.visionguard.policy.SuggestionEngine
import com.visionguard.policy.UserProfile
import com.visionguard.usage.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class SpikeMetrics(
    val isServiceRunning: Boolean = false,
    val isPaused: Boolean = false,
    val protectionState: ProtectionState = ProtectionState.NO_FACE_GRACE_PERIOD,
    val shouldDim: Boolean = false,
    val dimOpacity: Float = 0.0f,
    val isPowerSaving: Boolean = false,
    val isFacePresent: Boolean = false,
    val faceCount: Int = 0,
    val widthFraction: Float = 0f,
    val smoothedWidthFraction: Float = 0f,
    val estimatedDistanceCm: Float? = null,
    val rawDistanceCm: Float? = null,
    val consecutiveCloseFrames: Int = 0,
    val calibrationK: Float = EyeGuardPolicy.DEFAULT_CALIBRATION_K,
    val targetThresholdCm: Float = EyeGuardPolicy.DEFAULT_TOO_CLOSE_CM,
    val isCameraBound: Boolean = false,
    // Phase 2 Privacy Guard metrics:
    val isPrivacyGuardEnabled: Boolean = true,
    val isPrivacyAlertActive: Boolean = false,
    val isPrivacyDismissed: Boolean = false,
    val secondaryViewerCount: Int = 0,
    val secondaryConsecutiveFrames: Int = 0,
    // Phase 4 Profile metrics:
    val activeProfile: UserProfile = UserProfile.PARENT
) {
    val isTooClose: Boolean
        get() = protectionState == ProtectionState.TOO_CLOSE

    val isNoFaceDimmed: Boolean
        get() = protectionState == ProtectionState.NO_FACE_DIMMED

    val isChildProfile: Boolean
        get() = activeProfile == UserProfile.CHILD
}

class AppContainer(private val appContext: Context) {

    private val containerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = appContext.getSharedPreferences("visionguard_security_prefs", Context.MODE_PRIVATE)

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    val eventDao: EyeGuardEventDao by lazy {
        database.eyeGuardEventDao()
    }

    val overlayManager: SpikeOverlayManager by lazy {
        SpikeOverlayManager(appContext)
    }

    val usageRepository: UsageRepository by lazy {
        UsageRepository(appContext)
    }

    val suggestionEngine: SuggestionEngine by lazy {
        SuggestionEngine()
    }

    val pinAuthPolicy: PinAuthPolicy by lazy {
        PinAuthPolicy()
    }

    // Active Profile State (Application-level profile)
    private val _activeProfile = MutableStateFlow(
        try {
            UserProfile.valueOf(prefs.getString("active_profile", UserProfile.PARENT.name) ?: UserProfile.PARENT.name)
        } catch (e: Exception) {
            UserProfile.PARENT
        }
    )
    val activeProfile: StateFlow<UserProfile> = _activeProfile.asStateFlow()

    // Profile switch timestamps recorded today for usage attribution
    private val _profileSwitchRecords = mutableListOf<ProfileSwitchRecord>()
    val profileSwitchRecords: List<ProfileSwitchRecord>
        get() = synchronized(_profileSwitchRecords) { _profileSwitchRecords.toList() }

    // Consent States
    private val _hasCameraConsent = MutableStateFlow(prefs.getBoolean("camera_consent_granted", false))
    val hasCameraConsent: StateFlow<Boolean> = _hasCameraConsent.asStateFlow()

    private val _hasChildParentalConsent = MutableStateFlow(prefs.getBoolean("child_parental_consent_granted", false))
    val hasChildParentalConsent: StateFlow<Boolean> = _hasChildParentalConsent.asStateFlow()

    private val _calibrationK = MutableStateFlow(EyeGuardPolicy.DEFAULT_CALIBRATION_K)
    val calibrationK: StateFlow<Float> = _calibrationK.asStateFlow()

    private val _targetThresholdCm = MutableStateFlow(
        if (_activeProfile.value == UserProfile.CHILD) CHILD_TOO_CLOSE_CM else EyeGuardPolicy.DEFAULT_TOO_CLOSE_CM
    )
    val targetThresholdCm: StateFlow<Float> = _targetThresholdCm.asStateFlow()

    private val _isPrivacyGuardEnabled = MutableStateFlow(true)
    val isPrivacyGuardEnabled: StateFlow<Boolean> = _isPrivacyGuardEnabled.asStateFlow()

    private val _dailyGoalHours = MutableStateFlow(
        if (_activeProfile.value == UserProfile.CHILD) CHILD_DAILY_GOAL_HOURS else PARENT_DAILY_GOAL_HOURS
    )
    val dailyGoalHours: StateFlow<Float> = _dailyGoalHours.asStateFlow()

    // FLAG_SECURE setting: Off by default for pitch recording and screenshots
    private val _isSecureModeEnabled = MutableStateFlow(false)
    val isSecureModeEnabled: StateFlow<Boolean> = _isSecureModeEnabled.asStateFlow()

    private val _spikeMetrics = MutableStateFlow(
        SpikeMetrics(
            activeProfile = _activeProfile.value,
            targetThresholdCm = _targetThresholdCm.value
        )
    )
    val spikeMetrics: StateFlow<SpikeMetrics> = _spikeMetrics.asStateFlow()

    init {
        // Seed initial profile switch record for today
        synchronized(_profileSwitchRecords) {
            _profileSwitchRecords.add(ProfileSwitchRecord(System.currentTimeMillis(), _activeProfile.value))
        }
    }

    // --- Profile & PIN Management ---

    fun isParentPinSet(): Boolean {
        return prefs.getString("parent_pin_hash", null) != null &&
                prefs.getString("parent_pin_salt", null) != null
    }

    fun setParentPin(pin: String) {
        val salt = PinAuthPolicy.generateSalt()
        val hash = PinAuthPolicy.hashPin(pin, salt)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)

        prefs.edit()
            .putString("parent_pin_salt", saltB64)
            .putString("parent_pin_hash", hashB64)
            .apply()

        pinAuthPolicy.resetLockout()
        logEvent("PIN_SET", null, "Parent PIN set (PBKDF2-HMAC-SHA256, 100k iterations)")
    }

    fun verifyParentPin(enteredPin: String): PinVerificationResult {
        val saltB64 = prefs.getString("parent_pin_salt", null)
        val hashB64 = prefs.getString("parent_pin_hash", null)

        if (saltB64 == null || hashB64 == null) {
            return PinVerificationResult.PinNotConfigured
        }

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val hash = Base64.decode(hashB64, Base64.NO_WRAP)

        val result = pinAuthPolicy.verifyPin(enteredPin, salt, hash)
        when (result) {
            is PinVerificationResult.Success -> {
                logEvent("PIN_AUTH_SUCCESS", null, "Parent authentication succeeded")
            }
            is PinVerificationResult.Failed -> {
                logEvent("PIN_AUTH_FAILED", null, "Failed PIN attempt (${result.attemptsRemaining} remaining)")
            }
            is PinVerificationResult.LockedOut -> {
                logEvent("PIN_LOCKOUT", null, "PIN authentication locked out (${result.remainingSeconds}s)")
            }
            else -> {}
        }
        return result
    }

    fun switchProfile(toProfile: UserProfile) {
        val fromProfile = _activeProfile.value
        if (fromProfile == toProfile) return

        val now = System.currentTimeMillis()
        synchronized(_profileSwitchRecords) {
            _profileSwitchRecords.add(ProfileSwitchRecord(now, toProfile))
        }

        _activeProfile.value = toProfile
        prefs.edit().putString("active_profile", toProfile.name).apply()

        // Apply profile-specific limits and settings
        if (toProfile == UserProfile.CHILD) {
            _targetThresholdCm.value = CHILD_TOO_CLOSE_CM
            _dailyGoalHours.value = CHILD_DAILY_GOAL_HOURS
            _isPrivacyGuardEnabled.value = true // Forced on for Child
        } else {
            _targetThresholdCm.value = PARENT_TOO_CLOSE_CM
            _dailyGoalHours.value = PARENT_DAILY_GOAL_HOURS
        }

        _spikeMetrics.value = _spikeMetrics.value.copy(
            activeProfile = toProfile,
            targetThresholdCm = _targetThresholdCm.value,
            isPrivacyGuardEnabled = _isPrivacyGuardEnabled.value
        )

        logEvent("PROFILE_SWITCH", null, "Profile switched from $fromProfile to $toProfile")
    }

    fun grantCameraConsent() {
        _hasCameraConsent.value = true
        prefs.edit().putBoolean("camera_consent_granted", true).apply()
        logEvent("CONSENT_GRANTED", null, "Camera on-device processing consent granted")
    }

    fun grantChildParentalConsent() {
        _hasChildParentalConsent.value = true
        prefs.edit().putBoolean("child_parental_consent_granted", true).apply()
        logEvent("CONSENT_GRANTED", null, "Child profile parental consent granted")
    }

    // --- Thresholds & Calibration ---

    fun updateCalibrationK(newK: Float) {
        val clampedK = newK.coerceIn(5.0f, 30.0f)
        _calibrationK.value = clampedK
        _spikeMetrics.value = _spikeMetrics.value.copy(calibrationK = clampedK)
        logEvent("CALIBRATION", 30.0f, "Calibrated K updated to %.2f".format(clampedK))
    }

    fun updateTargetThresholdCm(thresholdCm: Float) {
        val clamped = thresholdCm.coerceIn(15.0f, 40.0f)
        _targetThresholdCm.value = clamped
        _spikeMetrics.value = _spikeMetrics.value.copy(targetThresholdCm = clamped)
        logEvent("SETTING", clamped, "Target threshold updated to %.0f cm".format(clamped))
    }

    fun setDailyGoalHours(hours: Float) {
        _dailyGoalHours.value = hours.coerceIn(1.0f, 12.0f)
    }

    fun setSecureModeEnabled(enabled: Boolean) {
        _isSecureModeEnabled.value = enabled
    }

    fun setPrivacyGuardEnabled(enabled: Boolean) {
        _isPrivacyGuardEnabled.value = enabled
        _spikeMetrics.value = _spikeMetrics.value.copy(
            isPrivacyGuardEnabled = enabled,
            isPrivacyAlertActive = if (!enabled) false else _spikeMetrics.value.isPrivacyAlertActive
        )
        if (!enabled) {
            if (_spikeMetrics.value.isPrivacyAlertActive) {
                overlayManager.updateGuards(
                    isPrivacyActive = false,
                    shouldDim = _spikeMetrics.value.shouldDim,
                    dimOpacity = _spikeMetrics.value.dimOpacity
                )
            }
        }
        logEvent("PRIVACY_TOGGLED", null, if (enabled) "Privacy Guard enabled" else "Privacy Guard disabled")
    }

    fun getStartOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun logEvent(eventType: String, distanceCm: Float?, detail: String) {
        containerScope.launch {
            try {
                eventDao.insert(
                    EyeGuardEventEntity(
                        timestamp = System.currentTimeMillis(),
                        eventType = eventType,
                        distanceCm = distanceCm,
                        detail = detail
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateServiceRunning(isRunning: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(
            isServiceRunning = isRunning,
            isPaused = if (!isRunning) false else _spikeMetrics.value.isPaused,
            protectionState = if (isRunning) _spikeMetrics.value.protectionState else ProtectionState.NO_FACE_GRACE_PERIOD,
            shouldDim = if (isRunning) _spikeMetrics.value.shouldDim else false,
            dimOpacity = if (isRunning) _spikeMetrics.value.dimOpacity else 0.0f,
            isPowerSaving = if (isRunning) _spikeMetrics.value.isPowerSaving else false,
            isFacePresent = if (isRunning) _spikeMetrics.value.isFacePresent else false,
            faceCount = if (isRunning) _spikeMetrics.value.faceCount else 0,
            widthFraction = if (isRunning) _spikeMetrics.value.widthFraction else 0f,
            smoothedWidthFraction = if (isRunning) _spikeMetrics.value.smoothedWidthFraction else 0f,
            estimatedDistanceCm = if (isRunning) _spikeMetrics.value.estimatedDistanceCm else null,
            rawDistanceCm = if (isRunning) _spikeMetrics.value.rawDistanceCm else null,
            consecutiveCloseFrames = if (isRunning) _spikeMetrics.value.consecutiveCloseFrames else 0,
            isCameraBound = if (isRunning) _spikeMetrics.value.isCameraBound else false,
            isPrivacyAlertActive = if (isRunning) _spikeMetrics.value.isPrivacyAlertActive else false,
            secondaryViewerCount = if (isRunning) _spikeMetrics.value.secondaryViewerCount else 0,
            secondaryConsecutiveFrames = if (isRunning) _spikeMetrics.value.secondaryConsecutiveFrames else 0,
            activeProfile = _activeProfile.value
        )
    }

    fun updatePaused(isPaused: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(
            isPaused = isPaused,
            shouldDim = if (isPaused) false else _spikeMetrics.value.shouldDim,
            dimOpacity = if (isPaused) 0.0f else _spikeMetrics.value.dimOpacity,
            isPrivacyAlertActive = if (isPaused) false else _spikeMetrics.value.isPrivacyAlertActive
        )
    }

    fun updateCameraBound(isBound: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(isCameraBound = isBound)
    }

    fun updateEyeGuardDecision(decision: EyeGuardDecision, faceCount: Int, widthFraction: Float) {
        val paused = _spikeMetrics.value.isPaused
        _spikeMetrics.value = _spikeMetrics.value.copy(
            protectionState = decision.state,
            shouldDim = decision.shouldDim && !paused,
            dimOpacity = if (paused) 0.0f else decision.dimOpacity,
            isPowerSaving = decision.isPowerSaving && !paused,
            isFacePresent = decision.isFacePresent,
            faceCount = faceCount,
            widthFraction = widthFraction,
            smoothedWidthFraction = decision.smoothedWidthFraction,
            estimatedDistanceCm = decision.smoothedDistanceCm,
            rawDistanceCm = decision.rawDistanceCm,
            consecutiveCloseFrames = decision.consecutiveCloseFrames
        )
    }

    fun updatePrivacyDecision(decision: PrivacyGuardDecision) {
        val paused = _spikeMetrics.value.isPaused
        val enabled = _isPrivacyGuardEnabled.value
        _spikeMetrics.value = _spikeMetrics.value.copy(
            isPrivacyAlertActive = decision.isTriggered && !paused && enabled,
            isPrivacyDismissed = decision.isDismissed,
            secondaryViewerCount = decision.qualifyingViewerCount,
            secondaryConsecutiveFrames = decision.consecutiveFrames
        )
    }

    fun updatePolicyDecision(decision: PolicyDecision, faceCount: Int, widthFraction: Float) {
        val paused = _spikeMetrics.value.isPaused
        _spikeMetrics.value = _spikeMetrics.value.copy(
            protectionState = decision.state,
            shouldDim = decision.shouldDim && !paused,
            dimOpacity = if (decision.shouldDim && !paused) 0.50f else 0.0f,
            isPowerSaving = decision.isPowerSaving && !paused,
            isFacePresent = decision.isFacePresent,
            faceCount = faceCount,
            widthFraction = widthFraction,
            estimatedDistanceCm = decision.estimatedDistanceCm
        )
    }

    companion object {
        const val PARENT_DAILY_GOAL_HOURS = 4.0f
        const val CHILD_DAILY_GOAL_HOURS = 2.0f
        const val PARENT_TOO_CLOSE_CM = 20.0f
        const val CHILD_TOO_CLOSE_CM = 25.0f
    }
}
