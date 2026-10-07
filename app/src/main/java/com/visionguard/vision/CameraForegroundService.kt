// Foreground service hosting the CameraX lifecycle, offline ML Kit face detection,
// EyeGuardPolicy and PrivacyGuardPolicy state machines with Room logging and priority overlay control.
package com.visionguard.vision

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.visionguard.R
import com.visionguard.VisionGuardApp
import com.visionguard.policy.DetectedFace
import com.visionguard.policy.EyeGuardPolicy
import com.visionguard.policy.FaceObservation
import com.visionguard.policy.PrivacyGuardPolicy
import com.visionguard.policy.ProtectionState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraForegroundService : LifecycleService() {

    private val tag = "CameraForegroundService"
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector
    private val eyeGuardPolicy = EyeGuardPolicy()
    private val privacyGuardPolicy = PrivacyGuardPolicy()

    private var isPaused: Boolean = false
    private var lastAnalyzedFrameTimeMs: Long = 0L
    private var previousProtectionState: ProtectionState = ProtectionState.NO_FACE_GRACE_PERIOD
    private var previousPrivacyTriggered: Boolean = false
    private var lastWarningNotificationTimeMs: Long = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(tag, "Screen OFF detected: unbinding camera analysis")
                    unbindCameraAnalysis()
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(tag, "Screen ON detected: rebinding camera analysis")
                    if (!isPaused) {
                        val now = SystemClock.elapsedRealtime()
                        eyeGuardPolicy.reset(now)
                        privacyGuardPolicy.reset(now)
                        bindCameraAnalysis()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Pure bounding box detection without requiring eye landmarks or classifications
        val detectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        faceDetector = FaceDetection.getClient(detectorOptions)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)

        createNotificationChannel()
        startAsForeground()

        val container = VisionGuardApp.instance.container
        container.updateServiceRunning(true)
        val now = SystemClock.elapsedRealtime()
        eyeGuardPolicy.reset(now)
        privacyGuardPolicy.reset(now)
        bindCameraAnalysis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE_PROTECTION -> {
                pauseProtection()
            }
            ACTION_RESUME_PROTECTION -> {
                resumeProtection()
            }
            ACTION_DISMISS_PRIVACY -> {
                dismissPrivacyShield()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun pauseProtection() {
        if (!isPaused) {
            isPaused = true
            Log.d(tag, "Protection paused by user")
            unbindCameraAnalysis()
            val now = SystemClock.elapsedRealtime()
            VisionGuardApp.instance.container.overlayManager.hideOverlay()
            eyeGuardPolicy.reset(now)
            privacyGuardPolicy.reset(now)
            previousPrivacyTriggered = false
            VisionGuardApp.instance.container.updatePaused(true)
            VisionGuardApp.instance.container.logEvent("PAUSED", null, "Protection paused by user")
            updateNotification()
        }
    }

    private fun resumeProtection() {
        if (isPaused) {
            isPaused = false
            Log.d(tag, "Protection resumed by user")
            val now = SystemClock.elapsedRealtime()
            eyeGuardPolicy.reset(now)
            privacyGuardPolicy.reset(now)
            previousPrivacyTriggered = false
            VisionGuardApp.instance.container.updatePaused(false)
            VisionGuardApp.instance.container.logEvent("RESUMED", null, "Protection resumed by user")
            updateNotification()
            bindCameraAnalysis()
        }
    }

    private fun dismissPrivacyShield() {
        Log.d(tag, "Privacy shield dismissed by user")
        privacyGuardPolicy.dismiss()
        val container = VisionGuardApp.instance.container
        container.logEvent("PRIVACY_DISMISSED", null, "Privacy shield dismissed by user")
        val eyeDecision = container.spikeMetrics.value
        container.overlayManager.updateGuards(
            isPrivacyActive = false,
            shouldDim = eyeDecision.shouldDim,
            dimOpacity = eyeDecision.dimOpacity
        )
        updateNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.camera_service_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.camera_service_notification_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        paused: Boolean,
        isPrivacyAlert: Boolean = false,
        isWarning: Boolean = false
    ): Notification {
        val title = when {
            paused -> getString(R.string.camera_service_notification_title_paused)
            isPrivacyAlert -> getString(R.string.camera_service_notification_title_privacy)
            isWarning -> "VisionGuard: Screen Too Close!"
            else -> getString(R.string.camera_service_notification_title)
        }

        val text = when {
            paused -> getString(R.string.camera_service_notification_desc_paused)
            isPrivacyAlert -> getString(R.string.camera_service_notification_desc_privacy)
            isWarning -> "Phone is held too close. Move it back to clear dimming."
            else -> getString(R.string.camera_service_notification_desc)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(if (isPrivacyAlert) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)

        if (isPrivacyAlert) {
            // Provide click-through dismissal action in notification shade
            val dismissIntent = Intent(this, CameraForegroundService::class.java).apply {
                action = ACTION_DISMISS_PRIVACY
            }
            val dismissPendingIntent = PendingIntent.getService(
                this,
                202,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_dismiss_privacy),
                dismissPendingIntent
            )
        }

        val pauseResumeActionText = if (paused) {
            getString(R.string.notification_action_resume)
        } else {
            getString(R.string.notification_action_pause)
        }

        val pauseResumeIntent = Intent(this, CameraForegroundService::class.java).apply {
            action = if (paused) ACTION_RESUME_PROTECTION else ACTION_PAUSE_PROTECTION
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            if (paused) 201 else 200,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(
            if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            pauseResumeActionText,
            pauseResumePendingIntent
        )

        return builder.build()
    }

    private fun startAsForeground() {
        val notification = buildNotification(isPaused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(
        isPrivacyAlert: Boolean = previousPrivacyTriggered,
        isWarning: Boolean = false
    ) {
        val notification = buildNotification(isPaused, isPrivacyAlert, isWarning)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun bindCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val provider = cameraProvider ?: return@addListener

                provider.unbindAll()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                @Suppress("DEPRECATION")
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 360))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    analyzeImage(imageProxy)
                }

                provider.bindToLifecycle(this, cameraSelector, imageAnalysis)
                VisionGuardApp.instance.container.updateCameraBound(true)
                Log.d(tag, "Camera analysis bound successfully")
            } catch (e: Exception) {
                Log.e(tag, "Failed to bind camera analysis", e)
                VisionGuardApp.instance.container.updateCameraBound(false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unbindCameraAnalysis() {
        try {
            cameraProvider?.unbindAll()
            VisionGuardApp.instance.container.updateCameraBound(false)
            Log.d(tag, "Camera analysis unbound")
        } catch (e: Exception) {
            Log.e(tag, "Error unbinding camera", e)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (isPaused) {
            imageProxy.close()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val isLowPower = VisionGuardApp.instance.container.spikeMetrics.value.isPowerSaving

        // Low-power polling: when no face has been detected for >5s, throttle analysis to 1 fps
        if (isLowPower && (now - lastAnalyzedFrameTimeMs) < IDLE_FRAME_POLL_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastAnalyzedFrameTimeMs = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) {
                imageProxy.height
            } else {
                imageProxy.width
            }

            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val faceCount = faces.size
                    // Owner is the largest detected face
                    val largestFace = faces.maxByOrNull { it.boundingBox.width() }
                    val widthFraction = if (largestFace != null) {
                        EyeGuardPolicy.calculateWidthFraction(largestFace.boundingBox.width(), uprightWidth)
                    } else {
                        0f
                    }
                    val yaw = largestFace?.headEulerAngleY ?: 0f

                    // Extract secondary viewers (all other detected faces in the frame)
                    val secondaryFaces = faces
                        .filter { it != largestFace }
                        .map { otherFace ->
                            DetectedFace(
                                widthFraction = EyeGuardPolicy.calculateWidthFraction(
                                    otherFace.boundingBox.width(),
                                    uprightWidth
                                ),
                                yaw = otherFace.headEulerAngleY
                            )
                        }

                    val observation = FaceObservation(
                        widthFraction = widthFraction,
                        yaw = yaw,
                        count = faceCount,
                        timestampMs = now,
                        secondaryFaces = secondaryFaces
                    )

                    // Synchronize user settings
                    val container = VisionGuardApp.instance.container
                    eyeGuardPolicy.calibrationK = container.calibrationK.value
                    eyeGuardPolicy.tooCloseThresholdCm = container.targetThresholdCm.value

                    // Evaluate policies
                    val eyeDecision = eyeGuardPolicy.evaluate(observation, now)
                    val isPrivacyEnabled = container.isPrivacyGuardEnabled.value
                    val privacyDecision = privacyGuardPolicy.evaluate(observation, isPrivacyEnabled, now)

                    container.updateEyeGuardDecision(eyeDecision, faceCount, widthFraction)
                    container.updatePrivacyDecision(privacyDecision)

                    // PRIORITY HANDLING: Privacy Guard shield wins over Eye Guard dimming
                    val isPrivacyActive = !isPaused && privacyDecision.isTriggered && isPrivacyEnabled
                    val shouldDim = !isPaused && eyeDecision.shouldDim
                    container.overlayManager.updateGuards(
                        isPrivacyActive = isPrivacyActive,
                        shouldDim = shouldDim,
                        dimOpacity = eyeDecision.dimOpacity
                    )

                    // Privacy Guard state transitions & logging
                    if (!previousPrivacyTriggered && privacyDecision.isTriggered) {
                        previousPrivacyTriggered = true
                        container.logEvent(
                            "PRIVACY_ALERT",
                            null,
                            "Secondary viewer detected (viewers: ${privacyDecision.qualifyingViewerCount})"
                        )
                        updateNotification(isPrivacyAlert = true)
                    } else if (previousPrivacyTriggered && !privacyDecision.isTriggered) {
                        previousPrivacyTriggered = false
                        container.logEvent("PRIVACY_RECOVERED", null, "Secondary viewer no longer present (cleared after 2s)")
                        updateNotification(isPrivacyAlert = false)
                    }

                    // Eye Guard state transitions & logging
                    if (previousProtectionState != ProtectionState.TOO_CLOSE && eyeDecision.state == ProtectionState.TOO_CLOSE) {
                        container.logEvent(
                            "TOO_CLOSE",
                            eyeDecision.smoothedDistanceCm,
                            "Screen too close (< %.0f cm)".format(container.targetThresholdCm.value)
                        )
                        if (!privacyDecision.isTriggered && (now - lastWarningNotificationTimeMs > 4000L)) {
                            lastWarningNotificationTimeMs = now
                            updateNotification(isWarning = true)
                        }
                    } else if (previousProtectionState == ProtectionState.TOO_CLOSE && eyeDecision.state == ProtectionState.NORMAL_DISTANCE) {
                        container.logEvent("RECOVERED", eyeDecision.smoothedDistanceCm, "Safe viewing distance restored")
                        if (!privacyDecision.isTriggered) {
                            updateNotification()
                        }
                    } else if (previousProtectionState != ProtectionState.NO_FACE_DIMMED && eyeDecision.state == ProtectionState.NO_FACE_DIMMED) {
                        container.logEvent("NO_FACE_POWER_SAVING", null, "No face for >5s; dimmed at 1 fps idle")
                    }
                    previousProtectionState = eyeDecision.state
                }
                .addOnFailureListener { e ->
                    Log.e(tag, "Face detection error", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (e: Exception) {
            Log.e(tag, "Exception during image processing", e)
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            Log.w(tag, "Receiver already unregistered", e)
        }

        unbindCameraAnalysis()
        VisionGuardApp.instance.container.overlayManager.hideOverlay()
        VisionGuardApp.instance.container.updateServiceRunning(false)

        faceDetector.close()
        cameraExecutor.shutdown()
        Log.d(tag, "CameraForegroundService destroyed")
    }

    companion object {
        const val CHANNEL_ID = "visionguard_camera_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.visionguard.action.STOP_SERVICE"
        const val ACTION_PAUSE_PROTECTION = "com.visionguard.action.PAUSE_PROTECTION"
        const val ACTION_RESUME_PROTECTION = "com.visionguard.action.RESUME_PROTECTION"
        const val ACTION_DISMISS_PRIVACY = "com.visionguard.action.DISMISS_PRIVACY"
        const val IDLE_FRAME_POLL_INTERVAL_MS = 1000L
    }
}
