// Pure Kotlin Eye Guard distance estimation and protection policy.
// Computes pinhole distance, EMA smoothing, N-frame confirmation, hysteresis,
// and closeness-proportional dimming opacity with zero Android dependencies.
package com.visionguard.policy

enum class ProtectionState {
    NORMAL_DISTANCE,       // Face detected at safe distance (no dim)
    TOO_CLOSE,             // Face detected within proximity threshold (dim ON)
    NO_FACE_GRACE_PERIOD,  // No face detected for < 5 seconds (no dim, avoids flicker)
    NO_FACE_DIMMED         // No face detected for >= 5 seconds (dim ON, 1 fps idle poll)
}

data class EyeGuardDecision(
    val state: ProtectionState,
    val shouldDim: Boolean,
    val dimOpacity: Float,
    val isPowerSaving: Boolean,
    val isFacePresent: Boolean,
    val rawDistanceCm: Float?,
    val smoothedDistanceCm: Float?,
    val smoothedWidthFraction: Float,
    val consecutiveCloseFrames: Int
)

/**
 * EyeGuardPolicy combines pinhole geometry, user calibration, EMA smoothing,
 * consecutive frame confirmation, and hysteresis.
 */
class EyeGuardPolicy(
    var calibrationK: Float = DEFAULT_CALIBRATION_K,
    var tooCloseThresholdCm: Float = DEFAULT_TOO_CLOSE_CM,
    var recoveryMarginCm: Float = DEFAULT_RECOVERY_MARGIN_CM,
    val consecutiveFramesRequired: Int = DEFAULT_CONSECUTIVE_FRAMES,
    val noFaceTimeoutMs: Long = DEFAULT_NO_FACE_TIMEOUT_MS,
    val emaAlpha: Float = DEFAULT_EMA_ALPHA,
    private val clock: Clock? = null
) {
    companion object {
        const val DEFAULT_CALIBRATION_K = 12.0f
        const val DEFAULT_TOO_CLOSE_CM = 20.0f
        const val DEFAULT_RECOVERY_MARGIN_CM = 10.0f
        const val DEFAULT_CONSECUTIVE_FRAMES = 5
        const val DEFAULT_NO_FACE_TIMEOUT_MS = 5000L
        const val DEFAULT_EMA_ALPHA = 0.35f
        const val MAX_OVERLAY_OPACITY = 0.80f
        const val MIN_OVERLAY_OPACITY = 0.45f
        const val DEFAULT_IDLE_DIM_OPACITY = 0.50f

        fun calculateWidthFraction(boundingBoxWidth: Int, uprightImageWidth: Int): Float {
            if (uprightImageWidth <= 0 || boundingBoxWidth <= 0) return 0f
            return boundingBoxWidth.toFloat() / uprightImageWidth.toFloat()
        }

        fun estimateDistanceCm(widthFraction: Float, k: Float = DEFAULT_CALIBRATION_K): Float? {
            if (widthFraction <= 0.01f) return null
            return (k / widthFraction).coerceIn(1.0f, 200.0f)
        }

        fun calculateCalibratedK(referenceDistanceCm: Float, observedWidthFraction: Float): Float {
            if (observedWidthFraction <= 0.01f || referenceDistanceCm <= 0f) return DEFAULT_CALIBRATION_K
            return referenceDistanceCm * observedWidthFraction
        }
    }

    private var isProximityTooClose: Boolean = false
    private var smoothedWidthFraction: Float = 0f
    private var consecutiveCloseFrames: Int = 0
    private var consecutiveRecoverFrames: Int = 0
    private var lastFaceSeenTimeMs: Long = 0L
    private var hasSeenFirstFrame: Boolean = false

    val recoveryThresholdCm: Float
        get() = tooCloseThresholdCm + recoveryMarginCm

    fun evaluate(
        observation: FaceObservation,
        currentTimeMs: Long = clock?.elapsedRealtime() ?: observation.timestampMs
    ): EyeGuardDecision {
        if (!hasSeenFirstFrame) {
            hasSeenFirstFrame = true
            lastFaceSeenTimeMs = currentTimeMs
        }

        if (observation.isFacePresent) {
            lastFaceSeenTimeMs = currentTimeMs

            // Update Exponential Moving Average (EMA)
            smoothedWidthFraction = if (smoothedWidthFraction <= 0f) {
                observation.widthFraction
            } else {
                (emaAlpha * observation.widthFraction) + ((1f - emaAlpha) * smoothedWidthFraction)
            }

            val rawDistance = estimateDistanceCm(observation.widthFraction, calibrationK)
            val smoothedDistance = estimateDistanceCm(smoothedWidthFraction, calibrationK) ?: 100f

            // Frame-confirmation & Hysteresis state machine
            if (smoothedDistance <= tooCloseThresholdCm) {
                consecutiveCloseFrames++
                consecutiveRecoverFrames = 0
                if (consecutiveCloseFrames >= consecutiveFramesRequired) {
                    isProximityTooClose = true
                }
            } else if (smoothedDistance >= recoveryThresholdCm) {
                consecutiveRecoverFrames++
                consecutiveCloseFrames = 0
                // Recover when confirmed above recovery threshold
                if (consecutiveRecoverFrames >= 2 || !isProximityTooClose) {
                    isProximityTooClose = false
                }
            } else {
                // In between tooCloseThresholdCm and recoveryThresholdCm: retain hysteresis state
                consecutiveCloseFrames = 0
                consecutiveRecoverFrames = 0
            }

            val state = if (isProximityTooClose) ProtectionState.TOO_CLOSE else ProtectionState.NORMAL_DISTANCE
            val opacity = if (isProximityTooClose) {
                calculateProportionalOpacity(smoothedDistance)
            } else {
                0.0f
            }

            return EyeGuardDecision(
                state = state,
                shouldDim = isProximityTooClose,
                dimOpacity = opacity,
                isPowerSaving = false,
                isFacePresent = true,
                rawDistanceCm = rawDistance,
                smoothedDistanceCm = smoothedDistance,
                smoothedWidthFraction = smoothedWidthFraction,
                consecutiveCloseFrames = consecutiveCloseFrames
            )
        } else {
            // No face present
            isProximityTooClose = false
            consecutiveCloseFrames = 0
            consecutiveRecoverFrames = 0
            smoothedWidthFraction = 0f

            val elapsedNoFaceMs = currentTimeMs - lastFaceSeenTimeMs

            return if (elapsedNoFaceMs >= noFaceTimeoutMs) {
                // 5s continuous no-face timeout: dim overlay and enter 1 fps low-power idle polling
                EyeGuardDecision(
                    state = ProtectionState.NO_FACE_DIMMED,
                    shouldDim = true,
                    dimOpacity = DEFAULT_IDLE_DIM_OPACITY,
                    isPowerSaving = true,
                    isFacePresent = false,
                    rawDistanceCm = null,
                    smoothedDistanceCm = null,
                    smoothedWidthFraction = 0f,
                    consecutiveCloseFrames = 0
                )
            } else {
                // Grace period avoids single-frame drops flickering
                EyeGuardDecision(
                    state = ProtectionState.NO_FACE_GRACE_PERIOD,
                    shouldDim = false,
                    dimOpacity = 0.0f,
                    isPowerSaving = false,
                    isFacePresent = false,
                    rawDistanceCm = null,
                    smoothedDistanceCm = null,
                    smoothedWidthFraction = 0f,
                    consecutiveCloseFrames = 0
                )
            }
        }
    }

    private fun calculateProportionalOpacity(distanceCm: Float): Float {
        val minDistance = 12.0f
        val closeness = (tooCloseThresholdCm - distanceCm).coerceAtLeast(0f)
        val range = (tooCloseThresholdCm - minDistance).coerceAtLeast(1.0f)
        val fraction = (closeness / range).coerceIn(0f, 1f)
        return (MIN_OVERLAY_OPACITY + (MAX_OVERLAY_OPACITY - MIN_OVERLAY_OPACITY) * fraction)
            .coerceIn(MIN_OVERLAY_OPACITY, MAX_OVERLAY_OPACITY)
    }

    fun reset(currentTimeMs: Long = 0L) {
        isProximityTooClose = false
        smoothedWidthFraction = 0f
        consecutiveCloseFrames = 0
        consecutiveRecoverFrames = 0
        lastFaceSeenTimeMs = currentTimeMs
        hasSeenFirstFrame = false
    }
}
