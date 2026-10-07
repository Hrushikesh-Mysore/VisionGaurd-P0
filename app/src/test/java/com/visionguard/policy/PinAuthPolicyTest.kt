// Unit tests for PinAuthPolicy covering PBKDF2 hashing, attempt rate-limiting, and exponential lockout.
// Tested deterministically against pure Kotlin TestClock with zero Android framework dependencies.
package com.visionguard.policy

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PinAuthPolicyTest {

    private lateinit var testClock: TestClock
    private lateinit var policy: PinAuthPolicy

    @Before
    fun setUp() {
        testClock = TestClock(1000L)
        policy = PinAuthPolicy(
            maxConsecutiveAttempts = 5,
            initialLockoutMs = 30_000L,
            clock = testClock
        )
    }

    @Test
    fun saltGeneration_produces16UniqueBytes() {
        val salt1 = PinAuthPolicy.generateSalt()
        val salt2 = PinAuthPolicy.generateSalt()

        assertEquals(16, salt1.size)
        assertEquals(16, salt2.size)
        assertFalse(salt1.contentEquals(salt2))
    }

    @Test
    fun pbkdf2Hashing_producesConsistent256BitKey() {
        val salt = PinAuthPolicy.generateSalt()
        val hash1 = PinAuthPolicy.hashPin("1234", salt)
        val hash2 = PinAuthPolicy.hashPin("1234", salt)

        assertEquals(32, hash1.size) // 256 bits = 32 bytes
        assertArrayEquals(hash1, hash2)

        val hashOtherPin = PinAuthPolicy.hashPin("4321", salt)
        assertFalse(hash1.contentEquals(hashOtherPin))
    }

    @Test
    fun verifyPin_withCorrectPin_returnsSuccess() {
        val salt = PinAuthPolicy.generateSalt()
        val hash = PinAuthPolicy.hashPin("2580", salt)

        val result = policy.verifyPin("2580", salt, hash)
        assertTrue(result is PinVerificationResult.Success)
    }

    @Test
    fun verifyPin_withIncorrectPin_decrementsAttempts() {
        val salt = PinAuthPolicy.generateSalt()
        val hash = PinAuthPolicy.hashPin("2580", salt)

        val result1 = policy.verifyPin("0000", salt, hash)
        assertTrue(result1 is PinVerificationResult.Failed)
        assertEquals(4, (result1 as PinVerificationResult.Failed).attemptsRemaining)
        assertFalse(result1.isLockedOut)

        val result2 = policy.verifyPin("1111", salt, hash)
        assertEquals(3, (result2 as PinVerificationResult.Failed).attemptsRemaining)
    }

    @Test
    fun verifyPin_5ConsecutiveFailures_triggers30SecondLockout() {
        val salt = PinAuthPolicy.generateSalt()
        val hash = PinAuthPolicy.hashPin("9999", salt)

        for (i in 1..4) {
            val r = policy.verifyPin("0000", salt, hash)
            assertFalse((r as PinVerificationResult.Failed).isLockedOut)
        }

        // 5th failed attempt triggers lockout
        val r5 = policy.verifyPin("0000", salt, hash)
        assertTrue(r5 is PinVerificationResult.Failed)
        val failed = r5 as PinVerificationResult.Failed
        assertTrue(failed.isLockedOut)
        assertEquals(30L, failed.lockoutDurationSeconds)
        assertTrue(policy.isLockedOut())
    }

    @Test
    fun lockedOutState_rejectsAttemptsUntilExpiry() {
        val salt = PinAuthPolicy.generateSalt()
        val hash = PinAuthPolicy.hashPin("9999", salt)

        // Trigger lockout
        repeat(5) { policy.verifyPin("0000", salt, hash) }
        assertTrue(policy.isLockedOut())

        // Even with correct PIN, locked out policy rejects verification
        val lockedResult = policy.verifyPin("9999", salt, hash)
        assertTrue(lockedResult is PinVerificationResult.LockedOut)
        assertEquals(30L, (lockedResult as PinVerificationResult.LockedOut).remainingSeconds)

        // Advance clock by 15 seconds
        testClock.advanceBy(15_000L)
        assertEquals(15L, policy.getRemainingLockoutSeconds())

        // Advance clock past 30s lockout expiry
        testClock.advanceBy(16_000L)
        assertFalse(policy.isLockedOut())

        // Now correct PIN succeeds
        val successResult = policy.verifyPin("9999", salt, hash)
        assertTrue(successResult is PinVerificationResult.Success)
    }

    @Test
    fun exponentialLockout_doublesDurationOnSubsequentLockouts() {
        val salt = PinAuthPolicy.generateSalt()
        val hash = PinAuthPolicy.hashPin("1234", salt)

        // 1st lockout: 5 failures -> 30s
        repeat(5) { policy.verifyPin("0000", salt, hash) }
        assertEquals(30L, policy.getRemainingLockoutSeconds())

        // Advance past 1st lockout
        testClock.advanceBy(31_000L)
        assertFalse(policy.isLockedOut())

        // Next failure triggers 2nd lockout: 60s
        val rNext = policy.verifyPin("0000", salt, hash)
        assertTrue(rNext is PinVerificationResult.Failed)
        assertEquals(60L, (rNext as PinVerificationResult.Failed).lockoutDurationSeconds)
        assertEquals(60L, policy.getRemainingLockoutSeconds())

        // Advance past 2nd lockout
        testClock.advanceBy(61_000L)
        assertFalse(policy.isLockedOut())

        // Next failure triggers 3rd lockout: 120s
        val rThird = policy.verifyPin("0000", salt, hash)
        assertEquals(120L, (rThird as PinVerificationResult.Failed).lockoutDurationSeconds)
    }

    @Test
    fun unconfiguredPin_returnsPinNotConfigured() {
        val result = policy.verifyPin("1234", null, null)
        assertTrue(result is PinVerificationResult.PinNotConfigured)
    }

    @Test
    fun savedLockoutState_restoresRemainingTimeAndFailureBackoff() {
        val salt = PinAuthPolicy.generateSalt()
        val hash = PinAuthPolicy.hashPin("9999", salt)
        repeat(5) { policy.verifyPin("0000", salt, hash) }
        testClock.advanceBy(10_000L)
        val saved = policy.saveState()

        testClock.advanceBy(5_000L)
        val restored = PinAuthPolicy(clock = testClock)
        restored.restoreState(saved)
        assertEquals(15L, restored.getRemainingLockoutSeconds())
        assertTrue(restored.verifyPin("9999", salt, hash) is PinVerificationResult.LockedOut)
    }

    @Test
    fun savedLockoutState_survivesElapsedClockResetAfterReboot() {
        val salt = PinAuthPolicy.generateSalt()
        val hash = PinAuthPolicy.hashPin("9999", salt)
        repeat(5) { policy.verifyPin("0000", salt, hash) }
        val saved = policy.saveState()

        testClock.setTime(10L)
        val restored = PinAuthPolicy(clock = testClock)
        restored.restoreState(saved)
        assertEquals(30L, restored.getRemainingLockoutSeconds())
    }

    @Test
    fun savedLockoutState_usesBootCountWhenNewUptimeExceedsSavedUptime() {
        testClock.reboot(1_000L, 1L)
        policy = PinAuthPolicy(clock = testClock)
        val salt = PinAuthPolicy.generateSalt()
        val hash = PinAuthPolicy.hashPin("9999", salt)
        repeat(5) { policy.verifyPin("0000", salt, hash) }
        val saved = policy.saveState()

        testClock.reboot(2_000L, 2L)
        val restored = PinAuthPolicy(clock = testClock)
        restored.restoreState(saved)
        assertEquals(30L, restored.getRemainingLockoutSeconds())
    }
}
