package com.example.forexalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PriceService : Service() {
    companion object {
        const val ACTION_STOP_ALARM = "forexalarm.STOP_ALARM"
        const val ACTION_STOP_ALL = "forexalarm.STOP_ALL"
        private const val POLL_SECONDS = 30
    }

    @Volatile private var running = false
    private var wake: PowerManager.WakeLock? = null
    private var player: MediaPlayer? = null
    private val main = Handler(Looper.getMainLooper())
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                stopAlarm()
                if (!running) { stopSelf(); return START_NOT_STICKY }
                return START_STICKY
            }
            ACTION_STOP_ALL -> {
                stopAlarm(); running = false
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!Store.monitoring(this)) { stopSelf(); return START_NOT_STICKY }
        nm.createNotificationChannel(NotificationChannel("monitor", "Price monitoring", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel("alarm", "Price alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(false)
        })
        val n = monitorNote("Starting")
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (!running) {
            running = true
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "forexalarm:poll").also { it.acquire() }
            Thread { loop() }.start()
        }
        return START_STICKY
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun monitorNote(text: String): Notification =
        Notification.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Forex alarm is watching prices")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp())
            .build()

    private fun loop() {
        while (running) {
            try { poll() } catch (_: Exception) { }
            var s = 0
            while (running && s < POLL_SECONDS) { Thread.sleep(1000); s++ }
        }
    }

    private fun poll() {
        val active = Store.all(this).filter { !it.done }
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        if (active.isEmpty()) { nm.notify(1, monitorNote("No active alerts. Checked $stamp")); return }
        val prices = HashMap<String, Double>()
        for (pair in active.map { it.pair }.distinct()) {
            fetch(pair)?.let { prices[pair] = it; Store.savePrice(this, pair, it) }
        }
        for (a in active) {
            val v = prices[a.pair] ?: continue
            if ((a.above && v >= a.target) || (!a.above && v <= a.target)) {
                Store.markDone(this, a.id, v)
                trigger(a, v)
            }
        }
        nm.notify(1, monitorNote("${active.size} active alert(s). Checked $stamp"))
    }

    private fun symbol(pair: String): String {
        val (b, q) = pair.split("/")
        return if (b == "USD") "$q=X" else "$b$q=X"
    }

    private fun fetch(pair: String): Double? {
        return try {
            val c = URL("https://query1.finance.yahoo.com/v8/finance/chart/${symbol(pair)}?interval=1m&range=1d")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            try {
                if (c.responseCode != 200) return null
                val body = c.inputStream.bufferedReader().readText()
                JSONObject(body).getJSONObject("chart").getJSONArray("result")
                    .getJSONObject(0).getJSONObject("meta").getDouble("regularMarketPrice")
            } finally { c.disconnect() }
        } catch (_: Exception) { null }
    }

    @Suppress("DEPRECATION")
    private fun trigger(a: Alert, v: Double) {
        startAlarm()
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PriceService::class.java).setAction(ACTION_STOP_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, "alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${a.pair} hit your target")
            .setContentText("Price is ${Store.fmt(a.pair, v)}")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openApp())
            .setFullScreenIntent(openApp(), true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop alarm", stop)
            .build()
        nm.notify(2, n)
    }

    @Synchronized private fun startAlarm() {
        stopAlarm()
        val am = getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@PriceService, uri)
                isLooping = true
                prepare(); start()
            }
        } catch (_: Exception) { }
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        main.postDelayed({ stopAlarm() }, 10 * 60 * 1000L) // safety: stop after 10 minutes
    }

    @Synchronized private fun stopAlarm() {
        main.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (_: Exception) { }
        player?.release(); player = null
        getSystemService(Vibrator::class.java)?.cancel()
        nm.cancel(2)
    }

    override fun onDestroy() {
        running = false
        stopAlarm()
        try { wake?.release() } catch (_: Exception) { }
        super.onDestroy()
    }
}
