// Pure Kotlin domain representation of application-level user profiles.
// NOTE: These are application-level profiles, NOT Android OS multi-user accounts.
// Normal Android applications cannot provide true operating system level user separation.
package com.visionguard.policy

enum class UserProfile {
    PARENT,
    CHILD
}

data class ProfileSwitchRecord(
    val timestamp: Long,
    val profile: UserProfile
)
