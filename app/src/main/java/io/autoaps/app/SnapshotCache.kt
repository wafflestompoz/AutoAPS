package io.autoaps.app

import android.content.Context
import io.autoaps.core.GlucoseUnit
import io.autoaps.core.NightscoutSnapshot
import io.autoaps.core.TempBasal
import io.autoaps.core.Trend

/**
 * The last good Nightscout reading, shared by the car screen and the widget so each can
 * show something immediately. Stored on this phone only and excluded from backup.
 */
class SnapshotCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("autoaps_snapshot", Context.MODE_PRIVATE)

    fun save(s: NightscoutSnapshot, error: String? = null) {
        prefs.edit().clear().apply {
            s.glucoseMgdl?.let { putFloat("bg", it.toFloat()) }
            s.readingTimeMillis?.let { putLong("time", it) }
            putString("trend", s.trend.name)
            s.deltaMgdl?.let { putFloat("delta", it.toFloat()) }
            s.iobUnits?.let { putFloat("iob", it.toFloat()) }
            s.cobGrams?.let { putFloat("cob", it.toFloat()) }
            s.scheduledBasal?.let { putFloat("basal", it.toFloat()) }
            s.tempBasal?.let { t ->
                putFloat("temp_rate", t.rateUnitsPerHour.toFloat())
                t.percentOfScheduled?.let { putInt("temp_pct", it) }
                // Store the end time, not minutes left, so the countdown stays right later.
                t.remainingMinutes?.let { putLong("temp_end", System.currentTimeMillis() + it * 60_000L) }
            }
            s.serverUnits?.let { putString("units", it.name) }
            error?.let { putString("error", it) }
            putLong("fetched_at", System.currentTimeMillis())
        }.apply()
    }

    /** Records a failed refresh without throwing away the last good reading. */
    fun saveError(error: String) = prefs.edit().putString("error", error).apply()

    val lastError: String? get() = prefs.getString("error", null)

    fun load(nowMillis: Long = System.currentTimeMillis()): NightscoutSnapshot? {
        if (!prefs.contains("fetched_at")) return null
        fun float(key: String) = if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null
        val temp = float("temp_rate")?.let { rate ->
            TempBasal(
                rateUnitsPerHour = rate,
                percentOfScheduled = if (prefs.contains("temp_pct")) prefs.getInt("temp_pct", 0) else null,
                remainingMinutes = if (prefs.contains("temp_end")) {
                    ((prefs.getLong("temp_end", 0) - nowMillis) / 60_000L).toInt().coerceAtLeast(0)
                } else null,
            )
        }
        return NightscoutSnapshot(
            glucoseMgdl = float("bg"),
            readingTimeMillis = if (prefs.contains("time")) prefs.getLong("time", 0) else null,
            trend = runCatching { Trend.valueOf(prefs.getString("trend", null)!!) }.getOrDefault(Trend.UNKNOWN),
            deltaMgdl = float("delta"),
            iobUnits = float("iob"),
            cobGrams = float("cob"),
            scheduledBasal = float("basal"),
            // A temp basal that has run out is no longer active.
            tempBasal = temp?.takeUnless { it.remainingMinutes == 0 },
            serverUnits = prefs.getString("units", null)?.let { runCatching { GlucoseUnit.valueOf(it) }.getOrNull() },
        )
    }
}
