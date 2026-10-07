// Lightweight application dependency container managing singleton instances.
// Avoids heavy dependency injection frameworks to keep build times fast and explainability high.
package com.visionguard

import android.content.Context
import com.visionguard.overlay.SpikeOverlayManager
import com.visionguard.policy.ProximityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpikeMetrics(
    val isServiceRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isPowerSaving: Boolean = false,
    val faceCount: Int = 0,
    val widthFraction: Float = 0f,
    val proximityState: ProximityState = ProximityState.NO_FACE_DETECTED,
    val isCameraBound: Boolean = false
) {
    val isTooClose: Boolean
        get() = proximityState == ProximityState.TOO_CLOSE
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
            isPowerSaving = if (!isRunning) false else _spikeMetrics.value.isPowerSaving,
            faceCount = if (isRunning) _spikeMetrics.value.faceCount else 0,
            widthFraction = if (isRunning) _spikeMetrics.value.widthFraction else 0f,
            proximityState = if (isRunning) _spikeMetrics.value.proximityState else ProximityState.NO_FACE_DETECTED,
            isCameraBound = if (isRunning) _spikeMetrics.value.isCameraBound else false
        )
    }

    fun updatePaused(isPaused: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(
            isPaused = isPaused,
            // When paused, clear proximity warning and power saving indicator
            proximityState = if (isPaused) ProximityState.NO_FACE_DETECTED else _spikeMetrics.value.proximityState,
            isPowerSaving = if (isPaused) false else _spikeMetrics.value.isPowerSaving
        )
    }

    fun updatePowerSaving(isPowerSaving: Boolean) {
        if (_spikeMetrics.value.isPowerSaving != isPowerSaving) {
            _spikeMetrics.value = _spikeMetrics.value.copy(isPowerSaving = isPowerSaving)
        }
    }

    fun updateCameraBound(isBound: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(isCameraBound = isBound)
    }

    fun updateDetections(faceCount: Int, widthFraction: Float, proximityState: ProximityState) {
        _spikeMetrics.value = _spikeMetrics.value.copy(
            faceCount = faceCount,
            widthFraction = widthFraction,
            proximityState = proximityState
        )
    }
}
