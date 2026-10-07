// Pure Kotlin Clock abstraction for deterministic time testing.
// Decouples timing logic from the Android SDK.
package com.visionguard.policy

interface Clock {
    fun elapsedRealtime(): Long
}

class TestClock(private var currentTimeMs: Long = 0L) : Clock {
    override fun elapsedRealtime(): Long = currentTimeMs

    fun setTime(timeMs: Long) {
        currentTimeMs = timeMs
    }

    fun advanceBy(deltaMs: Long) {
        currentTimeMs += deltaMs
    }
}
