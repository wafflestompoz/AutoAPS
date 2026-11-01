package io.autoaps.app.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.autoaps.app.R
import io.autoaps.app.Settings
import io.autoaps.core.DisplayFormatter
import io.autoaps.core.DisplaySettings
import io.autoaps.core.GlucoseUnit
import io.autoaps.core.NightscoutSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The one car screen: a PaneTemplate with three rows (glucose, IOB/COB, basal).
 *
 * The title and row count never change between updates, so the host treats each update
 * as a refresh of the same template rather than a new step. That keeps the screen inside
 * Android Auto's template step limit no matter how long it stays open.
 */
class StatusScreen(carContext: CarContext) : Screen(carContext) {
    private val settings = Settings(carContext)

    private var snapshot: NightscoutSnapshot? = null
    private var lastError: String? = null
    private var loaded = false
    private var refreshJob: Job? = null

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refresh()
                    delay(REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun refresh() {
        if (!settings.isReady) {
            invalidate()
            return
        }
        val result = withContext(Dispatchers.IO) {
            runCatching { settings.client().fetchSnapshot(settings.unitsOverride) }
        }
        result.onSuccess { snapshot = it; lastError = null }
            .onFailure { lastError = it.message ?: "Unknown error" }
        loaded = true
        invalidate()
    }

    private fun refreshNow() {
        if (refreshJob?.isActive == true) return
        refreshJob = lifecycleScope.launch { refresh() }
    }

    override fun onGetTemplate(): Template {
        if (!settings.isReady) return setupTemplate()

        val builder = PaneTemplate.Builder(buildPane())
            .setTitle(carContext.getString(R.string.car_title))
            .setHeaderAction(Action.APP_ICON)
        return builder.build()
    }

    private fun buildPane(): Pane {
        if (!loaded) return Pane.Builder().setLoading(true).build()

        val pane = Pane.Builder()
        val data = snapshot
        val now = System.currentTimeMillis()

        if (data == null) {
            pane.addRow(row("${carContext.getString(R.string.car_error_prefix)}: ${lastError.orEmpty()}", null))
            pane.addRow(row("IOB --  ·  COB --", null))
            pane.addRow(row("Basal --", carContext.getString(R.string.car_footer)))
        } else {
            val units = settings.unitsOverride ?: data.serverUnits ?: GlucoseUnit.MGDL
            val lines = DisplayFormatter.format(data, now, DisplaySettings(units = units))
            val ageText = listOfNotNull(lines.rangeFlag, lines.age).joinToString("  ·  ")
            val footer = lastError?.let { "${carContext.getString(R.string.car_error_prefix)}: $it" }
                ?: carContext.getString(R.string.car_footer)
            pane.addRow(row(lines.glucose, ageText))
            pane.addRow(row(lines.iobCob, null))
            pane.addRow(row(lines.basal, footer))
        }

        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_refresh))
                .setOnClickListener { refreshNow() }
                .build()
        )
        return pane.build()
    }

    private fun row(title: String, text: String?): Row =
        Row.Builder().setTitle(title).apply { text?.let { addText(it) } }.build()

    private fun setupTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(R.string.car_setup_needed))
            .setTitle(carContext.getString(R.string.car_title))
            .setHeaderAction(Action.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle("Check again")
                    .setOnClickListener { refreshNow() }
                    .build()
            )
            .build()

    private companion object {
        /** CGMs report every 1 to 5 minutes; polling once a minute is plenty. */
        const val REFRESH_INTERVAL_MS = 60_000L
    }
}
