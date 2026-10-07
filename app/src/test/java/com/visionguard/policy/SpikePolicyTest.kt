// Comprehensive JVM unit tests for ProtectionPolicy state machine transitions and timing rules.
package com.visionguard.policy

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpikePolicyTest {

    private lateinit var policy: ProtectionPolicy

    @Before
    fun setUp() {
        policy = ProtectionPolicy(
            triggerThresholdFraction = 0.60f,   // ~20 cm
            recoveryThresholdFraction = 0.45f,  // ~30 cm
            noFaceTimeoutMs = 5000L             // 5 seconds
        )
    }

    @Test
    fun calculateWidthFraction_and_distanceEstimation_math() {
        val fraction = ProtectionPolicy.calculateWidthFraction(boundingBoxWidth = 300, uprightImageWidth = 500)
        assertEquals(0.60f, fraction, 0.0001f)

        val dist20cm = ProtectionPolicy.estimateDistanceCm(0.60f)
        assertNotNull(dist20cm)
        assertEquals(20.0f, dist20cm!!, 0.1f)

        val dist30cm = ProtectionPolicy.estimateDistanceCm(0.40f)
        assertNotNull(dist30cm)
        assertEquals(30.0f, dist30cm!!, 0.1f)
    }

    @Test
    fun calculateWidthFraction_invalidDimensions_returnsZero() {
        assertEquals(0f, ProtectionPolicy.calculateWidthFraction(boundingBoxWidth = 100, uprightImageWidth = 0), 0.0001f)
        assertEquals(0f, ProtectionPolicy.calculateWidthFraction(boundingBoxWidth = 0, uprightImageWidth = 400), 0.0001f)
        assertEquals(0f, ProtectionPolicy.calculateWidthFraction(boundingBoxWidth = -10, uprightImageWidth = 400), 0.0001f)
    }

    @Test
    fun reset_clearsProximityAndState() {
        val closeDecision = policy.evaluate(faceDetected = true, widthFraction = 0.65f, currentTimeMs = 1000L)
        assertTrue(closeDecision.shouldDim)
        policy.reset(currentTimeMs = 2000L)
        val decision = policy.evaluate(faceDetected = true, widthFraction = 0.35f, currentTimeMs = 2100L)
        assertEquals(ProtectionState.NORMAL_DISTANCE, decision.state)
        assertFalse(decision.shouldDim)
    }

    @Test
    fun facePresent_evaluatesNormalAndTooCloseAccurately() {
        val normalDecision = policy.evaluate(faceDetected = true, widthFraction = 0.35f, currentTimeMs = 1000L)
        assertEquals(ProtectionState.NORMAL_DISTANCE, normalDecision.state)
        assertFalse(normalDecision.shouldDim)
        assertFalse(normalDecision.isPowerSaving)
        assertTrue(normalDecision.isFacePresent)

        val tooCloseDecision = policy.evaluate(faceDetected = true, widthFraction = 0.65f, currentTimeMs = 1100L)
        assertEquals(ProtectionState.TOO_CLOSE, tooCloseDecision.state)
        assertTrue(tooCloseDecision.shouldDim)
        assertFalse(tooCloseDecision.isPowerSaving)
        assertTrue(tooCloseDecision.isFacePresent)
    }

    @Test
    fun hysteresis_preventsScreenDimFlicker() {
        // 1. Far away: normal
        val d1 = policy.evaluate(faceDetected = true, widthFraction = 0.30f, currentTimeMs = 1000L)
        assertFalse(d1.shouldDim)

        // 2. Approach ~25 cm (0.50): stays normal
        val d2 = policy.evaluate(faceDetected = true, widthFraction = 0.50f, currentTimeMs = 1100L)
        assertFalse(d2.shouldDim)

        // 3. Approach closer than 20 cm (0.62 >= 0.60): triggers dim
        val d3 = policy.evaluate(faceDetected = true, widthFraction = 0.62f, currentTimeMs = 1200L)
        assertTrue(d3.shouldDim)
        assertEquals(ProtectionState.TOO_CLOSE, d3.state)

        // 4. Back away slightly to 0.52 (within 0.45..0.60 band): stays dimmed to avoid flicker
        val d4 = policy.evaluate(faceDetected = true, widthFraction = 0.52f, currentTimeMs = 1300L)
        assertTrue(d4.shouldDim)
        assertEquals(ProtectionState.TOO_CLOSE, d4.state)

        // 5. Back away to 30 cm or more (0.42 < 0.45): recovers, clears dim
        val d5 = policy.evaluate(faceDetected = true, widthFraction = 0.42f, currentTimeMs = 1400L)
        assertFalse(d5.shouldDim)
        assertEquals(ProtectionState.NORMAL_DISTANCE, d5.state)
    }

    @Test
    fun noFace_gracePeriodPreventsImmediateFlicker() {
        // Face seen at t = 1000ms
        policy.evaluate(faceDetected = true, widthFraction = 0.40f, currentTimeMs = 1000L)

        // 2 seconds later: single missed frame or temporary look-away
        val graceDecision = policy.evaluate(faceDetected = false, widthFraction = 0f, currentTimeMs = 3000L)
        assertEquals(ProtectionState.NO_FACE_GRACE_PERIOD, graceDecision.state)
        assertFalse(graceDecision.shouldDim)
        assertFalse(graceDecision.isPowerSaving)
        assertFalse(graceDecision.isFacePresent)
    }

    @Test
    fun noFace_exceeding5Seconds_dimsScreenAndEntersLowPower() {
        // Face seen at t = 1000ms
        policy.evaluate(faceDetected = true, widthFraction = 0.40f, currentTimeMs = 1000L)

        // 4.9 seconds later: still grace period
        val d1 = policy.evaluate(faceDetected = false, widthFraction = 0f, currentTimeMs = 5900L)
        assertFalse(d1.shouldDim)
        assertFalse(d1.isPowerSaving)

        // 5.0 seconds later (6000ms - 1000ms = 5000ms): triggers NO_FACE_DIMMED
        val d2 = policy.evaluate(faceDetected = false, widthFraction = 0f, currentTimeMs = 6000L)
        assertEquals(ProtectionState.NO_FACE_DIMMED, d2.state)
        assertTrue(d2.shouldDim)
        assertTrue(d2.isPowerSaving)
        assertFalse(d2.isFacePresent)
    }

    @Test
    fun faceReturnsAfterNoFaceDimmedState_exitsLowPowerAndEvaluatesProximity() {
        // Enter no-face dimmed state at t = 10,000ms
        policy.evaluate(faceDetected = true, widthFraction = 0.40f, currentTimeMs = 1000L)
        val dimmedDecision = policy.evaluate(faceDetected = false, widthFraction = 0f, currentTimeMs = 10000L)
        assertEquals(ProtectionState.NO_FACE_DIMMED, dimmedDecision.state)
        assertTrue(dimmedDecision.shouldDim)
        assertTrue(dimmedDecision.isPowerSaving)

        // Case A: User returns at normal distance (~35 cm, fraction 0.35)
        val returnNormal = policy.evaluate(faceDetected = true, widthFraction = 0.35f, currentTimeMs = 12000L)
        assertEquals(ProtectionState.NORMAL_DISTANCE, returnNormal.state)
        assertFalse(returnNormal.shouldDim) // Dimming cleared!
        assertFalse(returnNormal.isPowerSaving) // Exited low power!
        assertTrue(returnNormal.isFacePresent)

        // Re-enter no-face state
        policy.evaluate(faceDetected = false, widthFraction = 0f, currentTimeMs = 18000L)

        // Case B: User returns holding phone very close (< 20 cm, fraction 0.65)
        val returnClose = policy.evaluate(faceDetected = true, widthFraction = 0.65f, currentTimeMs = 20000L)
        assertEquals(ProtectionState.TOO_CLOSE, returnClose.state)
        assertTrue(returnClose.shouldDim) // Stays dimmed due to proximity!
        assertFalse(returnClose.isPowerSaving) // Exited low power!
        assertTrue(returnClose.isFacePresent)
    }
}
