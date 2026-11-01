package io.autoaps.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown for any failure the user can act on: bad URL, bad token, unreachable site. */
class NightscoutException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Read-only Nightscout client. It only ever issues GET requests: AutoAPS never writes
 * treatments, never changes settings, and never talks to a pump or loop.
 *
 * Calls are blocking; run them off the main thread.
 */
class NightscoutClient(
    baseUrl: String,
    private val token: String?,
    private val http: OkHttpClient = defaultHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val base: HttpUrl = normalizeUrl(baseUrl)
        ?: throw NightscoutException("That doesn't look like a valid Nightscout address.")

    fun fetchSnapshot(unitsOverride: GlucoseUnit? = null): NightscoutSnapshot {
        val units = unitsOverride ?: runCatching { fetchServerUnits() }.getOrNull()

        val properties = runCatching {
            NightscoutParser.parseProperties(
                get("api/v2/properties/bgnow,delta,direction,iob,cob,basal"), clock(), units,
            )
        }
        properties.getOrNull()?.takeIf { it.glucoseMgdl != null }?.let { return it }

        // Older or restricted sites: fall back to the plain entries endpoint.
        val fallback = runCatching { NightscoutParser.parseEntries(get("api/v1/entries/sgv.json?count=2"), units) }
        fallback.getOrNull()?.let { entries ->
            val props = properties.getOrNull()
            return if (props == null) entries else props.copy(
                glucoseMgdl = entries.glucoseMgdl,
                readingTimeMillis = entries.readingTimeMillis,
                trend = entries.trend,
                deltaMgdl = entries.deltaMgdl,
            )
        }

        val error = properties.exceptionOrNull() ?: fallback.exceptionOrNull()
        throw error as? NightscoutException ?: NightscoutException("Couldn't read data from Nightscout.", error)
    }

    fun fetchServerUnits(): GlucoseUnit? = NightscoutParser.parseStatusUnits(get("api/v1/status.json"))

    private fun get(pathAndQuery: String): String {
        val (path, query) = pathAndQuery.split('?', limit = 2).let { it[0] to it.getOrNull(1) }
        val url = base.newBuilder().apply {
            addPathSegments(path)
            query?.split('&')?.forEach { pair ->
                val (k, v) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                addQueryParameter(k, v)
            }
            token?.takeIf { it.isNotBlank() }?.let { addQueryParameter("token", it.trim()) }
        }.build()

        val request = Request.Builder().url(url).header("Accept", "application/json").get().build()
        try {
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        throw NightscoutException("Nightscout refused the token. Check it has the readable role.")
                    response.code == 404 -> throw NightscoutException("Nightscout page not found (404).")
                    !response.isSuccessful -> throw NightscoutException("Nightscout returned HTTP ${response.code}.")
                }
                return response.body?.string() ?: throw NightscoutException("Nightscout sent an empty response.")
            }
        } catch (e: IOException) {
            throw NightscoutException("Can't reach Nightscout. Check the address and your connection.", e)
        }
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        /**
         * Accepts what users typically paste: "mysite.herokuapp.com", "https://x.fly.dev/",
         * or a full URL with "?token=..." (the token in the URL is dropped; it's set separately).
         */
        fun normalizeUrl(input: String): HttpUrl? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            val parsed = withScheme.toHttpUrlOrNull() ?: return null
            return parsed.newBuilder().query(null).fragment(null).build()
        }

        /** Pulls a token out of a pasted URL like https://site/?token=abc, if present. */
        fun tokenFromUrl(input: String): String? {
            val trimmed = input.trim()
            val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
            return withScheme.toHttpUrlOrNull()?.queryParameter("token")?.takeIf { it.isNotBlank() }
        }
    }
}
