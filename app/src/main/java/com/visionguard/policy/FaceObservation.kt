// Domain abstraction representing detected face observations per frame.
// Captures geometric properties and secondary viewers without retaining raw frames.
package com.visionguard.policy

data class DetectedFace(
    val widthFraction: Float,
    val yaw: Float
)

data class FaceObservation(
    val widthFraction: Float,
    val yaw: Float,
    val count: Int,
    val timestampMs: Long = 0L,
    val secondaryFaces: List<DetectedFace> = emptyList()
) {
    val isFacePresent: Boolean
        get() = count > 0 && widthFraction > 0f
}
