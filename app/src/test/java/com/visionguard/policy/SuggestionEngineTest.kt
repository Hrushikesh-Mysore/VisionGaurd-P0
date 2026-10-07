// Comprehensive JVM unit tests for pure Kotlin SuggestionEngine covering all recommendation rules.
// Verifies continuous feed threshold, 20-20-20 rule for eye strain, late night wind-down, and positive balance.
package com.visionguard.policy

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SuggestionEngineTest {

    private lateinit var engine: SuggestionEngine

    @Before
    fun setUp() {
        engine = SuggestionEngine(
            continuousFeedThresholdMinutes = 40L,
            frequentEyeGuardThreshold = 5,
            lateNightStartHour = 23,
            lateNightEndHour = 5
        )
    }

    @Test
    fun continuousFeedOver40Minutes_suggestsWalkOrStretch() {
        val metrics = DashboardMetrics(
            totalScreenTimeMs = 3 * 3600 * 1000L,
            continuousFeedMinutes = 45L,
            eyeGuardTriggerCountToday = 2,
            privacyGuardAlertCountToday = 1,
            currentHourOfDay = 15
        )

        val suggestion = engine.generateSuggestion(metrics)
        assertEquals(SuggestionType.FEED_BREAK, suggestion.type)
        assertTrue(suggestion.title.contains("Screen Break", ignoreCase = true))
        assertTrue(suggestion.message.contains("walk", ignoreCase = true))
    }

    @Test
    fun continuousFeedBelowThreshold_doesNotTriggerFeedBreak() {
        val metrics = DashboardMetrics(
            totalScreenTimeMs = 2 * 3600 * 1000L,
            continuousFeedMinutes = 25L,
            eyeGuardTriggerCountToday = 1,
            privacyGuardAlertCountToday = 0,
            currentHourOfDay = 14
        )

        val suggestion = engine.generateSuggestion(metrics)
        assertNotEquals(SuggestionType.FEED_BREAK, suggestion.type)
    }

    @Test
    fun frequentEyeGuardTriggers_suggests20_20_20Rule() {
        val metrics = DashboardMetrics(
            totalScreenTimeMs = 2 * 3600 * 1000L,
            continuousFeedMinutes = 10L,
            eyeGuardTriggerCountToday = 6, // >= 5 threshold
            privacyGuardAlertCountToday = 0,
            currentHourOfDay = 16
        )

        val suggestion = engine.generateSuggestion(metrics)
        assertEquals(SuggestionType.EYE_REST_20_20_20, suggestion.type)
        assertTrue(suggestion.title.contains("20-20-20", ignoreCase = true))
        assertTrue(suggestion.message.contains("20 feet", ignoreCase = true))
    }

    @Test
    fun lateNightAfter11pm_suggestsWindDown() {
        val metrics = DashboardMetrics(
            totalScreenTimeMs = 1 * 3600 * 1000L,
            continuousFeedMinutes = 15L,
            eyeGuardTriggerCountToday = 1,
            privacyGuardAlertCountToday = 0,
            currentHourOfDay = 23 // 11 PM
        )

        val suggestion = engine.generateSuggestion(metrics)
        assertEquals(SuggestionType.LATE_NIGHT_WIND_DOWN, suggestion.type)
        assertTrue(suggestion.title.contains("Wind Down", ignoreCase = true))
        assertTrue(suggestion.message.contains("sleep", ignoreCase = true))
    }

    @Test
    fun lateNightBefore5am_suggestsWindDown() {
        val metrics = DashboardMetrics(
            totalScreenTimeMs = 1 * 3600 * 1000L,
            continuousFeedMinutes = 10L,
            eyeGuardTriggerCountToday = 1,
            privacyGuardAlertCountToday = 0,
            currentHourOfDay = 2 // 2 AM
        )

        val suggestion = engine.generateSuggestion(metrics)
        assertEquals(SuggestionType.LATE_NIGHT_WIND_DOWN, suggestion.type)
        assertTrue(suggestion.title.contains("Wind Down", ignoreCase = true))
    }

    @Test
    fun healthyBalancedUsage_returnsPositiveEncouragement() {
        val metrics = DashboardMetrics(
            totalScreenTimeMs = 1 * 3600 * 1000L,
            continuousFeedMinutes = 10L,
            eyeGuardTriggerCountToday = 2,
            privacyGuardAlertCountToday = 0,
            currentHourOfDay = 14 // 2 PM
        )

        val suggestion = engine.generateSuggestion(metrics)
        assertEquals(SuggestionType.HEALTHY_BALANCE, suggestion.type)
        assertTrue(suggestion.title.contains("Balance", ignoreCase = true))
        assertTrue(suggestion.message.isNotEmpty())
    }

    @Test
    fun feedBreakRule_takesPrecedenceOverLateNightRule() {
        // Even at 11:30 PM, 50 continuous minutes in a feed app should primarily trigger feed break
        val metrics = DashboardMetrics(
            totalScreenTimeMs = 2 * 3600 * 1000L,
            continuousFeedMinutes = 50L,
            eyeGuardTriggerCountToday = 1,
            privacyGuardAlertCountToday = 0,
            currentHourOfDay = 23
        )

        val suggestion = engine.generateSuggestion(metrics)
        assertEquals(SuggestionType.FEED_BREAK, suggestion.type)
    }
}
