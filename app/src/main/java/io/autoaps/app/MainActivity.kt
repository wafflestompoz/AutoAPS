package io.autoaps.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.autoaps.app.widget.WidgetRefreshWorker
import io.autoaps.core.DisplayFormatter
import io.autoaps.core.DisplaySettings
import io.autoaps.core.GlucoseUnit
import io.autoaps.core.NightscoutClient
import io.autoaps.core.TargetRange
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Phone-side setup: safety notice, Nightscout address, token and units. */
class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings
    private lateinit var disclaimer: CheckBox
    private lateinit var urlInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var unitsSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var rangeCustom: CheckBox
    private lateinit var rangeLow: EditText
    private lateinit var rangeHigh: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        disclaimer = findViewById(R.id.disclaimer_check)
        urlInput = findViewById(R.id.url_input)
        tokenInput = findViewById(R.id.token_input)
        unitsSpinner = findViewById(R.id.units_spinner)
        status = findViewById(R.id.status_text)
        rangeCustom = findViewById(R.id.range_custom_check)
        rangeLow = findViewById(R.id.range_low_input)
        rangeHigh = findViewById(R.id.range_high_input)

        disclaimer.isChecked = settings.disclaimerAccepted
        urlInput.setText(settings.nightscoutUrl)
        tokenInput.setText(settings.token)
        unitsSpinner.setSelection(
            when (settings.unitsOverride) {
                null -> 0
                GlucoseUnit.MGDL -> 1
                GlucoseUnit.MMOL -> 2
            }
        )

        loadRange()
        rangeCustom.setOnCheckedChangeListener { _, checked ->
            setRangeEnabled(checked)
            // Start from the site's range (or 70-180 mg/dL) so people only adjust what they need.
            if (checked && rangeLow.text.isBlank() && rangeHigh.text.isBlank()) {
                showRange(SnapshotCache(this).load()?.targetRange ?: TargetRange(70.0, 180.0), rangeUnits())
            }
        }
        unitsSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            private var lastUnits = rangeUnits()
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val newUnits = rangeUnits()
                if (newUnits != lastUnits) {
                    // Re-express whatever is typed in the new units.
                    parseRange(lastUnits)?.let { showRange(it, newUnits) }
                    lastUnits = newUnits
                }
                updateRangeHints()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.save_button).setOnClickListener { save() }
        findViewById<Button>(R.id.test_button).setOnClickListener { testConnection() }
    }

    private fun selectedUnits(): GlucoseUnit? = when (unitsSpinner.selectedItemPosition) {
        1 -> GlucoseUnit.MGDL
        2 -> GlucoseUnit.MMOL
        else -> null
    }

    /** Units the range fields are shown in: the chosen units, else the site's, else mg/dL. */
    private fun rangeUnits(): GlucoseUnit =
        selectedUnits() ?: SnapshotCache(this).load()?.serverUnits ?: GlucoseUnit.MGDL

    private fun setRangeEnabled(enabled: Boolean) {
        rangeLow.isEnabled = enabled
        rangeHigh.isEnabled = enabled
    }

    private fun updateRangeHints() {
        val label = rangeUnits().label
        rangeLow.hint = getString(R.string.settings_range_low, label)
        rangeHigh.hint = getString(R.string.settings_range_high, label)
    }

    private fun loadRange() {
        val saved = settings.rangeOverride
        rangeCustom.isChecked = saved != null
        setRangeEnabled(saved != null)
        saved?.let { showRange(it, rangeUnits()) }
        updateRangeHints()
    }

    private fun showRange(range: TargetRange, units: GlucoseUnit) {
        rangeLow.setText(format(range.lowMgdl, units))
        rangeHigh.setText(format(range.highMgdl, units))
    }

    private fun format(mgdl: Double, units: GlucoseUnit) = when (units) {
        GlucoseUnit.MGDL -> Math.round(mgdl).toString()
        GlucoseUnit.MMOL -> String.format(Locale.US, "%.1f", mgdl / DisplayFormatter.MGDL_PER_MMOL)
    }

    /** Reads the two fields as mg/dL, or null if either is empty or not a number. */
    private fun parseRange(units: GlucoseUnit): TargetRange? {
        fun read(e: EditText) = e.text.toString().trim().replace(',', '.').toDoubleOrNull()?.let {
            if (units == GlucoseUnit.MMOL) it * DisplayFormatter.MGDL_PER_MMOL else it
        }
        val low = read(rangeLow) ?: return null
        val high = read(rangeHigh) ?: return null
        return TargetRange(low, high)
    }

    /** Lets people paste "https://site/?token=abc" into the address field. */
    private fun splitPastedToken() {
        val pasted = urlInput.text.toString()
        NightscoutClient.tokenFromUrl(pasted)?.let { token ->
            if (tokenInput.text.isBlank()) tokenInput.setText(token)
            urlInput.setText(NightscoutClient.normalizeUrl(pasted)?.toString() ?: pasted)
        }
    }

    private fun save(): Boolean {
        if (!disclaimer.isChecked) {
            status.setText(R.string.settings_need_disclaimer)
            return false
        }
        splitPastedToken()
        val normalized = NightscoutClient.normalizeUrl(urlInput.text.toString())
        if (normalized == null) {
            status.text = "That doesn't look like a valid Nightscout address."
            return false
        }
        val customRange = if (rangeCustom.isChecked) {
            val range = parseRange(rangeUnits())
            if (range == null || range.lowMgdl < 40 || range.highMgdl > 400 || range.lowMgdl >= range.highMgdl) {
                status.text = "Enter a low and high value, with low below high " +
                    "(between ${format(40.0, rangeUnits())} and ${format(400.0, rangeUnits())} ${rangeUnits().label})."
                return false
            }
            range
        } else null
        settings.rangeOverride = customRange
        settings.acceptedDisclaimerVersion = Settings.DISCLAIMER_VERSION
        settings.nightscoutUrl = normalized.toString()
        settings.token = tokenInput.text.toString()
        settings.unitsOverride = selectedUnits()
        urlInput.setText(settings.nightscoutUrl)
        status.setText(R.string.settings_saved)
        WidgetRefreshWorker.schedule(this, immediately = true)
        return true
    }

    private fun testConnection() {
        if (!save()) return
        status.setText(R.string.settings_testing)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val snapshot = settings.client().fetchSnapshot(settings.unitsOverride)
                    SnapshotCache(this@MainActivity).save(snapshot)
                    val units = settings.unitsOverride ?: snapshot.serverUnits ?: GlucoseUnit.MGDL
                    DisplayFormatter.format(snapshot, System.currentTimeMillis(), DisplaySettings(units = units, rangeOverride = settings.rangeOverride))
                }
            }
            status.text = result.fold(
                onSuccess = { "Connected.\n${it.glucose}  (${it.age})\n${it.iobCob}\n${it.basal}" },
                onFailure = { "Couldn't connect: ${it.message}" },
            )
        }
    }
}
