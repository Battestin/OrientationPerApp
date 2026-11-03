package app.orientation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Finds the top-most application window (the home screen counts too) and, only if
 * that app is configured, adds an invisible accessibility overlay that requests the
 * chosen orientation. For any other app the overlay is removed, so the system goes
 * back to following the normal auto-rotate setting.
 */
class OrientationService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var overlay: View? = null
    private var current = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        evaluate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        evaluate()
    }

    private fun foregroundPackage(): String? {
        val all = windows ?: return null
        val apps = all.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        // Prefer the active/focused app window (matters in split screen).
        val top = apps.firstOrNull { it.isActive || it.isFocused } ?: apps.firstOrNull()
        return top?.root?.packageName?.toString()
    }

    private fun evaluate() {
        val pkg = foregroundPackage() ?: return
        val target = when (Prefs.get(prefs, pkg)) {
            Prefs.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            Prefs.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        applyOrientation(target)
    }

    private fun applyOrientation(orientation: Int) {
        if (orientation == current) return
        current = orientation

        if (orientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            removeOverlay()
            return
        }

        val lp = WindowManager.LayoutParams(
            0, 0,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.screenOrientation = orientation

        val existing = overlay
        if (existing == null) {
            val v = View(this)
            wm.addView(v, lp)
            overlay = v
        } else {
            wm.updateViewLayout(existing, lp)
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        removeOverlay()
        current = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}
