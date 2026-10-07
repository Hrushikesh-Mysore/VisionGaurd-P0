// Foreground service hosting the CameraX lifecycle and offline ML Kit face detection.
// Integrates ProtectionPolicy state machine for ~20 cm proximity dimming, notification pause/resume,
// screen on/off power gating, and 5s no-face idle dimming with 1 fps low-power polling.
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
import com.visionguard.policy.ProtectionPolicy
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraForegroundService : LifecycleService() {

    private val tag = "CameraForegroundService"
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector
    private val protectionPolicy = ProtectionPolicy()

    private var isPaused: Boolean = false
    private var lastAnalyzedFrameTimeMs: Long = 0L

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
                        protectionPolicy.reset(SystemClock.elapsedRealtime())
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

        VisionGuardApp.instance.container.updateServiceRunning(true)
        protectionPolicy.reset(SystemClock.elapsedRealtime())
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
            VisionGuardApp.instance.container.overlayManager.hideOverlay()
            protectionPolicy.reset(SystemClock.elapsedRealtime())
            VisionGuardApp.instance.container.updatePaused(true)
            updateNotification()
        }
    }

    private fun resumeProtection() {
        if (isPaused) {
            isPaused = false
            Log.d(tag, "Protection resumed by user")
            protectionPolicy.reset(SystemClock.elapsedRealtime())
            VisionGuardApp.instance.container.updatePaused(false)
            updateNotification()
            bindCameraAnalysis()
        }
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

    private fun buildNotification(paused: Boolean): Notification {
        val title = if (paused) {
            getString(R.string.camera_service_notification_title_paused)
        } else {
            getString(R.string.camera_service_notification_title)
        }

        val text = if (paused) {
            getString(R.string.camera_service_notification_desc_paused)
        } else {
            getString(R.string.camera_service_notification_desc)
        }

        val actionText = if (paused) {
            getString(R.string.notification_action_resume)
        } else {
            getString(R.string.notification_action_pause)
        }

        val actionIntent = Intent(this, CameraForegroundService::class.java).apply {
            action = if (paused) ACTION_RESUME_PROTECTION else ACTION_PAUSE_PROTECTION
        }
        val actionPendingIntent = PendingIntent.getService(
            this,
            if (paused) 201 else 200,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (paused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                actionText,
                actionPendingIntent
            )
            .build()
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

    private fun updateNotification() {
        val notification = buildNotification(isPaused)
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
                    val faceDetected = faces.isNotEmpty()
                    val faceCount = faces.size
                    val largestFace = faces.maxByOrNull { it.boundingBox.width() }
                    val widthFraction = if (largestFace != null) {
                        ProtectionPolicy.calculateWidthFraction(largestFace.boundingBox.width(), uprightWidth)
                    } else {
                        0f
                    }

                    // Centralized policy evaluation (proximity hysteresis + 5s no-face dimming timeout)
                    val decision = protectionPolicy.evaluate(faceDetected, widthFraction, SystemClock.elapsedRealtime())

                    Log.d(tag, "Policy: state=${decision.state}, dim=${decision.shouldDim}, powerSave=${decision.isPowerSaving}, widthFraction=%.3f".format(widthFraction))

                    VisionGuardApp.instance.container.updatePolicyDecision(decision, faceCount, widthFraction)

                    if (!isPaused && decision.shouldDim) {
                        VisionGuardApp.instance.container.overlayManager.showOverlay()
                    } else {
                        VisionGuardApp.instance.container.overlayManager.hideOverlay()
                    }
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
        const val IDLE_FRAME_POLL_INTERVAL_MS = 1000L
    }
}
