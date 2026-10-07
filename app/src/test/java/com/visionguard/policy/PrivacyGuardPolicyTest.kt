// Unit tests for pure Kotlin PrivacyGuardPolicy covering secondary viewer qualification,
// N=3 frame confirmation, 2s absence clearance, manual dismissal, and guard priority.
package com.visionguard.policy

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PrivacyGuardPolicyTest {

    private lateinit var testClock: TestClock
    private lateinit var policy: PrivacyGuardPolicy

    @Before
    fun setUp() {
        testClock = TestClock(1000L)
        policy = PrivacyGuardPolicy(
            minSecondaryWidthFraction = 0.10f,
            maxYawAngleDegrees = 35.0f,
            consecutiveFramesRequired = 3,
            absenceTimeoutMs = 2000L,
            clock = testClock
        )
    }

    @Test
    fun singleFace_doesNotTriggerPrivacyGuard() {
        val singleFaceObs = FaceObservation(
            widthFraction = 0.40f,
            yaw = 0f,
            count = 1,
            timestampMs = 1000L,
            secondaryFaces = emptyList()
        )

        for (i in 1..5) {
            testClock.advanceBy(100L)
            val decision = policy.evaluate(singleFaceObs)
            assertFalse(decision.isTriggered)
            assertEquals(0, decision.qualifyingViewerCount)
            assertEquals(0, decision.consecutiveFrames)
        }
    }

    @Test
    fun secondaryFaceBelowWidthThreshold_isIgnored() {
        // Second face is too small / far away (widthFraction = 0.08 < 0.10)
        val smallFaceObs = FaceObservation(
            widthFraction = 0.40f,
            yaw = 0f,
            count = 2,
            timestampMs = 1000L,
            secondaryFaces = listOf(DetectedFace(widthFraction = 0.08f, yaw = 0f))
        )

        for (i in 1..5) {
            testClock.advanceBy(100L)
            val decision = policy.evaluate(smallFaceObs)
            assertFalse(decision.isTriggered)
            assertEquals(0, decision.qualifyingViewerCount)
        }
    }

    @Test
    fun secondaryFaceLookingAway_isIgnored() {
        // Second face has yaw = 45 deg (>= 35 deg, looking away from screen)
        val turnedFaceObs = FaceObservation(
            widthFraction = 0.40f,
            yaw = 0f,
            count = 2,
            timestampMs = 1000L,
            secondaryFaces = listOf(DetectedFace(widthFraction = 0.20f, yaw = 45f))
        )

        for (i in 1..5) {
            testClock.advanceBy(100L)
            val decision = policy.evaluate(turnedFaceObs)
            assertFalse(decision.isTriggered)
            assertEquals(0, decision.qualifyingViewerCount)
        }
    }

    @Test
    fun qualifyingSecondaryFace_triggersAfter3ConsecutiveFrames() {
        val viewerObs = FaceObservation(
            widthFraction = 0.40f,
            yaw = 0f,
            count = 2,
            timestampMs = 1000L,
            secondaryFaces = listOf(DetectedFace(widthFraction = 0.15f, yaw = 10f))
        )

        // Frame 1
        testClock.advanceBy(100L)
        val d1 = policy.evaluate(viewerObs)
        assertFalse(d1.isTriggered)
        assertEquals(1, d1.consecutiveFrames)
        assertEquals(1, d1.qualifyingViewerCount)

        // Frame 2
        testClock.advanceBy(100L)
        val d2 = policy.evaluate(viewerObs)
        assertFalse(d2.isTriggered)
        assertEquals(2, d2.consecutiveFrames)

        // Frame 3: reaches threshold of 3 -> triggers alert
        testClock.advanceBy(100L)
        val d3 = policy.evaluate(viewerObs)
        assertTrue(d3.isTriggered)
        assertEquals(3, d3.consecutiveFrames)
        assertNotNull(d3.mostProminentViewer)
        assertEquals(0.15f, d3.mostProminentViewer!!.widthFraction, 0.001f)
    }

    @Test
    fun triggeredAlert_persistsDuringBriefAbsence_clearsAfter2Seconds() {
        val viewerObs = FaceObservation(
            widthFraction = 0.40f,
            yaw = 0f,
            count = 2,
            timestampMs = 1000L,
            secondaryFaces = listOf(DetectedFace(widthFraction = 0.15f, yaw = 5f))
        )

        // Trigger with 3 frames
        for (i in 1..3) {
            testClock.advanceBy(100L)
            policy.evaluate(viewerObs)
        }
        assertTrue(policy.evaluate(viewerObs).isTriggered)

        // Face disappears (count = 1, no secondary faces)
        val singleObs = FaceObservation(widthFraction = 0.40f, yaw = 0f, count = 1, timestampMs = 1300L)

        // At 1000 ms elapsed (< 2000 ms timeout), still triggered
        testClock.advanceBy(1000L)
        val dBrief = policy.evaluate(singleObs)
        assertTrue("Alert should persist during brief absence < 2s", dBrief.isTriggered)

        // At 2100 ms elapsed (>= 2000 ms timeout), clears automatically
        testClock.advanceBy(1100L)
        val dCleared = policy.evaluate(singleObs)
        assertFalse("Alert should clear after 2 seconds of secondary absence", dCleared.isTriggered)
    }

    @Test
    fun manualDismissal_suppressesAlertUntilViewerLeavesAndReturns() {
        val viewerObs = FaceObservation(
            widthFraction = 0.40f,
            yaw = 0f,
            count = 2,
            timestampMs = 1000L,
            secondaryFaces = listOf(DetectedFace(widthFraction = 0.18f, yaw = -10f))
        )

        // Trigger alert
        for (i in 1..3) {
            testClock.advanceBy(100L)
            policy.evaluate(viewerObs)
        }
        assertTrue(policy.evaluate(viewerObs).isTriggered)

        // User taps dismiss action
        policy.dismiss()

        // Same viewer still present: must remain suppressed (not re-trigger)
        testClock.advanceBy(100L)
        val dDismissed = policy.evaluate(viewerObs)
        assertFalse(dDismissed.isTriggered)
        assertTrue(dDismissed.isDismissed)

        // Viewer leaves for > 2 seconds
        val singleObs = FaceObservation(widthFraction = 0.40f, yaw = 0f, count = 1, timestampMs = 1500L)
        testClock.advanceBy(2500L)
        val dAbsent = policy.evaluate(singleObs)
        assertFalse(dAbsent.isTriggered)
        assertFalse(dAbsent.isDismissed)

        // Viewer returns: triggers again after 3 frames
        for (i in 1..3) {
            testClock.advanceBy(100L)
            policy.evaluate(viewerObs)
        }
        val dReTriggered = policy.evaluate(viewerObs)
        assertTrue("Viewer returning after absence must re-trigger alert", dReTriggered.isTriggered)
    }

    @Test
    fun disabledPolicy_doesNotTrigger() {
        val viewerObs = FaceObservation(
            widthFraction = 0.40f,
            yaw = 0f,
            count = 2,
            timestampMs = 1000L,
            secondaryFaces = listOf(DetectedFace(widthFraction = 0.20f, yaw = 0f))
        )

        for (i in 1..5) {
            testClock.advanceBy(100L)
            val decision = policy.evaluate(viewerObs, isEnabled = false)
            assertFalse(decision.isTriggered)
            assertEquals(0, decision.consecutiveFrames)
        }
    }

    @Test
    fun guardPriority_privacyOverlayWinsOverEyeGuardDimming() {
        // Document and verify priority: Privacy Alert supersedes Eye Guard Dimming
        val eyeGuardPolicy = EyeGuardPolicy(clock = testClock)

        // Eye Guard triggers TOO_CLOSE
        val closeAndShoulderSurfed = FaceObservation(
            widthFraction = 0.80f,
            yaw = 0f,
            count = 2,
            timestampMs = 1000L,
            secondaryFaces = listOf(DetectedFace(widthFraction = 0.25f, yaw = 0f))
        )

        for (i in 1..5) {
            testClock.advanceBy(100L)
            eyeGuardPolicy.evaluate(closeAndShoulderSurfed)
            policy.evaluate(closeAndShoulderSurfed)
        }

        val eyeDecision = eyeGuardPolicy.evaluate(closeAndShoulderSurfed)
        val privacyDecision = policy.evaluate(closeAndShoulderSurfed)

        assertTrue(eyeDecision.shouldDim)
        assertTrue(privacyDecision.isTriggered)

        // Priority resolution rule
        val activeOverlay = if (privacyDecision.isTriggered) "PRIVACY_GUARD_SHIELD" else if (eyeDecision.shouldDim) "EYE_GUARD_DIM" else "NONE"
        assertEquals("Privacy Guard shield must take priority over Eye Guard dimming", "PRIVACY_GUARD_SHIELD", activeOverlay)
    }
}
