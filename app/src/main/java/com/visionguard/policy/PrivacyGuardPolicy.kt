// Pure Kotlin Privacy Guard policy detecting secondary viewers (shoulder surfing).
// Evaluates multi-face geometry, N=3 frame confirmation, 2s absence clearance, and zero Android imports.
package com.visionguard.policy

import kotlin.math.abs

data class PrivacyGuardDecision(
    val isTriggered: Boolean,
    val qualifyingViewerCount: Int,
    val consecutiveFrames: Int,
    val isDismissed: Boolean,
    val mostProminentViewer: DetectedFace? = null
)

class PrivacyGuardPolicy(
    var minSecondaryWidthFraction: Float = DEFAULT_MIN_SECONDARY_WIDTH_FRACTION,
    var maxYawAngleDegrees: Float = DEFAULT_MAX_YAW_DEGREES,
    val consecutiveFramesRequired: Int = DEFAULT_CONSECUTIVE_FRAMES,
    val absenceTimeoutMs: Long = DEFAULT_ABSENCE_TIMEOUT_MS,
    private val clock: Clock? = null
) {
    companion object {
        const val DEFAULT_MIN_SECONDARY_WIDTH_FRACTION = 0.10f
        const val DEFAULT_MAX_YAW_DEGREES = 35.0f
        const val DEFAULT_CONSECUTIVE_FRAMES = 3
        const val DEFAULT_ABSENCE_TIMEOUT_MS = 2000L
    }

    private var isTriggered: Boolean = false
    private var isDismissed: Boolean = false
    private var consecutiveFrames: Int = 0
    private var lastSecondaryFaceSeenTimeMs: Long = 0L

    fun evaluate(
        observation: FaceObservation,
        isEnabled: Boolean = true,
        currentTimeMs: Long = clock?.elapsedRealtime() ?: observation.timestampMs
    ): PrivacyGuardDecision {
        if (!isEnabled) {
            reset(currentTimeMs)
            return PrivacyGuardDecision(
                isTriggered = false,
                qualifyingViewerCount = 0,
                consecutiveFrames = 0,
                isDismissed = false,
                mostProminentViewer = null
            )
        }

        // Find qualifying secondary viewers facing the screen with sufficient size
        val qualifyingViewers = observation.secondaryFaces.filter { face ->
            face.widthFraction >= minSecondaryWidthFraction && abs(face.yaw) < maxYawAngleDegrees
        }

        val hasQualifyingViewer = qualifyingViewers.isNotEmpty()
        val mostProminent = qualifyingViewers.maxByOrNull { it.widthFraction }

        if (hasQualifyingViewer) {
            lastSecondaryFaceSeenTimeMs = currentTimeMs

            if (!isDismissed && !isTriggered) {
                consecutiveFrames++
                if (consecutiveFrames >= consecutiveFramesRequired) {
                    isTriggered = true
                }
            }
        } else {
            consecutiveFrames = 0

            val elapsedSinceLastSeen = currentTimeMs - lastSecondaryFaceSeenTimeMs
            if (elapsedSinceLastSeen >= absenceTimeoutMs) {
                // Secondary viewer absent for >= 2 seconds: clear alert and reset dismissal
                isTriggered = false
                isDismissed = false
            }
        }

        return PrivacyGuardDecision(
            isTriggered = isTriggered,
            qualifyingViewerCount = qualifyingViewers.size,
            consecutiveFrames = consecutiveFrames,
            isDismissed = isDismissed,
            mostProminentViewer = mostProminent
        )
    }

    fun dismiss() {
        isDismissed = true
        isTriggered = false
        consecutiveFrames = 0
    }

    fun reset(currentTimeMs: Long = 0L) {
        isTriggered = false
        isDismissed = false
        consecutiveFrames = 0
        lastSecondaryFaceSeenTimeMs = currentTimeMs
    }
}
