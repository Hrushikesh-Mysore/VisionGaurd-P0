// Manages system overlay window for visual warning display across applications.
// Supports proportional closeness-based opacity capped at 0.8 to ensure touch pass-through.
package com.visionguard.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpikeOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _currentOpacity = MutableStateFlow(0.0f)
    val currentOpacity: StateFlow<Float> = _currentOpacity.asStateFlow()

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @Synchronized
    fun showOverlay(opacity: Float = DEFAULT_ALPHA) {
        if (!canDrawOverlays()) return
        val clampedOpacity = opacity.coerceIn(0.1f, MAX_ALPHA)

        if (overlayView != null) {
            updateOpacity(clampedOpacity)
            return
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
            _currentOpacity.value = clampedOpacity
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun updateOpacity(opacity: Float) {
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
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            _isOverlayVisible.value = false
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
