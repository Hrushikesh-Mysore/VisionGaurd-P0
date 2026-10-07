// Repository for aggregating Android foreground app usage statistics.
// Queries UsageStatsManager events, pairs RESUMED/PAUSED transitions, filters launcher/system, and extracts 7-day trends.
package com.visionguard.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val foregroundTimeMs: Long,
    val percentageOfTotal: Float
)

data class DailyUsageItem(
    val dayLabel: String,
    val dateMillis: Long,
    val totalTimeMs: Long,
    val isToday: Boolean
)

data class UsageAggregationResult(
    val hasPermission: Boolean,
    val todayTotalTimeMs: Long,
    val topApps: List<AppUsageItem>,
    val weeklyTrend: List<DailyUsageItem>,
    val longestContinuousFeedMinutes: Long
)

class UsageRepository(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager = context.packageManager

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun getDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    suspend fun getUsageData(): UsageAggregationResult = withContext(Dispatchers.IO) {
        if (!hasUsagePermission() || usageStatsManager == null) {
            return@withContext UsageAggregationResult(
                hasPermission = false,
                todayTotalTimeMs = 0L,
                topApps = emptyList(),
                weeklyTrend = getEmptyWeeklyTrend(),
                longestContinuousFeedMinutes = 0L
            )
        }

        val launcherPkg = getDefaultLauncherPackage()
        val ownPkg = context.packageName

        val calendar = Calendar.getInstance()
        val now = System.currentTimeMillis()

        // Today start of day (midnight)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        // 1. Query today's events and compute foreground times by pairing ACTIVITY_RESUMED and ACTIVITY_PAUSED
        val todayEvents = usageStatsManager.queryEvents(startOfToday, now)
        val todayDurations = mutableMapOf<String, Long>()
        val openResumes = mutableMapOf<String, Long>()
        var longestFeedMs = 0L

        val event = UsageEvents.Event()
        while (todayEvents.hasNextEvent()) {
            todayEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue

            // Exclude our own app and launcher
            if (pkg == ownPkg || pkg == launcherPkg || pkg == "android") continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    openResumes[pkg] = event.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = openResumes.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        val duration = event.timeStamp - start
                        todayDurations[pkg] = (todayDurations[pkg] ?: 0L) + duration
                        if (isFeedApp(pkg) && duration > longestFeedMs) {
                            longestFeedMs = duration
                        }
                    }
                }
            }
        }

        // Close any still-open sessions
        for ((pkg, start) in openResumes) {
            if (now > start) {
                val duration = now - start
                todayDurations[pkg] = (todayDurations[pkg] ?: 0L) + duration
                if (isFeedApp(pkg) && duration > longestFeedMs) {
                    longestFeedMs = duration
                }
            }
        }

        // Fallback / augmentation via queryUsageStats if queryEvents had empty intervals
        if (todayDurations.isEmpty()) {
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startOfToday,
                now
            )
            if (statsList != null) {
                for (stat in statsList) {
                    val pkg = stat.packageName ?: continue
                    if (pkg == ownPkg || pkg == launcherPkg || pkg == "android") continue
                    if (stat.totalTimeInForeground > 0) {
                        todayDurations[pkg] = stat.totalTimeInForeground
                    }
                }
            }
        }

        val totalTimeMs = todayDurations.values.sum()

        // Build top apps list (top 5)
        val topApps = todayDurations.entries
            .filter { it.value > 1000L } // At least 1 second
            .sortedByDescending { it.value }
            .take(5)
            .map { (pkg, duration) ->
                val label = try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.substringAfterLast('.')
                }
                val icon = try {
                    packageManager.getApplicationIcon(pkg)
                } catch (e: Exception) {
                    null
                }
                val fraction = if (totalTimeMs > 0) duration.toFloat() / totalTimeMs.toFloat() else 0f
                AppUsageItem(
                    packageName = pkg,
                    appName = label,
                    appIcon = icon,
                    foregroundTimeMs = duration,
                    percentageOfTotal = fraction.coerceIn(0f, 1f)
                )
            }

        // 2. Compute 7-day trend
        val weeklyTrend = compute7DayTrend(usageStatsManager, ownPkg, launcherPkg)

        UsageAggregationResult(
            hasPermission = true,
            todayTotalTimeMs = totalTimeMs,
            topApps = topApps,
            weeklyTrend = weeklyTrend,
            longestContinuousFeedMinutes = longestFeedMs / (60 * 1000L)
        )
    }

    private fun compute7DayTrend(
        manager: UsageStatsManager,
        ownPkg: String,
        launcherPkg: String?
    ): List<DailyUsageItem> {
        val result = mutableListOf<DailyUsageItem>()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // Iterate backwards from 6 days ago up to today (7 days total)
        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dayStart = dayCal.timeInMillis
            val dayEnd = if (i == 0) System.currentTimeMillis() else dayStart + 24 * 3600 * 1000L

            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
            var dayTotal = 0L
            if (stats != null) {
                for (stat in stats) {
                    val pkg = stat.packageName ?: continue
                    if (pkg == ownPkg || pkg == launcherPkg || pkg == "android") continue
                    dayTotal += stat.totalTimeInForeground
                }
            }

            result.add(
                DailyUsageItem(
                    dayLabel = dayFormat.format(dayCal.time),
                    dateMillis = dayStart,
                    totalTimeMs = dayTotal,
                    isToday = (i == 0)
                )
            )
        }

        return result
    }

    private fun getEmptyWeeklyTrend(): List<DailyUsageItem> {
        val result = mutableListOf<DailyUsageItem>()
        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val cal = Calendar.getInstance()

        for (i in 6 downTo 0) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.DAY_OF_YEAR, -i)
            }
            result.add(
                DailyUsageItem(
                    dayLabel = dayFormat.format(dayCal.time),
                    dateMillis = dayCal.timeInMillis,
                    totalTimeMs = 0L,
                    isToday = (i == 0)
                )
            )
        }
        return result
    }

    companion object {
        fun isFeedApp(packageName: String): Boolean {
            val lower = packageName.lowercase()
            return lower.contains("youtube") ||
                    lower.contains("instagram") ||
                    lower.contains("twitter") ||
                    lower.contains("tiktok") ||
                    lower.contains("reddit") ||
                    lower.contains("facebook") ||
                    lower.contains("snapchat") ||
                    lower.contains("netflix") ||
                    lower.contains("reels") ||
                    lower.contains("shorts")
        }
    }
}
