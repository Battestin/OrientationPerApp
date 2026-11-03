package app.orientation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Watches which app is in the foreground and, if that app is configured,
 * adds an invisible accessibility overlay window that requests the desired
 * screen orientation. The system applies the top window's requested
 * orientation to the whole display, independent of the auto-rotate setting.
 */
class OrientationService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var overlay: View? = null
    private var current = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var ignored: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        val ime = getSystemService(InputMethodManager::class.java)
            .inputMethodList.map { it.packageName }
        // The keyboard, status bar/notification shade and system dialogs
        // must not change the orientation.
        ignored = (ime + listOf("com.android.systemui", "android", packageName)).toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return

        val target = when (Prefs.get(prefs, pkg)) {
            Prefs.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            Prefs.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ->
                // Regular app (or the home screen): go back to normal behavior.
                // Packages without a launcher icon (system popups) are ignored.
                if (packageManager.getLaunchIntentForPackage(pkg) != null)
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                else return
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
