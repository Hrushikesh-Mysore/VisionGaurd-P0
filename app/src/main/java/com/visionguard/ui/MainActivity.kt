// Main launcher activity hosting Phase 0 spike verification screen.
// Provides permission controls, service lifecycle triggers, and live detection metrics.
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.visionguard.SpikeMetrics
import com.visionguard.VisionGuardApp
import com.visionguard.policy.SpikePolicy
import com.visionguard.vision.CameraForegroundService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (metrics.isServiceRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (metrics.isServiceRunning) "Protection Active" else "Protection Stopped",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        !metrics.isServiceRunning -> "Tap Start to launch camera background monitor."
                        metrics.isCameraBound -> "Camera active (monitoring face distance)."
                        else -> "Camera paused (screen off or background gating)."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Big One-Tap Start / Stop Button
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

        // Live Debug Metrics
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
                MetricRow(
                    label = "Width Fraction",
                    value = if (metrics.faceCount > 0) "%.3f (threshold: %.2f)".format(metrics.widthFraction, SpikePolicy.SPIKE_CLOSE_THRESHOLD_FRACTION) else "0.000"
                )
                MetricRow(
                    label = "Distance State",
                    value = if (metrics.isTooClose) "TOO CLOSE" else "NORMAL",
                    highlight = metrics.isTooClose
                )
                MetricRow(
                    label = "Overlay Window",
                    value = if (isOverlayShowing) "VISIBLE (alpha 0.5)" else "HIDDEN",
                    highlight = isOverlayShowing
                )
                MetricRow(
                    label = "Camera Binding",
                    value = if (metrics.isCameraBound) "BOUND & ANALYZING" else "UNBOUND"
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
