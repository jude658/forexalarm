package com.example.forexalarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Alert(
    val id: Long,
    val pair: String,
    val above: Boolean,
    val target: Double,
    var done: Boolean = false,
    var hit: Double = 0.0
)

object Store {
    val PAIRS = listOf(
        "EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF", "AUD/USD", "USD/CAD", "NZD/USD",
        "EUR/GBP", "EUR/JPY", "GBP/JPY", "USD/UGX", "USD/KES", "USD/ZAR"
    )

    private fun p(c: Context) = c.getSharedPreferences("fx", Context.MODE_PRIVATE)

    fun fmt(pair: String, v: Double): String {
        val d = if (pair.endsWith("JPY")) 3 else if (pair.endsWith("UGX") || pair.endsWith("KES")) 2 else 5
        return String.format(Locale.US, "%.${d}f", v)
    }

    @Synchronized fun all(c: Context): MutableList<Alert> {
        val out = mutableListOf<Alert>()
        val arr = JSONArray(p(c).getString("alerts", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Alert(o.getLong("id"), o.getString("pair"), o.getBoolean("above"),
                o.getDouble("target"), o.getBoolean("done"), o.getDouble("hit")))
        }
        return out
    }

    private fun write(c: Context, l: List<Alert>) {
        val arr = JSONArray()
        for (a in l) arr.put(JSONObject().put("id", a.id).put("pair", a.pair).put("above", a.above)
            .put("target", a.target).put("done", a.done).put("hit", a.hit))
        p(c).edit().putString("alerts", arr.toString()).apply()
    }

    @Synchronized fun add(c: Context, a: Alert) { val l = all(c); l.add(a); write(c, l) }
    @Synchronized fun remove(c: Context, id: Long) { write(c, all(c).filter { it.id != id }) }
    @Synchronized fun markDone(c: Context, id: Long, hit: Double) {
        val l = all(c)
        l.find { it.id == id }?.let { it.done = true; it.hit = hit }
        write(c, l)
    }

    fun savePrice(c: Context, pair: String, v: Double) { p(c).edit().putString("px_$pair", v.toString()).apply() }
    fun price(c: Context, pair: String): Double? = p(c).getString("px_$pair", null)?.toDoubleOrNull()
    fun monitoring(c: Context) = p(c).getBoolean("monitoring", false)
    fun setMonitoring(c: Context, on: Boolean) { p(c).edit().putBoolean("monitoring", on).apply() }
}
