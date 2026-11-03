package app.orientation

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class MainActivity : Activity() {

    private class AppItem(val pkg: String, val label: String)

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var enableBtn: Button
    private lateinit var adapter: AppAdapter
    private var allApps: List<AppItem> = emptyList()

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val s = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(s.left + px(16), s.top + px(12), s.right + px(16), s.bottom + px(8))
            insets
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
        }
        status = TextView(this).apply { setPadding(0, px(8), 0, px(8)) }
        enableBtn = Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        val search = EditText(this).apply {
            hint = "Search apps"
            isSingleLine = true
        }
        val list = ListView(this)
        adapter = AppAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ -> choose(adapter.getItem(pos)) }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString() ?: "")
            }
        })

        root.addView(title)
        root.addView(status)
        root.addView(enableBtn)
        root.addView(search)
        root.addView(
            list,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
                .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                .filter { it.pkg != packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                allApps = apps
                adapter.filter("")
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val on = serviceEnabled()
        status.text = if (on) {
            "✔ Service active. Tap an app to choose its orientation."
        } else {
            "Service is off. Go to Accessibility > Installed apps and enable \"Orientation per App\".\n" +
                "If it appears greyed out: App info > ⋮ > Allow restricted settings."
        }
        enableBtn.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(this, OrientationService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun choose(item: AppItem) {
        val options = arrayOf("Automatic (follow system)", "Always landscape", "Always portrait")
        AlertDialog.Builder(this)
            .setTitle(item.label)
            .setSingleChoiceItems(options, Prefs.get(prefs, item.pkg)) { d, which ->
                Prefs.set(prefs, item.pkg, which)
                adapter.notifyDataSetChanged()
                d.dismiss()
            }
            .show()
    }

    private inner class AppAdapter : BaseAdapter() {
        private var shown: List<AppItem> = emptyList()

        fun filter(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) }
            notifyDataSetChanged()
        }

        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = shown[position]
            val ctx = this@MainActivity
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(8), 0, px(8))
            }
            val icon = ImageView(ctx).apply {
                setImageDrawable(runCatching { packageManager.getApplicationIcon(item.pkg) }.getOrNull())
            }
            row.addView(icon, LinearLayout.LayoutParams(px(40), px(40)))

            val name = TextView(ctx).apply {
                text = item.label
                textSize = 16f
                setPadding(px(12), 0, px(12), 0)
            }
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val mode = Prefs.get(prefs, item.pkg)
            val badge = TextView(ctx).apply {
                text = when (mode) {
                    Prefs.LANDSCAPE -> "↔ Landscape"
                    Prefs.PORTRAIT -> "↕ Portrait"
                    else -> "Auto"
                }
                setTypeface(null, if (mode == Prefs.AUTO) Typeface.NORMAL else Typeface.BOLD)
            }
            row.addView(badge)
            return row
        }
    }
}
