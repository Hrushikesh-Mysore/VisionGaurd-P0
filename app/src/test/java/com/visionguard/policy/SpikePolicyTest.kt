// Unit tests verifying ProximityEstimator hysteresis, boundary states, and no-face handling.
// Executes directly on JVM without Android dependencies.
package com.visionguard.policy

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpikePolicyTest {

    private lateinit var estimator: ProximityEstimator

    @Before
    fun setUp() {
        estimator = ProximityEstimator(
            triggerThresholdFraction = 0.60f,  // ~20 cm
            recoveryThresholdFraction = 0.45f  // ~30 cm
        )
    }

    @Test
    fun calculateWidthFraction_validDimensions_computesAccurately() {
        val fraction = ProximityEstimator.calculateWidthFraction(boundingBoxWidth = 300, uprightImageWidth = 500)
        assertEquals(0.60f, fraction, 0.0001f)
    }

    @Test
    fun calculateWidthFraction_invalidDimensions_returnsZero() {
        assertEquals(0f, ProximityEstimator.calculateWidthFraction(boundingBoxWidth = 100, uprightImageWidth = 0), 0.0001f)
        assertEquals(0f, ProximityEstimator.calculateWidthFraction(boundingBoxWidth = 0, uprightImageWidth = 400), 0.0001f)
        assertEquals(0f, ProximityEstimator.calculateWidthFraction(boundingBoxWidth = -10, uprightImageWidth = 400), 0.0001f)
    }

    @Test
    fun estimateDistanceCm_estimatesReasonably() {
        assertNull(ProximityEstimator.estimateDistanceCm(0.005f))
        val distanceAt20cm = ProximityEstimator.estimateDistanceCm(0.60f)
        assertNotNull(distanceAt20cm)
        assertEquals(20.0f, distanceAt20cm!!, 0.1f)

        val distanceAt30cm = ProximityEstimator.estimateDistanceCm(0.40f)
        assertNotNull(distanceAt30cm)
        assertEquals(30.0f, distanceAt30cm!!, 0.1f)
    }

    @Test
    fun noFaceDetected_alwaysReturnsNoFaceAndClearsTooClose() {
        // Normal to no face
        val state1 = estimator.evaluate(faceDetected = false, widthFraction = 0f)
        assertEquals(ProximityState.NO_FACE_DETECTED, state1)
        assertFalse(estimator.isCurrentlyTooClose)

        // Was too close, then face lost
        estimator.evaluate(faceDetected = true, widthFraction = 0.70f)
        assertTrue(estimator.isCurrentlyTooClose)

        val state2 = estimator.evaluate(faceDetected = false, widthFraction = 0f)
        assertEquals(ProximityState.NO_FACE_DETECTED, state2)
        assertFalse(estimator.isCurrentlyTooClose)
    }

    @Test
    fun hysteresis_preventsFlickerAroundThreshold() {
        // 1. Start far away (~40 cm, fraction 0.30)
        assertEquals(ProximityState.NORMAL_DISTANCE, estimator.evaluate(faceDetected = true, widthFraction = 0.30f))
        assertFalse(estimator.isCurrentlyTooClose)

        // 2. Approach ~25 cm (fraction 0.50): between recovery (0.45) and trigger (0.60)
        // Should stay NORMAL because trigger is 0.60
        assertEquals(ProximityState.NORMAL_DISTANCE, estimator.evaluate(faceDetected = true, widthFraction = 0.50f))
        assertFalse(estimator.isCurrentlyTooClose)

        // 3. Approach closer than 20 cm (fraction 0.65 >= 0.60) -> Triggers TOO_CLOSE
        assertEquals(ProximityState.TOO_CLOSE, estimator.evaluate(faceDetected = true, widthFraction = 0.65f))
        assertTrue(estimator.isCurrentlyTooClose)

        // 4. Back away slightly to ~25 cm (fraction 0.52): still in hysteresis band (0.45 to 0.60)
        // Must STAY TOO_CLOSE to avoid screen dim flickering
        assertEquals(ProximityState.TOO_CLOSE, estimator.evaluate(faceDetected = true, widthFraction = 0.52f))
        assertTrue(estimator.isCurrentlyTooClose)

        // 5. Back away to ~30 cm or farther (fraction 0.42 < 0.45) -> Recovers to NORMAL
        assertEquals(ProximityState.NORMAL_DISTANCE, estimator.evaluate(faceDetected = true, widthFraction = 0.42f))
        assertFalse(estimator.isCurrentlyTooClose)

        // 6. Move slightly closer to 0.50 again -> stays NORMAL
        assertEquals(ProximityState.NORMAL_DISTANCE, estimator.evaluate(faceDetected = true, widthFraction = 0.50f))
        assertFalse(estimator.isCurrentlyTooClose)
    }
}
