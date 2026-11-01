package io.autoaps.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

/**
 * Parses Nightscout responses defensively: Nightscout's output varies with server version,
 * enabled plugins and the uploader (AAPS, Loop, OpenAPS, xDrip+), so every field is optional.
 */
object NightscoutParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Parses `/api/v2/properties/bgnow,delta,direction,iob,cob,basal`.
     * Returns a snapshot even when some plugins are disabled; missing values are null.
     */
    fun parseProperties(body: String, nowMillis: Long, serverUnits: GlucoseUnit? = null): NightscoutSnapshot {
        val root = json.parseToJsonElement(body).asObject() ?: JsonObject(emptyMap())

        val bgnow = root["bgnow"].asObject()
        val firstSgv = bgnow?.get("sgvs").asArray()?.firstOrNull().asObject()
        val glucose = bgnow?.get("last").asDouble() ?: firstSgv?.get("mgdl").asDouble()
        val readingTime = bgnow?.get("mills").asLong() ?: firstSgv?.get("mills").asLong()

        val directionName = root["direction"].asObject()?.get("value").asString()
            ?: firstSgv?.get("direction").asString()

        val delta = root["delta"].asObject()?.let { it["mgdl"].asDouble() ?: it["absolute"].asDouble() }

        val iob = root["iob"].asObject()?.get("iob").asDouble()
        val cob = root["cob"].asObject()?.get("cob").asDouble()

        val current = root["basal"].asObject()?.get("current").asObject()
        val scheduled = current?.get("basal").asDouble()
        val tempBasal = current?.let { parseTempBasal(it, scheduled, nowMillis) }

        return NightscoutSnapshot(
            glucoseMgdl = glucose,
            readingTimeMillis = readingTime,
            trend = Trend.fromNightscout(directionName),
            deltaMgdl = delta,
            iobUnits = iob,
            cobGrams = cob,
            scheduledBasal = scheduled,
            tempBasal = tempBasal,
            serverUnits = serverUnits,
        )
    }

    private fun parseTempBasal(current: JsonObject, scheduled: Double?, nowMillis: Long): TempBasal? {
        val treatment = current["treatment"].asObject() ?: return null
        val rate = current["tempbasal"].asDouble() ?: current["totalbasal"].asDouble() ?: return null

        val percent = treatment["percent"].asDouble()?.let { (100 + it).roundToInt() }
            ?: scheduled?.takeIf { it > 0 }?.let { (rate / it * 100).roundToInt() }

        val start = treatment["mills"].asLong()
        val duration = treatment["duration"].asDouble()
        val remaining = if (start != null && duration != null) {
            val endMillis = start + (duration * 60_000).toLong()
            ((endMillis - nowMillis) / 60_000.0).roundToInt().coerceAtLeast(0)
        } else null

        return TempBasal(rate, percent, remaining)
    }

    /**
     * Parses `/api/v1/entries/sgv.json?count=2`, used as a fallback when the v2 properties
     * endpoint is unavailable. Gives glucose, trend and delta only.
     */
    fun parseEntries(body: String, serverUnits: GlucoseUnit? = null): NightscoutSnapshot {
        val entries = json.parseToJsonElement(body).asArray().orEmpty().mapNotNull { it.asObject() }
        val latest = entries.getOrNull(0)
        val previous = entries.getOrNull(1)
        val latestSgv = latest?.get("sgv").asDouble()
        val latestDate = latest?.get("date").asLong()
        val previousSgv = previous?.get("sgv").asDouble()
        val previousDate = previous?.get("date").asLong()

        // Only report a delta between readings roughly one CGM interval apart.
        val delta = if (latestSgv != null && previousSgv != null && latestDate != null && previousDate != null &&
            latestDate - previousDate in 1..(10 * 60_000L)
        ) latestSgv - previousSgv else latest?.get("delta").asDouble()

        return NightscoutSnapshot(
            glucoseMgdl = latestSgv,
            readingTimeMillis = latestDate,
            trend = Trend.fromNightscout(latest?.get("direction").asString()),
            deltaMgdl = delta,
            iobUnits = null,
            cobGrams = null,
            scheduledBasal = null,
            tempBasal = null,
            serverUnits = serverUnits,
        )
    }

    /** Reads `settings.units` from `/api/v1/status.json`. */
    fun parseStatusUnits(body: String): GlucoseUnit? {
        val root = json.parseToJsonElement(body).asObject() ?: return null
        return GlucoseUnit.fromNightscout(root["settings"].asObject()?.get("units").asString())
    }

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
    private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray
    private fun JsonElement?.asPrimitive(): JsonPrimitive? =
        (this as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonElement?.asDouble(): Double? =
        asPrimitive()?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }?.takeIf { it.isFinite() }

    private fun JsonElement?.asLong(): Long? =
        asPrimitive()?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

    private fun JsonElement?.asString(): String? = asPrimitive()?.content
}
