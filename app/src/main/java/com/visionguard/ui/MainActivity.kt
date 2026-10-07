// Main launcher activity hosting Phase 0 spike verification screen.
// Provides permission controls, service lifecycle triggers, notification pause/resume awareness,
// and live proximity metrics distinguishing between no-face, normal, and too-close states.
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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.visionguard.VisionGuardApp
import com.visionguard.policy.ProtectionPolicy
import com.visionguard.policy.ProtectionState
import com.visionguard.vision.CameraForegroundService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SpikeHomeScreen()
                }
            }
        }
    }
}

@Composable
fun SpikeHomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val container = VisionGuardApp.instance.container

    val metrics by container.spikeMetrics.collectAsState()
    val isOverlayShowing by container.overlayManager.isOverlayVisible.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
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
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "VisionGuard Spike (Phase 0)",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Status Card
        val statusContainerColor = when {
            !metrics.isServiceRunning -> MaterialTheme.colorScheme.surfaceVariant
            metrics.isPaused -> MaterialTheme.colorScheme.tertiaryContainer
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
                        metrics.isTooClose -> "Too Close! (< 20 cm)"
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
                        metrics.isPaused -> "Protection paused via notification or app. Proximity dimming is inactive."
                        metrics.isNoFaceDimmed -> "No face seen for >5s. Screen dimmed and frame analysis throttled to 1 fps."
                        metrics.isPowerSaving -> "No face seen for >5s. Frame analysis throttled to 1 fps to save battery."
                        metrics.isCameraBound -> "Camera active. Monitoring face distance (~20 cm threshold)."
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
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (metrics.isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (metrics.isServiceRunning) "Stop Protection" else "Start Protection",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // In-App Pause / Resume Button (mirrors the notification control)
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
                    .height(48.dp)
            ) {
                Text(
                    text = if (metrics.isPaused) "Resume Protection" else "Pause Protection (Temporary)"
                )
            }
        }

        // Live Detection Metrics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Live Detection Metrics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                MetricRow(label = "Faces Detected", value = "${metrics.faceCount}")

                val widthFractionStr = if (metrics.faceCount > 0) {
                    "%.3f (trigger: %.2f, recover: %.2f)".format(
                        metrics.widthFraction,
                        ProtectionPolicy.DEFAULT_TRIGGER_THRESHOLD_FRACTION,
                        ProtectionPolicy.DEFAULT_RECOVERY_THRESHOLD_FRACTION
                    )
                } else {
                    "0.000 (No face)"
                }
                MetricRow(label = "Width Fraction", value = widthFractionStr)

                val distanceEst = if (metrics.faceCount > 0) {
                    val d = metrics.estimatedDistanceCm ?: ProtectionPolicy.estimateDistanceCm(metrics.widthFraction)
                    if (d != null) "~%.0f cm (approximate)".format(d) else "Unknown"
                } else {
                    "None (no face in view)"
                }
                MetricRow(label = "Est. Distance", value = distanceEst)

                val stateLabel = when (metrics.protectionState) {
                    ProtectionState.NORMAL_DISTANCE -> "NORMAL DISTANCE"
                    ProtectionState.TOO_CLOSE -> "TOO CLOSE (≤ 20 cm)"
                    ProtectionState.NO_FACE_GRACE_PERIOD -> "NO FACE DETECTED (Grace Period)"
                    ProtectionState.NO_FACE_DIMMED -> "NO FACE DETECTED (Dimmed / 1 fps)"
                }
                MetricRow(
                    label = "Protection State",
                    value = stateLabel,
                    highlight = metrics.isTooClose || metrics.isNoFaceDimmed
                )

                val powerStateLabel = when {
                    metrics.isPaused -> "PAUSED"
                    metrics.isPowerSaving -> "POWER SAVING (1 fps idle)"
                    metrics.isServiceRunning -> "ACTIVE (Full rate)"
                    else -> "OFF"
                }
                MetricRow(label = "Analysis State", value = powerStateLabel)

                MetricRow(
                    label = "Dim Overlay",
                    value = if (isOverlayShowing) "ACTIVE (alpha 0.5)" else "HIDDEN",
                    highlight = isOverlayShowing
                )
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
                    description = "Required to estimate screen viewing distance locally.",
                    isGranted = hasCameraPermission,
                    onRequest = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = { openAppSettings(context) }
                )

                PermissionItem(
                    name = "Display Over Other Apps",
                    description = "Enables non-intrusive dimming overlay during close proximity.",
                    isGranted = hasOverlayPermission,
                    onRequest = { openOverlaySettings(context) },
                    onOpenSettings = { openOverlaySettings(context) }
                )

                PermissionItem(
                    name = "Notifications",
                    description = "Maintains persistent camera foreground service notification.",
                    isGranted = hasNotificationPermission,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenSettings = { openAppSettings(context) }
                )
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isGranted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isGranted) "GRANTED" else "REQUIRED",
                color = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!isGranted) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRequest,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "Grant", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "Settings", fontSize = 12.sp)
                }
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
