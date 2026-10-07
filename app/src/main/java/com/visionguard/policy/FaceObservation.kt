// Domain abstraction representing a detected face observation per frame.
// Captures geometric properties without retaining raw frames or facial identifiers.
package com.visionguard.policy

data class FaceObservation(
    val widthFraction: Float,
    val yaw: Float,
    val count: Int,
    val timestampMs: Long = 0L
) {
    val isFacePresent: Boolean
        get() = count > 0 && widthFraction > 0f
}
