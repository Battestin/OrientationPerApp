package app.orientation

import android.content.SharedPreferences

object Prefs {
    const val NAME = "orientation"

    // Values match the order of the options in the selection dialog.
    const val AUTO = 0
    const val LANDSCAPE = 1
    const val PORTRAIT = 2

    fun get(p: SharedPreferences, pkg: String): Int = p.getInt(pkg, AUTO)

    fun set(p: SharedPreferences, pkg: String, mode: Int) {
        p.edit().apply {
            if (mode == AUTO) remove(pkg) else putInt(pkg, mode)
        }.apply()
    }
}
