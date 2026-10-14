package com.example.forexalarm

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val BG = 0xFF10201C.toInt()
    private val PANEL = 0xFF173029.toInt()
    private val INK = 0xFFEAF2EE.toInt()
    private val MUTE = 0xFF8FB0A4.toInt()
    private val GOLD = 0xFFF2C14E.toInt()
    private val RED = 0xFFB3402F.toInt()

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var pair: Spinner
    private lateinit var cond: Spinner
    private lateinit var target: EditText
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var monBtn: Button
    private val tick = object : Runnable { override fun run() { refresh(); ui.postDelayed(this, 2000) } }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun LinearLayout.put(v: View, top: Int = 10) {
        addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun label(s: String, size: Float = 15f, color: Int = INK) =
        TextView(this).apply { text = s; textSize = size; setTextColor(color) }
    private fun btn(s: String, bg: Int, fg: Int, onClick: () -> Unit) =
        Button(this).apply { text = s; isAllCaps = false; setBackgroundColor(bg); setTextColor(fg); setOnClickListener { onClick() } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(36), dp(20), dp(28)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; addView(root) })

        root.put(label("Forex price alarm", 26f), 0)
        status = label("", 14f, MUTE); root.put(status, 4)

        pair = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, Store.PAIRS) }
        root.put(label("Pair", 13f, MUTE), 22); root.put(pair, 0)

        cond = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("At or above", "At or below")) }
        root.put(label("Alert me when price is", 13f, MUTE), 14); root.put(cond, 0)

        target = EditText(this).apply {
            hint = "Target price, e.g. 1.0850"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(INK); setHintTextColor(MUTE)
        }
        root.put(target, 8)
        root.put(btn("Add alert", GOLD, Color.BLACK) { addAlert() }, 12)

        root.put(label("Your alerts", 18f), 28)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.put(list, 6)

        monBtn = btn("", PANEL, INK) { toggleMonitoring() }
        root.put(monBtn, 22)
        root.put(btn("Stop alarm", RED, Color.WHITE) {
            startService(Intent(this, PriceService::class.java).setAction(PriceService.ACTION_STOP_ALARM))
        }, 10)
        root.put(btn("Allow background running", PANEL, INK) { askBattery() }, 10)
    }

    private fun addAlert() {
        val t = target.text.toString().toDoubleOrNull()
        if (t == null || t <= 0) { Toast.makeText(this, "Enter a target price above zero", Toast.LENGTH_SHORT).show(); return }
        Store.add(this, Alert(System.currentTimeMillis(), pair.selectedItem as String, cond.selectedItemPosition == 0, t))
        target.setText("")
        startMonitoring(); refresh()
    }

    private fun startMonitoring() {
        Store.setMonitoring(this, true)
        startForegroundService(Intent(this, PriceService::class.java))
    }

    private fun toggleMonitoring() {
        if (Store.monitoring(this)) {
            Store.setMonitoring(this, false)
            startService(Intent(this, PriceService::class.java).setAction(PriceService.ACTION_STOP_ALL))
        } else startMonitoring()
        refresh()
    }

    private fun askBattery() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show()
        } else startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
    }

    private fun refresh() {
        val on = Store.monitoring(this)
        monBtn.text = if (on) "Monitoring is on. Tap to stop" else "Start monitoring"
        status.text = if (on) "Watching prices in the background" else "Monitoring is off. Alarms will not ring."
        list.removeAllViews()
        val all = Store.all(this)
        if (all.isEmpty()) list.put(label("No alerts yet. Add one above.", 14f, MUTE))
        for (a in all) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(PANEL); setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val now = Store.price(this, a.pair)
            val line = "${a.pair} ${if (a.above) "at or above" else "at or below"} ${Store.fmt(a.pair, a.target)}\n" +
                if (a.done) "Triggered at ${Store.fmt(a.pair, a.hit)}"
                else "Now ${if (now == null) "waiting for price" else Store.fmt(a.pair, now)}"
            row.addView(label(line), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(btn("Remove", BG, MUTE) { Store.remove(this, a.id); refresh() })
            list.put(row, 8)
        }
    }

    override fun onResume() { super.onResume(); ui.post(tick) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(tick) }
}
