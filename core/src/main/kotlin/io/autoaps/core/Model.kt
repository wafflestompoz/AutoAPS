package io.autoaps.core

/** Glucose units. Nightscout stores every reading in mg/dL; mmol/L is a display conversion. */
enum class GlucoseUnit(val label: String) {
    MGDL("mg/dL"),
    MMOL("mmol/L");

    companion object {
        /** Parses the `units` value from Nightscout's status.json ("mg/dl", "mmol", "mmol/L"). */
        fun fromNightscout(value: String?): GlucoseUnit? = when {
            value == null -> null
            value.lowercase().startsWith("mmol") -> MMOL
            value.lowercase().startsWith("mg") -> MGDL
            else -> null
        }
    }
}

/** CGM trend, using the direction names Nightscout uploads. */
enum class Trend(val arrow: String, val description: String) {
    DOUBLE_UP("⇈", "rising quickly"),
    SINGLE_UP("↑", "rising"),
    FORTY_FIVE_UP("↗", "rising slowly"),
    FLAT("→", "steady"),
    FORTY_FIVE_DOWN("↘", "falling slowly"),
    SINGLE_DOWN("↓", "falling"),
    DOUBLE_DOWN("⇊", "falling quickly"),
    UNKNOWN("?", "trend unknown");

    companion object {
        fun fromNightscout(value: String?): Trend = when (value) {
            "DoubleUp", "TripleUp" -> DOUBLE_UP
            "SingleUp" -> SINGLE_UP
            "FortyFiveUp" -> FORTY_FIVE_UP
            "Flat" -> FLAT
            "FortyFiveDown" -> FORTY_FIVE_DOWN
            "SingleDown" -> SINGLE_DOWN
            "DoubleDown", "TripleDown" -> DOUBLE_DOWN
            else -> UNKNOWN
        }
    }
}

/** An active temporary basal rate. */
data class TempBasal(
    /** Rate actually being delivered, in U/h. */
    val rateUnitsPerHour: Double,
    /** Percent of the scheduled rate, when known. */
    val percentOfScheduled: Int?,
    /** Minutes until the temp basal ends, when known. */
    val remainingMinutes: Int?,
)

/** Everything the car screen shows, as read from Nightscout at one moment. */
data class NightscoutSnapshot(
    val glucoseMgdl: Double?,
    /** Epoch millis of the CGM reading. */
    val readingTimeMillis: Long?,
    val trend: Trend,
    /** Change since the previous reading, in mg/dL. */
    val deltaMgdl: Double?,
    val iobUnits: Double?,
    val cobGrams: Double?,
    /** Scheduled profile basal right now, in U/h. */
    val scheduledBasal: Double?,
    /** Null when no temp basal is running. */
    val tempBasal: TempBasal?,
    /** Units the Nightscout site is configured to display, when known. */
    val serverUnits: GlucoseUnit?,
)
