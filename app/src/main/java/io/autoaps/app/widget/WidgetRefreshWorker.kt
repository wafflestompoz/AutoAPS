package io.autoaps.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.autoaps.app.Settings
import io.autoaps.app.SnapshotCache
import java.util.concurrent.TimeUnit

/**
 * Refreshes the widget about every 5 minutes, matching CGM upload intervals.
 *
 * WorkManager's periodic work can't run more often than every 15 minutes, so each run
 * schedules the next one. Android may stretch the gap under Doze or battery saver; the
 * widget shows the reading's age, and marks it STALE, so a late refresh is visible.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = Settings(ctx)
        if (settings.isReady) {
            val cache = SnapshotCache(ctx)
            runCatching { settings.client().fetchSnapshot(settings.unitsOverride) }
                .onSuccess { cache.save(it) }
                .onFailure { cache.saveError(it.message ?: "Unknown error") }
        }
        AutoApsWidget().updateAll(ctx)

        // Keep going only while at least one widget is on a home screen.
        val placed = GlanceAppWidgetManager(ctx).getGlanceIds(AutoApsWidget::class.java).isNotEmpty()
        if (placed) schedule(ctx, immediately = false)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "autoaps_widget_refresh"
        private const val INTERVAL_MINUTES = 5L

        fun schedule(context: Context, immediately: Boolean) {
            val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .apply { if (!immediately) setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES) }
                .build()
            // An immediate refresh replaces any pending delayed one. A worker queuing its own
            // successor appends, so it doesn't cancel itself while still running.
            val policy = if (immediately) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
