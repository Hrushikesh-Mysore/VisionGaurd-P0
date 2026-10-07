// Lightweight application dependency container managing singleton instances.
// Avoids heavy dependency injection frameworks to keep build times fast and explainability high.
package com.visionguard

import android.content.Context
import com.visionguard.overlay.SpikeOverlayManager
import com.visionguard.policy.PolicyDecision
import com.visionguard.policy.ProtectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpikeMetrics(
    val isServiceRunning: Boolean = false,
    val isPaused: Boolean = false,
    val protectionState: ProtectionState = ProtectionState.NO_FACE_GRACE_PERIOD,
    val shouldDim: Boolean = false,
    val isPowerSaving: Boolean = false,
    val isFacePresent: Boolean = false,
    val faceCount: Int = 0,
    val widthFraction: Float = 0f,
    val estimatedDistanceCm: Float? = null,
    val isCameraBound: Boolean = false
) {
    val isTooClose: Boolean
        get() = protectionState == ProtectionState.TOO_CLOSE

    val isNoFaceDimmed: Boolean
        get() = protectionState == ProtectionState.NO_FACE_DIMMED
}

class AppContainer(private val appContext: Context) {

    val overlayManager: SpikeOverlayManager by lazy {
        SpikeOverlayManager(appContext)
    }

    private val _spikeMetrics = MutableStateFlow(SpikeMetrics())
    val spikeMetrics: StateFlow<SpikeMetrics> = _spikeMetrics.asStateFlow()

    fun updateServiceRunning(isRunning: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(
            isServiceRunning = isRunning,
            isPaused = if (!isRunning) false else _spikeMetrics.value.isPaused,
            protectionState = if (isRunning) _spikeMetrics.value.protectionState else ProtectionState.NO_FACE_GRACE_PERIOD,
            shouldDim = if (isRunning) _spikeMetrics.value.shouldDim else false,
            isPowerSaving = if (isRunning) _spikeMetrics.value.isPowerSaving else false,
            isFacePresent = if (isRunning) _spikeMetrics.value.isFacePresent else false,
            faceCount = if (isRunning) _spikeMetrics.value.faceCount else 0,
            widthFraction = if (isRunning) _spikeMetrics.value.widthFraction else 0f,
            estimatedDistanceCm = if (isRunning) _spikeMetrics.value.estimatedDistanceCm else null,
            isCameraBound = if (isRunning) _spikeMetrics.value.isCameraBound else false
        )
    }

    fun updatePaused(isPaused: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(
            isPaused = isPaused,
            shouldDim = if (isPaused) false else _spikeMetrics.value.shouldDim
        )
    }

    fun updateCameraBound(isBound: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(isCameraBound = isBound)
    }

    fun updatePolicyDecision(decision: PolicyDecision, faceCount: Int, widthFraction: Float) {
        _spikeMetrics.value = _spikeMetrics.value.copy(
            protectionState = decision.state,
            shouldDim = decision.shouldDim && !_spikeMetrics.value.isPaused,
            isPowerSaving = decision.isPowerSaving && !_spikeMetrics.value.isPaused,
            isFacePresent = decision.isFacePresent,
            faceCount = faceCount,
            widthFraction = widthFraction,
            estimatedDistanceCm = decision.estimatedDistanceCm
        )
    }
}
