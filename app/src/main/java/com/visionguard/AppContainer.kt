// Lightweight application dependency container managing singleton instances.
// Avoids heavy dependency injection frameworks to keep build times fast and explainability high.
package com.visionguard

import android.content.Context
import com.visionguard.overlay.SpikeOverlayManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpikeMetrics(
    val isServiceRunning: Boolean = false,
    val faceCount: Int = 0,
    val widthFraction: Float = 0f,
    val isTooClose: Boolean = false,
    val isCameraBound: Boolean = false
)

class AppContainer(private val appContext: Context) {

    val overlayManager: SpikeOverlayManager by lazy {
        SpikeOverlayManager(appContext)
    }

    private val _spikeMetrics = MutableStateFlow(SpikeMetrics())
    val spikeMetrics: StateFlow<SpikeMetrics> = _spikeMetrics.asStateFlow()

    fun updateServiceRunning(isRunning: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(
            isServiceRunning = isRunning,
            faceCount = if (isRunning) _spikeMetrics.value.faceCount else 0,
            widthFraction = if (isRunning) _spikeMetrics.value.widthFraction else 0f,
            isTooClose = if (isRunning) _spikeMetrics.value.isTooClose else false,
            isCameraBound = if (isRunning) _spikeMetrics.value.isCameraBound else false
        )
    }

    fun updateCameraBound(isBound: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(isCameraBound = isBound)
    }

    fun updateDetections(faceCount: Int, widthFraction: Float, isTooClose: Boolean) {
        _spikeMetrics.value = _spikeMetrics.value.copy(
            faceCount = faceCount,
            widthFraction = widthFraction,
            isTooClose = isTooClose
        )
    }
}
