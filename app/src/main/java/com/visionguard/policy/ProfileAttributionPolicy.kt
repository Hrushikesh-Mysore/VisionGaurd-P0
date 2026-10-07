// Resolves the profile active when a usage session began.
// Keeping this rule pure Kotlin makes attribution deterministic and testable.
package com.visionguard.policy

object ProfileAttributionPolicy {
    fun profileAt(
        timestamp: Long,
        records: List<ProfileSwitchRecord>,
        fallback: UserProfile
    ): UserProfile = records
        .asSequence()
        .filter { it.timestamp <= timestamp }
        .maxByOrNull { it.timestamp }
        ?.profile
        ?: records.minByOrNull { it.timestamp }?.profile
        ?: fallback
}
