package io.autoaps.app

import android.content.Context
import io.autoaps.core.GlucoseUnit
import io.autoaps.core.NightscoutClient
import io.autoaps.core.TargetRange

/** User settings, stored on this phone only. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("autoaps", Context.MODE_PRIVATE)

    var nightscoutUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_URL, value.trim()).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    /** Null means "use the units configured on the Nightscout site". */
    var unitsOverride: GlucoseUnit?
        get() = prefs.getString(KEY_UNITS, null)?.let { runCatching { GlucoseUnit.valueOf(it) }.getOrNull() }
        set(value) = prefs.edit().putString(KEY_UNITS, value?.name).apply()

    /**
     * A LOW/HIGH range the user set in the app, in mg/dL. Null means "use the target range
     * from my Nightscout site".
     */
    var rangeOverride: TargetRange?
        get() {
            if (!prefs.contains(KEY_RANGE_LOW) || !prefs.contains(KEY_RANGE_HIGH)) return null
            return TargetRange(
                prefs.getFloat(KEY_RANGE_LOW, 0f).toDouble(),
                prefs.getFloat(KEY_RANGE_HIGH, 0f).toDouble(),
            )
        }
        set(value) = prefs.edit().apply {
            if (value == null) {
                remove(KEY_RANGE_LOW); remove(KEY_RANGE_HIGH)
            } else {
                putFloat(KEY_RANGE_LOW, value.lowMgdl.toFloat())
                putFloat(KEY_RANGE_HIGH, value.highMgdl.toFloat())
            }
        }.apply()

    /** The disclaimer version the user accepted. Bump DISCLAIMER_VERSION to ask again. */
    var acceptedDisclaimerVersion: Int
        get() = prefs.getInt(KEY_DISCLAIMER, 0)
        set(value) = prefs.edit().putInt(KEY_DISCLAIMER, value).apply()

    val disclaimerAccepted get() = acceptedDisclaimerVersion >= DISCLAIMER_VERSION

    val isConfigured get() = NightscoutClient.normalizeUrl(nightscoutUrl) != null

    val isReady get() = isConfigured && disclaimerAccepted

    fun client() = NightscoutClient(nightscoutUrl, token.ifBlank { null })

    companion object {
        const val DISCLAIMER_VERSION = 1
        private const val KEY_URL = "nightscout_url"
        private const val KEY_TOKEN = "nightscout_token"
        private const val KEY_UNITS = "units_override"
        private const val KEY_DISCLAIMER = "disclaimer_version"
        private const val KEY_RANGE_LOW = "range_low_mgdl"
        private const val KEY_RANGE_HIGH = "range_high_mgdl"
    }
}
