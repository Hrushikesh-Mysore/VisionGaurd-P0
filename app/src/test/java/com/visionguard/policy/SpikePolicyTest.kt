// Unit tests verifying SpikePolicy mathematical calculations and threshold decisions.
// Runs purely on the JVM with no Android SDK dependencies.
package com.visionguard.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpikePolicyTest {

    @Test
    fun calculateWidthFraction_validDimensions_computesAccurately() {
        val fraction = SpikePolicy.calculateWidthFraction(boundingBoxWidth = 240, uprightImageWidth = 480)
        assertEquals(0.5f, fraction, 0.0001f)
    }

    @Test
    fun calculateWidthFraction_zeroOrNegativeUprightWidth_returnsZero() {
        val zeroWidth = SpikePolicy.calculateWidthFraction(boundingBoxWidth = 100, uprightImageWidth = 0)
        assertEquals(0f, zeroWidth, 0.0001f)

        val negativeWidth = SpikePolicy.calculateWidthFraction(boundingBoxWidth = 100, uprightImageWidth = -1)
        assertEquals(0f, negativeWidth, 0.0001f)
    }

    @Test
    fun isTooClose_evaluatesThresholdCorrectly() {
        assertFalse(SpikePolicy.isTooClose(0.44f))
        assertTrue(SpikePolicy.isTooClose(0.45f))
        assertTrue(SpikePolicy.isTooClose(0.60f))
    }
}
