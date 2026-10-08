// Checks profile attribution at session start across saved profile transitions.
// These tests protect Parent and Child usage totals from being mixed.
package com.visionguard.policy

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileAttributionPolicyTest {
    private val records = listOf(
        ProfileSwitchRecord(100L, UserProfile.PARENT),
        ProfileSwitchRecord(250L, UserProfile.CHILD),
        ProfileSwitchRecord(400L, UserProfile.PARENT)
    )

    @Test fun usesProfileActiveAtSessionStart() {
        assertEquals(UserProfile.PARENT, ProfileAttributionPolicy.profileAt(200L, records, UserProfile.CHILD))
        assertEquals(UserProfile.CHILD, ProfileAttributionPolicy.profileAt(300L, records, UserProfile.PARENT))
        assertEquals(UserProfile.PARENT, ProfileAttributionPolicy.profileAt(500L, records, UserProfile.CHILD))
    }

    @Test fun usesEarliestKnownProfileBeforeFirstSavedTransition() {
        assertEquals(UserProfile.PARENT, ProfileAttributionPolicy.profileAt(50L, records, UserProfile.CHILD))
    }

    @Test fun emptyHistoryUsesCurrentProfile() {
        assertEquals(UserProfile.CHILD, ProfileAttributionPolicy.profileAt(50L, emptyList(), UserProfile.CHILD))
    }
}
