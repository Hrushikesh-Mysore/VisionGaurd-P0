// Pure Kotlin proximity policy with hysteresis and approximate distance estimation.
// Zero Android framework imports to guarantee standalone JVM testability.
package com.visionguard.policy

enum class ProximityState {
    NO_FACE_DETECTED,
    NORMAL_DISTANCE,
    TOO_CLOSE
}

/**
 * Evaluates face dimensions against proximity thresholds with hysteresis.
 *
 * Distance calibration note:
 * Distance is approximate (pinhole model: distance ≈ K / widthFraction).
 * On standard front camera FOV (~75°):
 * - widthFraction >= 0.60 corresponds to roughly 20 cm or closer (TOO CLOSE).
 * - widthFraction <= 0.45 corresponds to roughly 30 cm or farther (RECOVERED).
 * Hysteresis between 0.45 and 0.60 prevents screen flickering around the boundary.
 */
class ProximityEstimator(
    val triggerThresholdFraction: Float = DEFAULT_TRIGGER_THRESHOLD_FRACTION,
    val recoveryThresholdFraction: Float = DEFAULT_RECOVERY_THRESHOLD_FRACTION
) {
    companion object {
        // ~20 cm: face fills 60% of upright frame width
        const val DEFAULT_TRIGGER_THRESHOLD_FRACTION = 0.60f
        // ~30 cm: face fills 45% of upright frame width
        const val DEFAULT_RECOVERY_THRESHOLD_FRACTION = 0.45f

        // Estimated optical constant (K ≈ 20 cm * 0.60 ≈ 12.0)
        private const val APPROX_K = 12.0f

        fun calculateWidthFraction(boundingBoxWidth: Int, uprightImageWidth: Int): Float {
            if (uprightImageWidth <= 0 || boundingBoxWidth <= 0) return 0f
            return boundingBoxWidth.toFloat() / uprightImageWidth.toFloat()
        }

        fun estimateDistanceCm(widthFraction: Float): Float? {
            if (widthFraction <= 0.01f) return null
            return APPROX_K / widthFraction
        }
    }

    var isCurrentlyTooClose: Boolean = false
        private set

    fun evaluate(faceDetected: Boolean, widthFraction: Float): ProximityState {
        if (!faceDetected || widthFraction <= 0f) {
            // No face detected: system must never pretend to know distance or remain in too-close state
            isCurrentlyTooClose = false
            return ProximityState.NO_FACE_DETECTED
        }

        isCurrentlyTooClose = when {
            isCurrentlyTooClose && widthFraction < recoveryThresholdFraction -> false
            !isCurrentlyTooClose && widthFraction >= triggerThresholdFraction -> true
            else -> isCurrentlyTooClose
        }

        return if (isCurrentlyTooClose) ProximityState.TOO_CLOSE else ProximityState.NORMAL_DISTANCE
    }

    fun reset() {
        isCurrentlyTooClose = false
    }
}
