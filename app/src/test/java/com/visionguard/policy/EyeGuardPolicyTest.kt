// Comprehensive JVM unit tests for EyeGuardPolicy covering calibration,
// EMA smoothing, N-frame confirmation, hysteresis, and proportional opacity.
package com.visionguard.policy

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EyeGuardPolicyTest {

    private lateinit var testClock: TestClock
    private lateinit var policy: EyeGuardPolicy

    @Before
    fun setUp() {
        testClock = TestClock(1000L)
        policy = EyeGuardPolicy(
            calibrationK = 12.0f,
            tooCloseThresholdCm = 20.0f,
            recoveryMarginCm = 10.0f, // recovery at 30.0f
            consecutiveFramesRequired = 5,
            noFaceTimeoutMs = 5000L,
            emaAlpha = 0.35f,
            clock = testClock
        )
    }

    @Test
    fun geometryMath_and_distanceEstimation_computesAccurately() {
        val fraction = EyeGuardPolicy.calculateWidthFraction(boundingBoxWidth = 240, uprightImageWidth = 480)
        assertEquals(0.50f, fraction, 0.0001f)

        // d = 12.0 / 0.60 = 20.0 cm
        val dist20cm = EyeGuardPolicy.estimateDistanceCm(0.60f, k = 12.0f)
        assertNotNull(dist20cm)
        assertEquals(20.0f, dist20cm!!, 0.1f)

        // d = 12.0 / 0.40 = 30.0 cm
        val dist30cm = EyeGuardPolicy.estimateDistanceCm(0.40f, k = 12.0f)
        assertNotNull(dist30cm)
        assertEquals(30.0f, dist30cm!!, 0.1f)
    }

    @Test
    fun calibrationMath_calculatesKAtReferenceDistance() {
        // At 30 cm, face width fraction is 0.40 -> K = 30 * 0.40 = 12.0
        val kStandard = EyeGuardPolicy.calculateCalibratedK(referenceDistanceCm = 30.0f, observedWidthFraction = 0.40f)
        assertEquals(12.0f, kStandard, 0.01f)

        // Smaller face at 30 cm (fraction 0.30) -> K = 30 * 0.30 = 9.0
        val kSmaller = EyeGuardPolicy.calculateCalibratedK(referenceDistanceCm = 30.0f, observedWidthFraction = 0.30f)
        assertEquals(9.0f, kSmaller, 0.01f)

        // Invalid fraction defaults safely
        val kInvalid = EyeGuardPolicy.calculateCalibratedK(referenceDistanceCm = 30.0f, observedWidthFraction = 0f)
        assertEquals(EyeGuardPolicy.DEFAULT_CALIBRATION_K, kInvalid, 0.01f)
    }

    @Test
    fun consecutiveFramesConfirmation_preventsInstantFlicker() {
        // User moves close (< 20 cm, fraction 0.70 => distance ~17 cm)
        val closeObservation = FaceObservation(widthFraction = 0.70f, yaw = 0f, count = 1, timestampMs = 1000L)

        // Frames 1 to 4: below threshold, but consecutive counter < 5 -> stays NORMAL_DISTANCE
        for (i in 1..4) {
            testClock.advanceBy(100L)
            val decision = policy.evaluate(closeObservation)
            assertEquals(ProtectionState.NORMAL_DISTANCE, decision.state)
            assertFalse(decision.shouldDim)
            assertEquals(i, decision.consecutiveCloseFrames)
        }

        // Frame 5: reaches 5 consecutive frames -> triggers TOO_CLOSE
        testClock.advanceBy(100L)
        val triggeredDecision = policy.evaluate(closeObservation)
        assertEquals(ProtectionState.TOO_CLOSE, triggeredDecision.state)
        assertTrue(triggeredDecision.shouldDim)
        assertTrue(triggeredDecision.dimOpacity >= EyeGuardPolicy.MIN_OVERLAY_OPACITY)
        assertTrue(triggeredDecision.dimOpacity <= EyeGuardPolicy.MAX_OVERLAY_OPACITY)
    }

    @Test
    fun proportionalOpacity_capsAt0Point8() {
        // Trigger TOO_CLOSE
        val veryCloseObservation = FaceObservation(widthFraction = 1.0f, yaw = 0f, count = 1, timestampMs = 1000L)
        for (i in 1..5) {
            testClock.advanceBy(100L)
            policy.evaluate(veryCloseObservation)
        }
        val decision = policy.evaluate(veryCloseObservation)
        assertTrue(decision.shouldDim)
        // Opacity must be strictly capped at 0.8 per Android 12+ touch pass-through rule
        assertEquals(EyeGuardPolicy.MAX_OVERLAY_OPACITY, decision.dimOpacity, 0.01f)
    }

    @Test
    fun hysteresis_preventsScreenDimFlickerInIntermediateBand() {
        val closeObservation = FaceObservation(widthFraction = 0.70f, yaw = 0f, count = 1, timestampMs = 1000L)

        // Trigger TOO_CLOSE with 5 frames
        for (i in 1..5) {
            testClock.advanceBy(100L)
            policy.evaluate(closeObservation)
        }

        // Back away slightly into hysteresis band (~25 cm, fraction 0.48)
        // Below recovery threshold (30 cm), so must STAY TOO_CLOSE
        val intermediateObservation = FaceObservation(widthFraction = 0.48f, yaw = 0f, count = 1, timestampMs = 2000L)
        for (i in 1..5) {
            testClock.advanceBy(100L)
            val d = policy.evaluate(intermediateObservation)
            assertEquals(ProtectionState.TOO_CLOSE, d.state)
            assertTrue(d.shouldDim)
        }

        // Back away to safe distance (> 30 cm, fraction 0.35 => ~34 cm)
        // With EMA smoothing (alpha = 0.35), recovery confirms within ~5 frames (500 ms, < 1 second)
        val safeObservation = FaceObservation(widthFraction = 0.35f, yaw = 0f, count = 1, timestampMs = 3000L)
        var recoveredDecision = policy.evaluate(safeObservation)
        for (i in 1..4) {
            testClock.advanceBy(100L)
            recoveredDecision = policy.evaluate(safeObservation)
        }
        assertEquals(ProtectionState.NORMAL_DISTANCE, recoveredDecision.state)
        assertFalse(recoveredDecision.shouldDim)
        assertEquals(0.0f, recoveredDecision.dimOpacity, 0.001f)
    }

    @Test
    fun noFace_gracePeriodAnd5SecondTimeout() {
        // Face seen at t = 1000
        policy.evaluate(FaceObservation(widthFraction = 0.40f, yaw = 0f, count = 1, timestampMs = 1000L))

        // No face at t = 3000 (2s elapsed): grace period
        testClock.setTime(3000L)
        val graceDecision = policy.evaluate(FaceObservation(widthFraction = 0f, yaw = 0f, count = 0, timestampMs = 3000L))
        assertEquals(ProtectionState.NO_FACE_GRACE_PERIOD, graceDecision.state)
        assertFalse(graceDecision.shouldDim)
        assertFalse(graceDecision.isPowerSaving)

        // No face at t = 6000 (5000ms elapsed): triggers NO_FACE_DIMMED
        testClock.setTime(6000L)
        val dimmedDecision = policy.evaluate(FaceObservation(widthFraction = 0f, yaw = 0f, count = 0, timestampMs = 6000L))
        assertEquals(ProtectionState.NO_FACE_DIMMED, dimmedDecision.state)
        assertTrue(dimmedDecision.shouldDim)
        assertTrue(dimmedDecision.isPowerSaving)
        assertEquals(EyeGuardPolicy.DEFAULT_IDLE_DIM_OPACITY, dimmedDecision.dimOpacity, 0.01f)
    }

    @Test
    fun faceReturnsAfterNoFaceDimmedState_exitsPowerSavingImmediately() {
        // Transition to NO_FACE_DIMMED
        policy.evaluate(FaceObservation(widthFraction = 0.40f, yaw = 0f, count = 1, timestampMs = 1000L))
        testClock.setTime(7000L)
        val d1 = policy.evaluate(FaceObservation(widthFraction = 0f, yaw = 0f, count = 0, timestampMs = 7000L))
        assertEquals(ProtectionState.NO_FACE_DIMMED, d1.state)

        // Face returns at safe distance (~35 cm, fraction 0.35)
        testClock.setTime(8000L)
        val returnDecision = policy.evaluate(FaceObservation(widthFraction = 0.35f, yaw = 0f, count = 1, timestampMs = 8000L))
        assertEquals(ProtectionState.NORMAL_DISTANCE, returnDecision.state)
        assertFalse(returnDecision.shouldDim)
        assertFalse(returnDecision.isPowerSaving)
        assertTrue(returnDecision.isFacePresent)
    }
}
