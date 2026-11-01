package io.autoaps.core

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Plain-text lines for the car screen. Kept free of Android types so it can be unit-tested. */
data class DisplayLines(
    /** e.g. "142 mg/dL →  +4" */
    val glucose: String,
    /** e.g. "3 min ago" or "STALE: last reading 24 min ago" */
    val age: String,
    val isStale: Boolean,
    /** "LOW", "HIGH" or null when in range or unknown. */
    val rangeFlag: String?,
    /** e.g. "IOB 1.25 U  ·  COB 18 g" */
    val iobCob: String,
    /** e.g. "Temp basal 0.50 U/h (63%) · 22 min left" or "Basal 0.80 U/h (scheduled)" */
    val basal: String,
)

data class DisplaySettings(
    val units: GlucoseUnit = GlucoseUnit.MGDL,
    val staleAfterMinutes: Int = 15,
    val lowMgdl: Int = 70,
    val highMgdl: Int = 180,
)

object DisplayFormatter {
    const val MGDL_PER_MMOL = 18.0182

    fun format(snapshot: NightscoutSnapshot, nowMillis: Long, settings: DisplaySettings): DisplayLines {
        val units = settings.units
        val glucose = snapshot.glucoseMgdl

        val ageMinutes = snapshot.readingTimeMillis?.let { ((nowMillis - it) / 60_000.0).toInt().coerceAtLeast(0) }
        val isStale = ageMinutes == null || ageMinutes >= settings.staleAfterMinutes

        val glucoseLine = if (glucose == null) {
            "No glucose reading"
        } else buildString {
            append(formatGlucose(glucose, units)).append(' ').append(units.label)
            // A trend arrow and delta on old data would be misleading; hide them when stale.
            if (!isStale) {
                append("  ").append(snapshot.trend.arrow)
                snapshot.deltaMgdl?.let { append("  ").append(formatDelta(it, units)) }
            }
        }

        val ageLine = when {
            ageMinutes == null -> "Reading time unknown"
            isStale -> "STALE: last reading ${formatMinutes(ageMinutes)} ago"
            ageMinutes == 0 -> "Just now"
            else -> "${formatMinutes(ageMinutes)} ago"
        }

        val rangeFlag = when {
            glucose == null || isStale -> null
            glucose < settings.lowMgdl -> "LOW"
            glucose > settings.highMgdl -> "HIGH"
            else -> null
        }

        val iob = snapshot.iobUnits?.let { "IOB ${String.format(Locale.US, "%.2f", it)} U" } ?: "IOB --"
        val cob = snapshot.cobGrams?.let { "COB ${it.roundToInt()} g" } ?: "COB --"

        return DisplayLines(
            glucose = glucoseLine,
            age = ageLine,
            isStale = isStale,
            rangeFlag = rangeFlag,
            iobCob = "$iob  ·  $cob",
            basal = formatBasal(snapshot),
        )
    }

    fun formatGlucose(mgdl: Double, units: GlucoseUnit): String = when (units) {
        GlucoseUnit.MGDL -> mgdl.roundToInt().toString()
        GlucoseUnit.MMOL -> String.format(Locale.US, "%.1f", mgdl / MGDL_PER_MMOL)
    }

    fun formatDelta(deltaMgdl: Double, units: GlucoseUnit): String {
        val text = when (units) {
            GlucoseUnit.MGDL -> deltaMgdl.roundToInt().let { if (it == 0) "0" else abs(it).toString() }
            GlucoseUnit.MMOL -> String.format(Locale.US, "%.1f", abs(deltaMgdl / MGDL_PER_MMOL))
        }
        val sign = when {
            text.trimStart('0', '.').isEmpty() -> "±"
            deltaMgdl > 0 -> "+"
            else -> "-"
        }
        return sign + text
    }

    fun formatBasal(snapshot: NightscoutSnapshot): String {
        val temp = snapshot.tempBasal
        if (temp != null) {
            return buildString {
                append("Temp basal ").append(formatRate(temp.rateUnitsPerHour)).append(" U/h")
                temp.percentOfScheduled?.let { append(" (").append(it).append("%)") }
                temp.remainingMinutes?.let { append(" · ").append(it).append(" min left") }
            }
        }
        return snapshot.scheduledBasal?.let { "Basal ${formatRate(it)} U/h (scheduled)" } ?: "Basal --"
    }

    private fun formatRate(rate: Double) = String.format(Locale.US, "%.2f", rate)

    private fun formatMinutes(minutes: Int): String =
        if (minutes < 120) "$minutes min" else "${minutes / 60} h"
}
