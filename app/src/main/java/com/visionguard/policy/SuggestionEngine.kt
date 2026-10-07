// Pure Kotlin suggestion engine providing rule-based digital wellbeing recommendations.
// Evaluates continuous feed sessions, eye fatigue triggers, late-night usage, and healthy balance.
package com.visionguard.policy

data class DashboardMetrics(
    val totalScreenTimeMs: Long,
    val continuousFeedMinutes: Long = 0L,
    val eyeGuardTriggerCountToday: Int = 0,
    val privacyGuardAlertCountToday: Int = 0,
    val currentHourOfDay: Int = 12 // 0..23
)

enum class SuggestionType {
    FEED_BREAK,
    EYE_REST_20_20_20,
    LATE_NIGHT_WIND_DOWN,
    HEALTHY_BALANCE
}

data class DashboardSuggestion(
    val type: SuggestionType,
    val title: String,
    val message: String
)

class SuggestionEngine(
    val continuousFeedThresholdMinutes: Long = DEFAULT_CONTINUOUS_FEED_MINUTES,
    val frequentEyeGuardThreshold: Int = DEFAULT_FREQUENT_EYE_GUARD_COUNT,
    val lateNightStartHour: Int = DEFAULT_LATE_NIGHT_HOUR,
    val lateNightEndHour: Int = DEFAULT_LATE_NIGHT_END_HOUR,
    private val clock: Clock? = null
) {
    companion object {
        const val DEFAULT_CONTINUOUS_FEED_MINUTES = 40L
        const val DEFAULT_FREQUENT_EYE_GUARD_COUNT = 5
        const val DEFAULT_LATE_NIGHT_HOUR = 23
        const val DEFAULT_LATE_NIGHT_END_HOUR = 5
    }

    fun generateSuggestion(metrics: DashboardMetrics): DashboardSuggestion {
        // Rule 1: 40+ continuous minutes in a feed/social/video app
        if (metrics.continuousFeedMinutes >= continuousFeedThresholdMinutes) {
            return DashboardSuggestion(
                type = SuggestionType.FEED_BREAK,
                title = "Time for a Screen Break",
                message = "You've been in feed & video apps for ${metrics.continuousFeedMinutes} continuous minutes. How about a quick walk, stretch, or physical book?"
            )
        }

        // Rule 2: Frequent Eye Guard triggers suggests the 20-20-20 rule
        if (metrics.eyeGuardTriggerCountToday >= frequentEyeGuardThreshold) {
            return DashboardSuggestion(
                type = SuggestionType.EYE_REST_20_20_20,
                title = "Practice the 20-20-20 Rule",
                message = "Eye Guard has triggered ${metrics.eyeGuardTriggerCountToday} times today. To prevent eye strain, look at an object 20 feet away for 20 seconds every 20 minutes."
            )
        }

        // Rule 3: Use after 11 PM suggests winding down
        if (metrics.currentHourOfDay >= lateNightStartHour || metrics.currentHourOfDay < lateNightEndHour) {
            return DashboardSuggestion(
                type = SuggestionType.LATE_NIGHT_WIND_DOWN,
                title = "Time to Wind Down",
                message = "Late-night screen light disrupts natural sleep cycles. Consider setting your phone aside for restorative rest."
            )
        }

        // Rule 4: Otherwise a positive message encouraging healthy balance
        val hours = metrics.totalScreenTimeMs / (1000 * 60 * 60)
        return DashboardSuggestion(
            type = SuggestionType.HEALTHY_BALANCE,
            title = "Healthy Digital Balance",
            message = if (hours < 2) {
                "Great posture and mindful screen time today! Keep up the healthy habits."
            } else {
                "Balanced screen habits so far today. Remember to blink regularly and take periodic pauses."
            }
        )
    }
}
