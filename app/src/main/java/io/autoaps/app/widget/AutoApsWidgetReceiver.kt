package io.autoaps.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class AutoApsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AutoApsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context, immediately = true)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancel(context)
    }
}
