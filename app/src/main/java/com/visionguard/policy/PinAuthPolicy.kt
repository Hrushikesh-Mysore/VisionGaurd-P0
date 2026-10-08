// Pure Kotlin Parent PIN authentication engine with PBKDF2-HMAC-SHA256 and exponential lockout.
// Provides secure on-device PIN verification with zero Android framework imports and Clock abstraction.
package com.visionguard.policy

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class PinVerificationResult {
    object Success : PinVerificationResult()
    data class Failed(val attemptsRemaining: Int, val isLockedOut: Boolean, val lockoutDurationSeconds: Long) : PinVerificationResult()
    data class LockedOut(val remainingSeconds: Long) : PinVerificationResult()
    object PinNotConfigured : PinVerificationResult()
}

class PinAuthPolicy(
    val maxConsecutiveAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val initialLockoutMs: Long = DEFAULT_INITIAL_LOCKOUT_MS,
    private val clock: Clock? = null
) {
    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val DEFAULT_INITIAL_LOCKOUT_MS = 30_000L // 30 seconds
        const val ITERATION_COUNT = 100_000 // MASVS compliant
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH_BYTES = 16
        const val MAX_LOCKOUT_MS = 3600_000L // 1 hour cap

        fun generateSalt(): ByteArray {
            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTES)
            random.nextBytes(salt)
            return salt
        }

        fun hashPin(pin: String, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        }
    }

    private var consecutiveFailures: Int = 0
    private var lockoutUntilMs: Long = 0L
    private var currentLockoutDurationMs: Long = initialLockoutMs

    data class State(
        val consecutiveFailures: Int,
        val lockoutRemainingMs: Long,
        val currentLockoutDurationMs: Long,
        val savedAtElapsedMs: Long,
        val savedBootCount: Long?
    )

    fun saveState(currentTimeMs: Long = getCurrentTime()): State = State(
        consecutiveFailures = consecutiveFailures,
        lockoutRemainingMs = (lockoutUntilMs - currentTimeMs).coerceAtLeast(0L),
        currentLockoutDurationMs = currentLockoutDurationMs,
        savedAtElapsedMs = currentTimeMs,
        savedBootCount = clock?.bootCount()
    )

    fun restoreState(state: State, currentTimeMs: Long = getCurrentTime()) {
        consecutiveFailures = state.consecutiveFailures.coerceAtLeast(0)
        currentLockoutDurationMs = state.currentLockoutDurationMs.coerceIn(initialLockoutMs, MAX_LOCKOUT_MS)
        val sameBoot = clock?.bootCount() == state.savedBootCount
        val remaining = if (sameBoot && currentTimeMs >= state.savedAtElapsedMs) {
            (state.lockoutRemainingMs - (currentTimeMs - state.savedAtElapsedMs)).coerceAtLeast(0L)
        } else {
            // elapsedRealtime resets after reboot; retain the saved remainder conservatively.
            state.lockoutRemainingMs
        }
        lockoutUntilMs = currentTimeMs + remaining
    }

    fun isLockedOut(currentTimeMs: Long = getCurrentTime()): Boolean {
        return currentTimeMs < lockoutUntilMs
    }

    fun getRemainingLockoutSeconds(currentTimeMs: Long = getCurrentTime()): Long {
        if (!isLockedOut(currentTimeMs)) return 0L
        val diff = lockoutUntilMs - currentTimeMs
        return (diff + 999L) / 1000L
    }

    fun verifyPin(
        enteredPin: String,
        storedSalt: ByteArray?,
        storedHash: ByteArray?,
        currentTimeMs: Long = getCurrentTime()
    ): PinVerificationResult {
        if (storedSalt == null || storedHash == null || storedSalt.isEmpty() || storedHash.isEmpty()) {
            return PinVerificationResult.PinNotConfigured
        }

        if (isLockedOut(currentTimeMs)) {
            return PinVerificationResult.LockedOut(getRemainingLockoutSeconds(currentTimeMs))
        }

        val enteredHash = hashPin(enteredPin, storedSalt)
        val isMatch = MessageDigest.isEqual(enteredHash, storedHash)

        return if (isMatch) {
            // Reset failure counter and lockout progression on successful authentication
            consecutiveFailures = 0
            lockoutUntilMs = 0L
            currentLockoutDurationMs = initialLockoutMs
            PinVerificationResult.Success
        } else {
            consecutiveFailures++
            if (consecutiveFailures >= maxConsecutiveAttempts) {
                // Trigger exponential lockout
                val lockoutDuration = currentLockoutDurationMs
                lockoutUntilMs = currentTimeMs + lockoutDuration
                currentLockoutDurationMs = (currentLockoutDurationMs * 2).coerceAtMost(MAX_LOCKOUT_MS)
                PinVerificationResult.Failed(
                    attemptsRemaining = 0,
                    isLockedOut = true,
                    lockoutDurationSeconds = lockoutDuration / 1000L
                )
            } else {
                val attemptsLeft = maxConsecutiveAttempts - consecutiveFailures
                PinVerificationResult.Failed(
                    attemptsRemaining = attemptsLeft,
                    isLockedOut = false,
                    lockoutDurationSeconds = 0L
                )
            }
        }
    }

    fun resetLockout() {
        consecutiveFailures = 0
        lockoutUntilMs = 0L
        currentLockoutDurationMs = initialLockoutMs
    }

    private fun getCurrentTime(): Long = clock?.elapsedRealtime() ?: System.nanoTime() / 1_000_000L
}
