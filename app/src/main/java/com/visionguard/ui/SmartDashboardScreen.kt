// Smart Dashboard Jetpack Compose screen with profile-aware usage attribution and PIN security.
// Displays hero screen time, circular progress, 7-day trend, top apps, protection stats, and rule-based suggestions.
package com.visionguard.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionguard.VisionGuardApp
import com.visionguard.policy.DashboardMetrics
import com.visionguard.policy.PinVerificationResult
import com.visionguard.policy.UserProfile
import com.visionguard.usage.AppUsageItem
import com.visionguard.usage.DailyUsageItem
import com.visionguard.usage.UsageAggregationResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SmartDashboardScreen() {
    val context = LocalContext.current
    val container = VisionGuardApp.instance.container
    val coroutineScope = rememberCoroutineScope()

    val activeProfile by container.activeProfile.collectAsState()
    val dailyGoalHours by container.dailyGoalHours.collectAsState()
    val isSecureModeEnabled by container.isSecureModeEnabled.collectAsState()

    val startOfToday = remember { container.getStartOfTodayMillis() }
    val eyeGuardCount by container.eventDao.getCountSince("TOO_CLOSE", startOfToday).collectAsState(initial = 0)
    val privacyGuardCount by container.eventDao.getCountSince("PRIVACY_ALERT", startOfToday).collectAsState(initial = 0)

    var usageResult by remember { mutableStateOf<UsageAggregationResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showProfileDialog by remember { mutableStateOf(false) }

    fun refreshUsage() {
        coroutineScope.launch {
            isLoading = true
            usageResult = container.usageRepository.getUsageData(
                targetProfile = activeProfile,
                switchRecords = container.profileSwitchRecords
            )
            isLoading = false
        }
    }

    LaunchedEffect(activeProfile) {
        refreshUsage()
    }

    // Synchronize FLAG_SECURE on Activity window
    val activity = context as? Activity
    LaunchedEffect(isSecureModeEnabled) {
        activity?.let { act ->
            if (isSecureModeEnabled) {
                act.window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val todayDateStr = remember { dateFormat.format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header: Date and Profile Chip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Smart Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = todayDateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AssistChip(
                onClick = { showProfileDialog = true },
                label = {
                    Text(
                        text = if (activeProfile == UserProfile.CHILD) "👶 Child (Protected)" else "👤 Parent",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (activeProfile == UserProfile.CHILD) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }

        // Child Profile Notice
        if (activeProfile == UserProfile.CHILD) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Child Profile active. Usage is attributed to Child. Settings and limits are locked by Parent PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        // Empty state / Permission request card if Usage Access is not granted
        val hasPermission = usageResult?.hasPermission ?: false
        if (!hasPermission && !isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📊 Usage Access Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "VisionGuard calculates screen time entirely on-device using local Android statistics. Grant usage access to view today's total, top apps, and digital wellbeing recommendations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Grant Usage Access")
                    }
                }
            }
        }

        val totalScreenTimeMs = usageResult?.todayTotalTimeMs ?: 0L
        val goalMs = (dailyGoalHours * 3600 * 1000L).toLong()
        val goalProgress = if (goalMs > 0) (totalScreenTimeMs.toFloat() / goalMs.toFloat()).coerceIn(0f, 1f) else 0f

        // 2. Hero Card: Screen Time & Circular Progress Ring
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeProfile == UserProfile.CHILD) "Child Screen Time Today" else "Today's Screen Time",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatDuration(totalScreenTimeMs),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Daily Goal: %.0f hours (%d%% used)".format(dailyGoalHours, (goalProgress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                // Custom Canvas Circular Progress Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp)
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)

                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = primaryColor,
                            startAngle = -90f,
                            sweepAngle = goalProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    Text(
                        text = "%d%%".format((goalProgress * 100).toInt()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Daily Goal Selector Chips (Locked if Child)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Goal:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            if (activeProfile == UserProfile.CHILD) {
                FilterChip(
                    selected = true,
                    onClick = { /* Locked for child */ },
                    label = { Text("%.0fh (Parent Lock)".format(dailyGoalHours)) },
                    enabled = false
                )
            } else {
                listOf(2f, 3f, 4f, 6f).forEach { hours ->
                    FilterChip(
                        selected = dailyGoalHours == hours,
                        onClick = { container.setDailyGoalHours(hours) },
                        label = { Text("%.0fh%s".format(hours, if (hours == 4f) " (Default)" else "")) }
                    )
                }
            }
        }

        // 3. Protection Today Cards (Eye Guard & Privacy Guard)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "👁️ Eye Guard", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$eyeGuardCount reminders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (eyeGuardCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Safe distance prompts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "🛡️ Privacy Guard", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$privacyGuardCount alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (privacyGuardCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "Shoulder-surfers blocked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 4. Suggestion Card from Pure Kotlin SuggestionEngine
        val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
        val suggestion = remember(totalScreenTimeMs, usageResult, eyeGuardCount, privacyGuardCount, currentHour) {
            container.suggestionEngine.generateSuggestion(
                DashboardMetrics(
                    totalScreenTimeMs = totalScreenTimeMs,
                    continuousFeedMinutes = usageResult?.longestContinuousFeedMinutes ?: 0L,
                    eyeGuardTriggerCountToday = eyeGuardCount,
                    privacyGuardAlertCountToday = privacyGuardCount,
                    currentHourOfDay = currentHour
                )
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💡 ${suggestion.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = suggestion.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // 5. 7-Day Trend Chart
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "7-Day Usage Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                val trend = usageResult?.weeklyTrend ?: emptyList()
                if (trend.isNotEmpty()) {
                    WeeklyTrendChart(trend = trend, goalMs = goalMs)
                } else {
                    Text(
                        text = "Trend will appear after usage access is granted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 6. Top Apps Breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Top Apps Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                val topApps = usageResult?.topApps ?: emptyList()
                if (topApps.isEmpty()) {
                    Text(
                        text = if (!hasPermission) "Grant usage access to see per-app breakdown." else "No active application usage recorded yet today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    topApps.forEach { appItem ->
                        AppUsageRow(item = appItem)
                    }
                }
            }
        }

        // 7. Security Setting: FLAG_SECURE (Off by default)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Screenshot Shield (FLAG_SECURE)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Blocks screenshots and app switcher recording (off by default for pitch recording).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSecureModeEnabled,
                    onCheckedChange = { container.setSecureModeEnabled(it) },
                    enabled = activeProfile == UserProfile.PARENT // Locked for child
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Profile Switch & PIN Authentication Dialog
    if (showProfileDialog) {
        ProfileSwitchModal(
            currentProfile = activeProfile,
            onDismiss = { showProfileDialog = false },
            onSwitch = { targetProfile ->
                container.switchProfile(targetProfile)
                showProfileDialog = false
            }
        )
    }
}

@Composable
fun ProfileSwitchModal(
    currentProfile: UserProfile,
    onDismiss: () -> Unit,
    onSwitch: (UserProfile) -> Unit
) {
    val container = VisionGuardApp.instance.container
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isSettingPin by remember { mutableStateOf(!container.isParentPinSet()) }
    var confirmPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSettingPin) "Set Parent PIN" else "Profile Management",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Current Active Profile: ${currentProfile.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Application-level profile separation. (Note: Android OS does not provide true multi-user separation to third-party apps).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isSettingPin) {
                    Text(
                        text = "Set a 4-digit Parent PIN to secure settings and profile switching (stored via PBKDF2-HMAC-SHA256 with 100,000 iterations).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { if (it.length <= 4) enteredPin = it },
                        label = { Text("4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 4) confirmPin = it },
                        label = { Text("Confirm 4-Digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (currentProfile == UserProfile.CHILD) {
                    Text(
                        text = "Enter Parent PIN to switch to Parent profile:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { if (it.length <= 4) enteredPin = it },
                        label = { Text("Parent PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Switching to Child profile will enforce 2h limit, 25 cm proximity threshold, and lock all settings.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                pinError?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSettingPin) {
                        if (enteredPin.length < 4) {
                            pinError = "PIN must be at least 4 digits."
                        } else if (enteredPin != confirmPin) {
                            pinError = "PINs do not match."
                        } else {
                            container.setParentPin(enteredPin)
                            isSettingPin = false
                            pinError = null
                        }
                    } else if (currentProfile == UserProfile.CHILD) {
                        when (val res = container.verifyParentPin(enteredPin)) {
                            is PinVerificationResult.Success -> {
                                onSwitch(UserProfile.PARENT)
                            }
                            is PinVerificationResult.LockedOut -> {
                                pinError = "Locked out. Try again in ${res.remainingSeconds}s."
                            }
                            is PinVerificationResult.Failed -> {
                                pinError = if (res.isLockedOut) "Too many failed attempts. Locked out for ${res.lockoutDurationSeconds}s."
                                else "Incorrect PIN. ${res.attemptsRemaining} attempts left."
                            }
                            is PinVerificationResult.PinNotConfigured -> {
                                isSettingPin = true
                            }
                        }
                    } else {
                        // Switching from Parent to Child
                        onSwitch(UserProfile.CHILD)
                    }
                }
            ) {
                Text(
                    text = when {
                        isSettingPin -> "Save PIN"
                        currentProfile == UserProfile.CHILD -> "Authenticate & Switch"
                        else -> "Switch to Child Profile"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun WeeklyTrendChart(trend: List<DailyUsageItem>, goalMs: Long) {
    val maxUsageMs = (trend.maxOfOrNull { it.totalTimeMs } ?: goalMs).coerceAtLeast(goalMs).coerceAtLeast(1000L)
    val primaryColor = MaterialTheme.colorScheme.primary
    val defaultBarColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        trend.forEach { item ->
            val heightFraction = (item.totalTimeMs.toFloat() / maxUsageMs.toFloat()).coerceIn(0.04f, 1.0f)
            val barHeight = (heightFraction * 85).dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                val hours = item.totalTimeMs.toFloat() / (3600 * 1000f)
                val durationText = if (item.totalTimeMs > 0) "%.1fh".format(hours) else "0h"
                Text(
                    text = durationText,
                    fontSize = 10.sp,
                    color = if (item.isToday) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (item.isToday) primaryColor else defaultBarColor)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.dayLabel,
                    fontSize = 11.sp,
                    fontWeight = if (item.isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (item.isToday) primaryColor else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun AppUsageRow(item: AppUsageItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bitmap = remember(item.packageName) {
            item.appIcon?.toBitmapSafe()
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = item.appName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.appName.firstOrNull()?.toString()?.uppercase() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatDuration(item.foregroundTimeMs),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { item.percentageOfTotal },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }
    }
}

fun formatDuration(timeMs: Long): String {
    val totalMinutes = timeMs / (60 * 1000)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

fun Drawable.toBitmapSafe(): Bitmap? {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap
    }
    return try {
        val width = if (intrinsicWidth > 0) intrinsicWidth else 48
        val height = if (intrinsicHeight > 0) intrinsicHeight else 48
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    } catch (e: Exception) {
        null
    }
}
