// Manages system overlay window for visual warning display across applications.
// Ensures overlay is non-intrusive, fully click-through, and conforms to Android security constraints.
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

class SpikeOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @Synchronized
    fun showOverlay() {
        if (!canDrawOverlays()) return
        if (overlayView != null) return

        val view = View(context).apply {
            // Semi-transparent black (alpha ~ 0.5, well below Android 12 touch pass-through limit of 0.8)
            setBackgroundColor(Color.argb(128, 0, 0, 0))
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
        } catch (e: Exception) {
            e.printStackTrace()
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
        }
    }

    @Synchronized
    fun toggleOverlay() {
        if (overlayView != null) {
            hideOverlay()
        } else {
            showOverlay()
        }
    }
}
