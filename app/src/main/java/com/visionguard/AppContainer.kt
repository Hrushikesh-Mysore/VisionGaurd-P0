// Lightweight application dependency container managing singleton instances.
// Holds Room database, overlay manager, usage statistics repository, suggestion engine, and metrics.
package com.visionguard

import android.content.Context
import com.visionguard.data.AppDatabase
import com.visionguard.data.EyeGuardEventDao
import com.visionguard.data.EyeGuardEventEntity
import com.visionguard.overlay.SpikeOverlayManager
import com.visionguard.policy.EyeGuardDecision
import com.visionguard.policy.EyeGuardPolicy
import com.visionguard.policy.PolicyDecision
import com.visionguard.policy.PrivacyGuardDecision
import com.visionguard.policy.ProtectionState
import com.visionguard.policy.SuggestionEngine
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
    val secondaryConsecutiveFrames: Int = 0
) {
    val isTooClose: Boolean
        get() = protectionState == ProtectionState.TOO_CLOSE

    val isNoFaceDimmed: Boolean
        get() = protectionState == ProtectionState.NO_FACE_DIMMED
}

class AppContainer(private val appContext: Context) {

    private val containerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    private val _calibrationK = MutableStateFlow(EyeGuardPolicy.DEFAULT_CALIBRATION_K)
    val calibrationK: StateFlow<Float> = _calibrationK.asStateFlow()

    private val _targetThresholdCm = MutableStateFlow(EyeGuardPolicy.DEFAULT_TOO_CLOSE_CM)
    val targetThresholdCm: StateFlow<Float> = _targetThresholdCm.asStateFlow()

    private val _isPrivacyGuardEnabled = MutableStateFlow(true)
    val isPrivacyGuardEnabled: StateFlow<Boolean> = _isPrivacyGuardEnabled.asStateFlow()

    private val _dailyGoalHours = MutableStateFlow(4.0f)
    val dailyGoalHours: StateFlow<Float> = _dailyGoalHours.asStateFlow()

    // FLAG_SECURE setting: Off by default for pitch recording and screenshots
    private val _isSecureModeEnabled = MutableStateFlow(false)
    val isSecureModeEnabled: StateFlow<Boolean> = _isSecureModeEnabled.asStateFlow()

    private val _spikeMetrics = MutableStateFlow(SpikeMetrics())
    val spikeMetrics: StateFlow<SpikeMetrics> = _spikeMetrics.asStateFlow()

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
            secondaryConsecutiveFrames = if (isRunning) _spikeMetrics.value.secondaryConsecutiveFrames else 0
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
}
