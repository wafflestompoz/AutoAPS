package io.autoaps.app

import android.content.Context
import io.autoaps.core.GlucoseUnit
import io.autoaps.core.NightscoutClient

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
    }
}
