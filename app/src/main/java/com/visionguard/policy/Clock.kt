// Pure Kotlin Clock abstraction for deterministic time testing.
// Decouples timing logic from the Android SDK.
package com.visionguard.policy

interface Clock {
    fun elapsedRealtime(): Long
    fun bootCount(): Long? = null
}

class TestClock(private var currentTimeMs: Long = 0L) : Clock {
    private var currentBootCount: Long? = null
    override fun elapsedRealtime(): Long = currentTimeMs
    override fun bootCount(): Long? = currentBootCount

    fun setTime(timeMs: Long) {
        currentTimeMs = timeMs
    }

    fun advanceBy(deltaMs: Long) {
        currentTimeMs += deltaMs
    }

    fun reboot(timeMs: Long, bootCount: Long) {
        currentTimeMs = timeMs
        currentBootCount = bootCount
    }
}
