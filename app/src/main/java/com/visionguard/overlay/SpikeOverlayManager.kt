// Manages system overlay window for visual warning display across applications.
// Supports proportional Eye Guard dimming and frosted Privacy Guard shield with blur-behind on API 31+.
package com.visionguard.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlayMode {
    NONE,
    EYE_GUARD_DIM,
    PRIVACY_GUARD_SHIELD
}

class SpikeOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _currentOpacity = MutableStateFlow(0.0f)
    val currentOpacity: StateFlow<Float> = _currentOpacity.asStateFlow()

    private val _overlayMode = MutableStateFlow(OverlayMode.NONE)
    val overlayMode: StateFlow<OverlayMode> = _overlayMode.asStateFlow()

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Updates overlay based on guard priorities.
     * PRIORITY RULE: Privacy Guard shield takes precedence over Eye Guard dimming.
     * When a secondary viewer is detected, screen content must be shielded immediately.
     */
    @Synchronized
    fun updateGuards(isPrivacyActive: Boolean, shouldDim: Boolean, dimOpacity: Float) {
        if (!canDrawOverlays()) return

        when {
            isPrivacyActive -> showPrivacyOverlay()
            shouldDim -> showOverlay(dimOpacity)
            else -> hideOverlay()
        }
    }

    /**
     * Displays a frosted / dark privacy shield overlay.
     * On API 31+, applies FLAG_BLUR_BEHIND with blurBehindRadius when cross-window blur is enabled.
     *
     * TRADE-OFF EXPLANATION:
     * The privacy overlay is configured with FLAG_NOT_TOUCHABLE so it remains click-through,
     * allowing the user to continue interacting with underlying apps without being blocked.
     * Because the overlay does not capture touch events, dismissal cannot be performed by
     * tapping the overlay directly; instead, dismissal is provided via the persistent
     * notification action and an in-app button.
     */
    @Synchronized
    fun showPrivacyOverlay() {
        if (!canDrawOverlays()) return

        // If already showing privacy shield, nothing to change
        if (overlayView != null && _overlayMode.value == OverlayMode.PRIVACY_GUARD_SHIELD) {
            return
        }

        // Clean up previous overlay if it was in another mode
        if (overlayView != null) {
            removeCurrentView()
        }

        val frostedAlpha = (MAX_ALPHA * 255).toInt() // 80% opacity to preserve touch pass-through
        val view = View(context).apply {
            setBackgroundColor(Color.argb(frostedAlpha, 15, 23, 42)) // Frosted dark navy slate
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START

            // API 31+ hardware cross-window blur support
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (windowManager.isCrossWindowBlurEnabled) {
                        flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        blurBehindRadius = 45
                    }
                } catch (e: Exception) {
                    // Fall back to dark frosted color without blur
                }
            }
        }

        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
            _isOverlayVisible.value = true
            _overlayMode.value = OverlayMode.PRIVACY_GUARD_SHIELD
            _currentOpacity.value = MAX_ALPHA
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun showOverlay(opacity: Float = DEFAULT_ALPHA) {
        if (!canDrawOverlays()) return

        // If privacy shield is active, it wins over eye guard dimming
        if (_overlayMode.value == OverlayMode.PRIVACY_GUARD_SHIELD) {
            return
        }

        val clampedOpacity = opacity.coerceIn(0.1f, MAX_ALPHA)

        if (overlayView != null && _overlayMode.value == OverlayMode.EYE_GUARD_DIM) {
            updateOpacity(clampedOpacity)
            return
        }

        if (overlayView != null) {
            removeCurrentView()
        }

        val alphaInt = (clampedOpacity * 255).toInt()
        val view = View(context).apply {
            setBackgroundColor(Color.argb(alphaInt, 0, 0, 0))
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
            _isOverlayVisible.value = true
            _overlayMode.value = OverlayMode.EYE_GUARD_DIM
            _currentOpacity.value = clampedOpacity
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun updateOpacity(opacity: Float) {
        if (_overlayMode.value == OverlayMode.PRIVACY_GUARD_SHIELD) return

        val clampedOpacity = opacity.coerceIn(0.1f, MAX_ALPHA)
        _currentOpacity.value = clampedOpacity
        overlayView?.let { view ->
            val alphaInt = (clampedOpacity * 255).toInt()
            view.setBackgroundColor(Color.argb(alphaInt, 0, 0, 0))
        } ?: run {
            if (_isOverlayVisible.value) {
                showOverlay(clampedOpacity)
            }
        }
    }

    @Synchronized
    fun hideOverlay() {
        removeCurrentView()
    }

    private fun removeCurrentView() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            _isOverlayVisible.value = false
            _overlayMode.value = OverlayMode.NONE
            _currentOpacity.value = 0.0f
        }
    }

    @Synchronized
    fun toggleOverlay() {
        if (overlayView != null) {
            hideOverlay()
        } else {
            showOverlay(DEFAULT_ALPHA)
        }
    }

    companion object {
        const val DEFAULT_ALPHA = 0.50f
        const val MAX_ALPHA = 0.80f // Android 12+ touch pass-through limit
    }
}
