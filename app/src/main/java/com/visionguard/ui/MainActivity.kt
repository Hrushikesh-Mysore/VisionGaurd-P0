// Main launcher activity hosting VisionGuard Protection and Smart Dashboard screens.
// Provides state-based bottom bar navigation, permission controls, and live multi-face detection metrics HUD.
package com.visionguard.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.visionguard.VisionGuardApp
import com.visionguard.overlay.OverlayMode
import com.visionguard.policy.EyeGuardPolicy
import com.visionguard.policy.ProtectionState
import com.visionguard.vision.CameraForegroundService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainNavTab {
    PROTECTION,
    DASHBOARD
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                var currentTab by remember { mutableStateOf(MainNavTab.PROTECTION) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab == MainNavTab.PROTECTION,
                                onClick = { currentTab = MainNavTab.PROTECTION },
                                icon = { Text("🛡️", fontSize = 18.sp) },
                                label = { Text("Protection") }
                            )
                            NavigationBarItem(
                                selected = currentTab == MainNavTab.DASHBOARD,
                                onClick = { currentTab = MainNavTab.DASHBOARD },
                                icon = { Text("📊", fontSize = 18.sp) },
                                label = { Text("Dashboard") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentTab) {
                            MainNavTab.PROTECTION -> VisionGuardHomeScreen()
                            MainNavTab.DASHBOARD -> SmartDashboardScreen()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisionGuardHomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = VisionGuardApp.instance.container
    val coroutineScope = rememberCoroutineScope()

    val metrics by container.spikeMetrics.collectAsState()
    val isOverlayShowing by container.overlayManager.isOverlayVisible.collectAsState()
    val overlayMode by container.overlayManager.overlayMode.collectAsState()
    val currentOverlayOpacity by container.overlayManager.currentOpacity.collectAsState()
    val calibrationK by container.calibrationK.collectAsState()
    val targetThresholdCm by container.targetThresholdCm.collectAsState()
    val isPrivacyGuardEnabled by container.isPrivacyGuardEnabled.collectAsState()
    val recentEvents by container.eventDao.getRecentEvents(10).collectAsState(initial = emptyList())

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var hasUsagePermission by remember {
        mutableStateOf(container.usageRepository.hasUsagePermission())
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasUsagePermission = container.usageRepository.hasUsagePermission()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    val allPermissionsGranted = hasCameraPermission && hasOverlayPermission && hasNotificationPermission

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "VisionGuard",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Prominent Privacy Alert Banner (Takes Priority)
        if (metrics.isPrivacyAlertActive) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚠️ Privacy Alert: Shoulder Surfer Detected!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A second person is viewing your screen. Frosted privacy shield is active to protect your data.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val dismissIntent = Intent(context, CameraForegroundService::class.java).apply {
                                action = CameraForegroundService.ACTION_DISMISS_PRIVACY
                            }
                            context.startService(dismissIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Dismiss Privacy Shield")
                    }
                }
            }
        } else if (metrics.isTooClose) {
            // Eye Guard Warning Banner when too close
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "⚠️ Screen Too Close!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Phone is held closer than %.0f cm. Screen has dimmed proportionally. Hold phone further away to restore normal brightness."
                            .format(targetThresholdCm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Status Card
        val statusContainerColor = when {
            !metrics.isServiceRunning -> MaterialTheme.colorScheme.surfaceVariant
            metrics.isPaused -> MaterialTheme.colorScheme.tertiaryContainer
            metrics.isPrivacyAlertActive -> MaterialTheme.colorScheme.errorContainer
            metrics.isTooClose -> MaterialTheme.colorScheme.errorContainer
            metrics.isNoFaceDimmed -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = statusContainerColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = when {
                        !metrics.isServiceRunning -> "Protection Stopped"
                        metrics.isPaused -> "Protection Paused"
                        metrics.isPrivacyAlertActive -> "Privacy Alert: Shoulder Surfer Detected!"
                        metrics.isTooClose -> "Eye Guard: Too Close! (< %.0f cm)".format(targetThresholdCm)
                        metrics.isNoFaceDimmed -> "No Face (>5s) - Screen Dimmed"
                        metrics.isPowerSaving -> "Protection Active (Power Saving)"
                        else -> "Protection Active"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        !metrics.isServiceRunning -> "Tap Start to launch camera background monitor."
                        metrics.isPaused -> "Protection paused via notification or app. Overlays are inactive."
                        metrics.isPrivacyAlertActive -> "Second face facing screen. Frosted privacy shield active."
                        metrics.isTooClose -> "Phone is within %.0f cm. Screen dimmed proportionally.".format(targetThresholdCm)
                        metrics.isNoFaceDimmed -> "No face seen for >5s. Screen dimmed and analysis throttled to 1 fps."
                        metrics.isPowerSaving -> "No face seen for >5s. Frame analysis throttled to 1 fps to save battery."
                        metrics.isCameraBound -> "Camera active. Eye Guard (~%.0f cm) & Privacy Guard active.".format(targetThresholdCm)
                        else -> "Camera paused (screen off or background gating)."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Primary Control: Start / Stop Button
        Button(
            onClick = {
                if (!metrics.isServiceRunning) {
                    val serviceIntent = Intent(context, CameraForegroundService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    val stopIntent = Intent(context, CameraForegroundService::class.java).apply {
                        action = CameraForegroundService.ACTION_STOP_SERVICE
                    }
                    context.startService(stopIntent)
                }
            },
            enabled = allPermissionsGranted || metrics.isServiceRunning,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (metrics.isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (metrics.isServiceRunning) "Stop Protection" else "Start Protection",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // In-App Pause / Resume Button
        if (metrics.isServiceRunning) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(context, CameraForegroundService::class.java).apply {
                        action = if (metrics.isPaused) {
                            CameraForegroundService.ACTION_RESUME_PROTECTION
                        } else {
                            CameraForegroundService.ACTION_PAUSE_PROTECTION
                        }
                    }
                    context.startService(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Text(
                    text = if (metrics.isPaused) "Resume Protection" else "Pause Protection (Temporary)"
                )
            }
        }

        // Privacy Guard Settings Card (Phase 2 Toggle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Privacy Guard (Shoulder Surfing)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Shields screen when a secondary viewer looks over your shoulder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPrivacyGuardEnabled,
                        onCheckedChange = { container.setPrivacyGuardEnabled(it) }
                    )
                }

                if (metrics.isPrivacyDismissed) {
                    Text(
                        text = "ℹ️ Privacy Shield currently dismissed. Will re-arm when secondary viewer departs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Distance Calibration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Distance Calibration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Hold your phone at a comfortable reading distance (~30 cm) and tap Calibrate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Current Calibration K:", style = MaterialTheme.typography.bodyMedium)
                    Text("%.2f".format(calibrationK), fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Observed Face Width:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (metrics.faceCount > 0) "%.3f".format(metrics.widthFraction) else "No face in view",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (metrics.widthFraction > 0.05f) {
                                val newK = EyeGuardPolicy.calculateCalibratedK(30.0f, metrics.widthFraction)
                                container.updateCalibrationK(newK)
                            }
                        },
                        enabled = metrics.faceCount > 0 && metrics.widthFraction > 0.05f,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Calibrate at 30 cm")
                    }

                    OutlinedButton(
                        onClick = {
                            container.updateCalibrationK(EyeGuardPolicy.DEFAULT_CALIBRATION_K)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset (K=12.0)")
                    }
                }
            }
        }

        // Distance Threshold Setting Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Eye Guard Trigger Threshold",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select when screen dimming activates (default ~20 cm).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(20f, 25f, 30f).forEach { thresholdOption ->
                        val isSelected = targetThresholdCm == thresholdOption
                        FilterChip(
                            selected = isSelected,
                            onClick = { container.updateTargetThresholdCm(thresholdOption) },
                            label = { Text("%.0f cm%s".format(thresholdOption, if (thresholdOption == 20f) " (Default)" else "")) }
                        )
                    }
                }
            }
        }

        // Live Detection Metrics Card (Debug HUD)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Live Detection Metrics (Debug HUD)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                MetricRow(label = "Total Faces Detected", value = "${metrics.faceCount}")
                MetricRow(
                    label = "Secondary Viewers",
                    value = "${metrics.secondaryViewerCount} (frames: ${metrics.secondaryConsecutiveFrames}/3)"
                )

                val privacyStatusStr = when {
                    !isPrivacyGuardEnabled -> "DISABLED"
                    metrics.isPrivacyAlertActive -> "SHIELD ACTIVE"
                    metrics.isPrivacyDismissed -> "DISMISSED (Temporary)"
                    else -> "MONITORING"
                }
                MetricRow(
                    label = "Privacy Guard State",
                    value = privacyStatusStr,
                    highlight = metrics.isPrivacyAlertActive
                )

                val widthFractionStr = if (metrics.faceCount > 0) {
                    "Raw: %.3f | EMA: %.3f".format(metrics.widthFraction, metrics.smoothedWidthFraction)
                } else {
                    "0.000 (No face)"
                }
                MetricRow(label = "Owner Face Width", value = widthFractionStr)

                val distanceEst = if (metrics.faceCount > 0) {
                    val d = metrics.estimatedDistanceCm
                    if (d != null) "~%.1f cm (approximate)".format(d) else "Unknown"
                } else {
                    "None (no face in view)"
                }
                MetricRow(label = "Est. Distance", value = distanceEst)

                MetricRow(
                    label = "Close Frame Count",
                    value = "${metrics.consecutiveCloseFrames} / ${EyeGuardPolicy.DEFAULT_CONSECUTIVE_FRAMES}"
                )

                val stateLabel = when (metrics.protectionState) {
                    ProtectionState.NORMAL_DISTANCE -> "NORMAL DISTANCE"
                    ProtectionState.TOO_CLOSE -> "TOO CLOSE (≤ %.0f cm)".format(targetThresholdCm)
                    ProtectionState.NO_FACE_GRACE_PERIOD -> "NO FACE DETECTED (Grace Period)"
                    ProtectionState.NO_FACE_DIMMED -> "NO FACE DETECTED (Dimmed / 1 fps)"
                }
                MetricRow(
                    label = "Eye Guard State",
                    value = stateLabel,
                    highlight = metrics.isTooClose || metrics.isNoFaceDimmed
                )

                val overlayLabel = when (overlayMode) {
                    OverlayMode.PRIVACY_GUARD_SHIELD -> "PRIVACY SHIELD (Frosted)"
                    OverlayMode.EYE_GUARD_DIM -> "EYE GUARD DIM (opacity: %.2f)".format(currentOverlayOpacity)
                    OverlayMode.NONE -> if (isOverlayShowing) "MANUAL (opacity: %.2f)".format(currentOverlayOpacity) else "HIDDEN"
                }
                MetricRow(
                    label = "Active Overlay Mode",
                    value = overlayLabel,
                    highlight = isOverlayShowing
                )
            }
        }

        // Recent Safety Events Card (Room DB)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Local Safety Event Ledger",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                container.eventDao.clearAll()
                            }
                        }
                    ) {
                        Text("Clear", style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()

                if (recentEvents.isEmpty()) {
                    Text(
                        text = "No events logged yet. Proximity triggers, privacy alerts, and recoveries will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    recentEvents.take(6).forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.eventType,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = when (event.eventType) {
                                        "PRIVACY_ALERT" -> MaterialTheme.colorScheme.error
                                        "TOO_CLOSE" -> MaterialTheme.colorScheme.error
                                        "PRIVACY_RECOVERED", "RECOVERED" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.secondary
                                    }
                                )
                                Text(
                                    text = event.detail,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = dateFormat.format(Date(event.timestamp)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        // Manual Overlay Test Button
        OutlinedButton(
            onClick = {
                container.overlayManager.toggleOverlay()
            },
            enabled = hasOverlayPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text(
                text = if (isOverlayShowing) "Hide Overlay Manually" else "Toggle Click-Through Overlay"
            )
        }

        // Permissions Checklist
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Required Permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                PermissionItem(
                    name = "Camera",
                    description = "Required to estimate screen viewing distance and detect viewers locally.",
                    isGranted = hasCameraPermission,
                    onRequest = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = { openAppSettings(context) }
                )

                PermissionItem(
                    name = "Display Over Other Apps",
                    description = "Enables non-intrusive dimming and frosted privacy shields.",
                    isGranted = hasOverlayPermission,
                    onRequest = { openOverlaySettings(context) },
                    onOpenSettings = { openOverlaySettings(context) }
                )

                PermissionItem(
                    name = "Usage Access",
                    description = "Required to compute foreground screen time and top apps for Smart Dashboard.",
                    isGranted = hasUsagePermission,
                    onRequest = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionItem(
                        name = "Notifications",
                        description = "Required for persistent foreground service and shield controls.",
                        isGranted = hasNotificationPermission,
                        onRequest = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        onOpenSettings = { openAppSettings(context) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun MetricRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PermissionItem(
    name: String,
    description: String,
    isGranted: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isGranted) {
            Text(
                text = "Granted",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        } else {
            Button(
                onClick = onRequest,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(text = "Grant", fontSize = 12.sp)
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    context.startActivity(intent)
}
