// Manages system overlay window for visual warning display across applications.
// Supports proportional Eye Guard dimming with explanatory on-screen warnings and frosted Privacy Guard shields.
package com.visionguard.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
    private var overlayContainer: FrameLayout? = null
    private var dimLayer: View? = null

    private val _isOverlayVisible = MutableStateFlow(false)
    val isOverlayVisible: StateFlow<Boolean> = _isOverlayVisible.asStateFlow()

    private val _currentOpacity = MutableStateFlow(0.0f)
    val currentOpacity: StateFlow<Float> = _currentOpacity.asStateFlow()

    private val _overlayMode = MutableStateFlow(OverlayMode.NONE)
    val overlayMode: StateFlow<OverlayMode> = _overlayMode.asStateFlow()

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
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
     * Displays a frosted / dark privacy shield overlay with an explanatory warning banner.
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

        if (overlayContainer != null && _overlayMode.value == OverlayMode.PRIVACY_GUARD_SHIELD) {
            return
        }

        if (overlayContainer != null) {
            removeCurrentView()
        }

        val frostedAlpha = (MAX_ALPHA * 255).toInt()
        val container = FrameLayout(context)

        // Frosted background layer
        val backgroundView = View(context).apply {
            setBackgroundColor(Color.argb(frostedAlpha, 15, 23, 42))
        }
        container.addView(
            backgroundView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        dimLayer = backgroundView

        // Clear explanatory banner for privacy warning
        val bannerCard = createExplanationBanner(
            title = "🛡️ Privacy Guard Alert",
            titleColor = Color.parseColor("#EF4444"), // Red
            whatText = "A secondary viewer is looking at your screen.",
            whyText = "VisionGuard shields your screen to protect your visual privacy.",
            actionText = "Dismiss via the notification shade or in-app button."
        )
        val bannerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(48)
            leftMargin = dpToPx(16)
            rightMargin = dpToPx(16)
        }
        container.addView(bannerCard, bannerParams)

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (windowManager.isCrossWindowBlurEnabled) {
                        flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        blurBehindRadius = 45
                    }
                } catch (e: Exception) {
                    // Fall back safely without hardware blur
                }
            }
        }

        try {
            windowManager.addView(container, layoutParams)
            overlayContainer = container
            _isOverlayVisible.value = true
            _overlayMode.value = OverlayMode.PRIVACY_GUARD_SHIELD
            _currentOpacity.value = MAX_ALPHA
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Displays proportional Eye Guard dimming WITH a clear, friendly on-screen explanation warning.
     * Tells the user WHAT happened, WHY the screen dimmed, and WHAT TO DO, while remaining click-through.
     */
    @Synchronized
    fun showOverlay(opacity: Float = DEFAULT_ALPHA) {
        if (!canDrawOverlays()) return

        if (_overlayMode.value == OverlayMode.PRIVACY_GUARD_SHIELD) {
            return
        }

        val clampedOpacity = opacity.coerceIn(0.1f, MAX_ALPHA)

        if (overlayContainer != null && _overlayMode.value == OverlayMode.EYE_GUARD_DIM) {
            updateOpacity(clampedOpacity)
            return
        }

        if (overlayContainer != null) {
            removeCurrentView()
        }

        val alphaInt = (clampedOpacity * 255).toInt()
        val container = FrameLayout(context)

        // Semi-transparent black dim layer
        val backgroundView = View(context).apply {
            setBackgroundColor(Color.argb(alphaInt, 0, 0, 0))
        }
        container.addView(
            backgroundView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        dimLayer = backgroundView

        // Friendly on-screen explanation banner: WHAT, WHY, and WHAT TO DO
        val bannerCard = createExplanationBanner(
            title = "⚠️ Screen Too Close",
            titleColor = Color.parseColor("#F59E0B"), // Amber
            whatText = "You're too close to the screen.",
            whyText = "VisionGuard dims the screen to encourage a safer viewing distance.",
            actionText = "Move the phone farther away to clear dimming."
        )
        val bannerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(48)
            leftMargin = dpToPx(16)
            rightMargin = dpToPx(16)
        }
        container.addView(bannerCard, bannerParams)

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
            windowManager.addView(container, layoutParams)
            overlayContainer = container
            _isOverlayVisible.value = true
            _overlayMode.value = OverlayMode.EYE_GUARD_DIM
            _currentOpacity.value = clampedOpacity
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createExplanationBanner(
        title: String,
        titleColor: Int,
        whatText: String,
        whyText: String,
        actionText: String
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14).toFloat()
                setColor(Color.argb(235, 15, 23, 42)) // Frosted dark navy slate
                setStroke(dpToPx(1), titleColor)
            }
            background = bg

            val titleView = TextView(context).apply {
                text = title
                setTextColor(titleColor)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            }
            addView(titleView)

            val bodyView = TextView(context).apply {
                text = "$whatText\n$whyText\n💡 $actionText"
                setTextColor(Color.parseColor("#F8FAFC"))
                textSize = 12.5f
                setLineSpacing(dpToPx(2).toFloat(), 1.15f)
                setPadding(0, dpToPx(4), 0, 0)
            }
            addView(bodyView)
        }
    }

    @Synchronized
    fun updateOpacity(opacity: Float) {
        if (_overlayMode.value == OverlayMode.PRIVACY_GUARD_SHIELD) return

        val clampedOpacity = opacity.coerceIn(0.1f, MAX_ALPHA)
        _currentOpacity.value = clampedOpacity
        dimLayer?.let { view ->
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
        val container = overlayContainer ?: return
        try {
            windowManager.removeView(container)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayContainer = null
            dimLayer = null
            _isOverlayVisible.value = false
            _overlayMode.value = OverlayMode.NONE
            _currentOpacity.value = 0.0f
        }
    }

    @Synchronized
    fun toggleOverlay() {
        if (overlayContainer != null) {
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
