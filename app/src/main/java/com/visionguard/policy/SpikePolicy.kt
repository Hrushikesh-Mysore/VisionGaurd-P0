// Pure Kotlin policy calculation for Phase 0 distance threshold evaluation.
// Decoupled from Android framework classes to ensure complete unit testability.
package com.visionguard.policy

object SpikePolicy {
    const val SPIKE_CLOSE_THRESHOLD_FRACTION = 0.45f

    fun calculateWidthFraction(boundingBoxWidth: Int, uprightImageWidth: Int): Float {
        if (uprightImageWidth <= 0) return 0f
        return boundingBoxWidth.toFloat() / uprightImageWidth.toFloat()
    }

    fun isTooClose(widthFraction: Float, thresholdFraction: Float = SPIKE_CLOSE_THRESHOLD_FRACTION): Boolean {
        return widthFraction >= thresholdFraction
    }
}
