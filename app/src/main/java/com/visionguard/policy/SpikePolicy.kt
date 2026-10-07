// Pure Kotlin proximity and battery-saving protection policy.
// Centralizes all proximity evaluation, hysteresis, and no-face idle dimming logic with zero Android dependencies.
package com.visionguard.policy

enum class ProtectionState {
    NORMAL_DISTANCE,       // Face detected at safe distance (no dim)
    TOO_CLOSE,             // Face detected within ~20 cm proximity threshold (dim ON)
    NO_FACE_GRACE_PERIOD,  // No face detected for < 5 seconds (no dim, avoids single-frame flicker)
    NO_FACE_DIMMED         // No face detected for >= 5 seconds (dim ON for privacy & battery conservation)
}

data class PolicyDecision(
    val state: ProtectionState,
    val shouldDim: Boolean,
    val isPowerSaving: Boolean,
    val isFacePresent: Boolean,
    val estimatedDistanceCm: Float?
)

/**
 * State machine evaluating camera detection frames against proximity thresholds and no-face timeout.
 *
 * Distance calibration note:
 * Distance is approximate based on front-camera optical geometry (d ≈ K / widthFraction, K ≈ 12.0).
 * - widthFraction >= 0.60: ~20 cm or closer (TOO_CLOSE)
 * - widthFraction <= 0.45: ~30 cm or farther (NORMAL_DISTANCE recovery)
 * - No face for >= 5000 ms: NO_FACE_DIMMED (Screen dimmed, 1 fps low-power polling)
 */
class ProtectionPolicy(
    val triggerThresholdFraction: Float = DEFAULT_TRIGGER_THRESHOLD_FRACTION,
    val recoveryThresholdFraction: Float = DEFAULT_RECOVERY_THRESHOLD_FRACTION,
    val noFaceTimeoutMs: Long = DEFAULT_NO_FACE_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_TRIGGER_THRESHOLD_FRACTION = 0.60f
        const val DEFAULT_RECOVERY_THRESHOLD_FRACTION = 0.45f
        const val DEFAULT_NO_FACE_TIMEOUT_MS = 5000L
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

    private var isProximityTooClose: Boolean = false
    private var lastFaceSeenTimeMs: Long = 0L
    private var hasSeenFirstFrame: Boolean = false

    fun evaluate(faceDetected: Boolean, widthFraction: Float, currentTimeMs: Long): PolicyDecision {
        if (!hasSeenFirstFrame) {
            hasSeenFirstFrame = true
            lastFaceSeenTimeMs = currentTimeMs
        }

        if (faceDetected && widthFraction > 0f) {
            lastFaceSeenTimeMs = currentTimeMs

            // Evaluate proximity with hysteresis
            isProximityTooClose = when {
                isProximityTooClose && widthFraction < recoveryThresholdFraction -> false
                !isProximityTooClose && widthFraction >= triggerThresholdFraction -> true
                else -> isProximityTooClose
            }

            val state = if (isProximityTooClose) ProtectionState.TOO_CLOSE else ProtectionState.NORMAL_DISTANCE
            return PolicyDecision(
                state = state,
                shouldDim = isProximityTooClose,
                isPowerSaving = false,
                isFacePresent = true,
                estimatedDistanceCm = estimateDistanceCm(widthFraction)
            )
        } else {
            // No face detected in this frame
            isProximityTooClose = false
            val elapsedNoFaceMs = currentTimeMs - lastFaceSeenTimeMs

            return if (elapsedNoFaceMs >= noFaceTimeoutMs) {
                // Dim screen and enter low-power state after 5 seconds continuous no-face
                PolicyDecision(
                    state = ProtectionState.NO_FACE_DIMMED,
                    shouldDim = true,
                    isPowerSaving = true,
                    isFacePresent = false,
                    estimatedDistanceCm = null
                )
            } else {
                // Grace period: do not dim immediately to prevent flicker on dropped frames
                PolicyDecision(
                    state = ProtectionState.NO_FACE_GRACE_PERIOD,
                    shouldDim = false,
                    isPowerSaving = false,
                    isFacePresent = false,
                    estimatedDistanceCm = null
                )
            }
        }
    }

    fun reset(currentTimeMs: Long = 0L) {
        isProximityTooClose = false
        lastFaceSeenTimeMs = currentTimeMs
        hasSeenFirstFrame = false
    }
}
