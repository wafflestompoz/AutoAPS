package io.autoaps.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.autoaps.app.MainActivity
import io.autoaps.app.Settings
import io.autoaps.app.SnapshotCache
import io.autoaps.core.DisplayFormatter
import io.autoaps.core.DisplayLines
import io.autoaps.core.DisplaySettings
import io.autoaps.core.GlucoseUnit

/**
 * Home screen widget, built with Jetpack Glance as Google's "Adapt widgets for cars"
 * guide asks: responsive to the cell size, no scrolling, follows light/dark and dynamic
 * color, fills its bounds. The same widget is what Android Auto shows when the car's
 * widget support is available on the user's phone.
 */
class AutoApsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = Settings(context)
        val cache = SnapshotCache(context)
        val now = System.currentTimeMillis()
        val snapshot = cache.load(now)
        val lines = snapshot?.let {
            val units = settings.unitsOverride ?: it.serverUnits ?: GlucoseUnit.MGDL
            DisplayFormatter.format(it, now, DisplaySettings(units = units, rangeOverride = settings.rangeOverride))
        }
        val message = when {
            !settings.isReady -> "Tap to set up AutoAPS"
            lines == null -> cache.lastError ?: "Waiting for Nightscout…"
            else -> null
        }
        provideContent {
            GlanceTheme { WidgetContent(lines, message, cache.lastError) }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 60.dp)    // glucose and age only
        val MEDIUM = DpSize(180.dp, 110.dp)  // + IOB/COB and basal
        val LARGE = DpSize(250.dp, 160.dp)   // + footer notice
    }
}

@Composable
private fun WidgetContent(lines: DisplayLines?, message: String?, error: String?) {
    val size = LocalSize.current
    val colors = GlanceTheme.colors
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lines == null) {
            Text(message.orEmpty(), style = TextStyle(color = colors.onSurface, fontSize = 15.sp))
            return@Column
        }

        Text(
            lines.glucose,
            maxLines = 1,
            style = TextStyle(color = colors.onSurface, fontSize = if (size.width >= AutoApsWidget.MEDIUM.width) 28.sp else 22.sp, fontWeight = FontWeight.Bold),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val flag = if (lines.isStale) "STALE" else lines.rangeFlag
            if (flag != null) {
                Text(
                    flag,
                    modifier = GlanceModifier.background(colors.errorContainer).cornerRadius(6.dp).padding(horizontal = 6.dp),
                    style = TextStyle(color = colors.onErrorContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(
                lines.age.removePrefix("STALE: "),
                maxLines = 1,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 13.sp),
            )
        }

        if (size.height >= AutoApsWidget.MEDIUM.height) {
            Spacer(GlanceModifier.height(6.dp))
            Text(lines.iobCob, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 15.sp))
            Text(lines.basal, maxLines = 1, style = TextStyle(color = colors.onSurface, fontSize = 15.sp))
        }

        if (size.height >= AutoApsWidget.LARGE.height) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                error?.let { "Can't update: $it" } ?: "Not for treatment decisions.",
                maxLines = 1,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}
