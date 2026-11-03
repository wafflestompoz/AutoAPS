package io.autoaps.app.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import io.autoaps.app.Settings
import io.autoaps.app.SnapshotCache
import io.autoaps.core.DisplayFormatter
import io.autoaps.core.DisplayLines
import io.autoaps.core.DisplaySettings
import io.autoaps.core.GlucoseUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows AutoAPS as a media source in Android Auto.
 *
 * Recent Android Auto versions only let sideloaded apps through "Unknown sources" if they are
 * media, messaging or notification apps; template (Car App Library) apps are refused. So this
 * service presents the same reading as "now playing" metadata plus a short browse list.
 * It plays no audio. Choosing it as the media source does pause other audio apps, which the
 * README explains.
 */
class GlucoseMediaService : MediaBrowserServiceCompat() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSessionCompat
    private lateinit var settings: Settings
    private lateinit var cache: SnapshotCache
    private var refreshJob: Job? = null
    private var lines: DisplayLines? = null
    private var lastError: String? = null

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        cache = SnapshotCache(this)
        session = MediaSessionCompat(this, "AutoAPS").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                // Play, and tapping any browse item, mean "refresh now".
                override fun onPlay() = refreshNow()
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = refreshNow()
                override fun onPause() = Unit
                override fun onStop() = Unit
            })
            isActive = true
        }
        sessionToken = session.sessionToken
        render()
        startRefreshing()
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot(ROOT_ID, null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (parentId != ROOT_ID) {
            result.sendResult(mutableListOf()); return
        }
        val l = lines
        val rows: List<Pair<String, String?>> = when {
            !settings.isReady -> listOf("Set up AutoAPS on your phone" to "Open the app while parked")
            l == null -> listOf((lastError ?: "Waiting for Nightscout…") to null)
            else -> listOf(
                l.glucose to listOfNotNull(if (l.isStale) "STALE" else l.rangeFlag, l.age.removePrefix("STALE: ")).joinToString("  ·  "),
                l.iobCob to null,
                l.basal to null,
                "Not for treatment decisions" to (lastError?.let { "Can't update: $it" } ?: "Confirm with your CGM"),
            )
        }
        result.sendResult(rows.mapIndexed { i, (title, subtitle) ->
            MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder().setMediaId("row$i").setTitle(title).setSubtitle(subtitle).build(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE,
            )
        }.toMutableList())
    }

    private fun startRefreshing() {
        refreshJob = scope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun refreshNow() {
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        if (settings.isReady) {
            val result = withContext(Dispatchers.IO) {
                runCatching { settings.client().fetchSnapshot(settings.unitsOverride) }
            }
            result.onSuccess { cache.save(it); lastError = null }
                .onFailure { lastError = it.message ?: "Unknown error"; cache.saveError(lastError!!) }
        }
        render()
    }

    /** Formats the cached reading and publishes it as now-playing metadata and browse rows. */
    private fun render() {
        val snapshot = cache.load()
        lines = snapshot?.let {
            val units = settings.unitsOverride ?: it.serverUnits ?: GlucoseUnit.MGDL
            DisplayFormatter.format(it, System.currentTimeMillis(), DisplaySettings(units = units, rangeOverride = settings.rangeOverride))
        }
        val l = lines
        val title = when {
            !settings.isReady -> "Set up AutoAPS on your phone"
            l == null -> lastError ?: "Waiting for Nightscout…"
            else -> l.glucose
        }
        val subtitle = l?.let { listOfNotNull(if (it.isStale) "STALE" else it.rangeFlag, it.age.removePrefix("STALE: "), it.iobCob).joinToString("  ·  ") }
        val album = l?.let { lastError?.let { e -> "Can't update: $e" } ?: it.basal }

        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle ?: "")
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, album ?: "")
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork(l))
                .build()
        )
        // "Playing" keeps the now-playing card on screen. Only play (refresh) is offered.
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                .build()
        )
        notifyChildrenChanged(ROOT_ID)
    }

    /** Large glucose number as album art, so it's readable at a glance on the car screen. */
    private fun artwork(l: DisplayLines?): Bitmap {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val flag = l?.let { if (it.isStale) "STALE" else it.rangeFlag }
        canvas.drawColor(
            when (flag) {
                "LOW" -> Color.rgb(0x8A, 0x1C, 0x14)
                "HIGH" -> Color.rgb(0x7A, 0x5A, 0x00)
                "STALE" -> Color.rgb(0x3D, 0x34, 0x66)
                else -> Color.rgb(0x1E, 0x3A, 0x5F)
            }
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // "142 mg/dL  ↗  +4" -> big "142", smaller "↗ +4".
        val parts = l?.glucose?.split("  ").orEmpty()
        val value = parts.firstOrNull()?.substringBefore(' ') ?: "--"
        paint.textSize = if (value.length > 3) 170f else 210f
        canvas.drawText(value, size / 2f, size * 0.55f, paint)
        paint.textSize = 72f
        val trend = parts.drop(1).joinToString(" ").ifBlank { flag ?: "" }
        canvas.drawText(trend, size / 2f, size * 0.80f, paint)
        return bmp
    }

    private companion object {
        const val ROOT_ID = "autoaps_root"
        const val REFRESH_INTERVAL_MS = 60_000L
    }
}
