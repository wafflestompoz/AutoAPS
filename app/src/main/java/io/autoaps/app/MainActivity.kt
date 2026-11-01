package io.autoaps.app

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.autoaps.core.DisplayFormatter
import io.autoaps.core.DisplaySettings
import io.autoaps.core.GlucoseUnit
import io.autoaps.core.NightscoutClient
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = Settings(this)

        disclaimer = findViewById(R.id.disclaimer_check)
        urlInput = findViewById(R.id.url_input)
        tokenInput = findViewById(R.id.token_input)
        unitsSpinner = findViewById(R.id.units_spinner)
        status = findViewById(R.id.status_text)

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

        findViewById<Button>(R.id.save_button).setOnClickListener { save() }
        findViewById<Button>(R.id.test_button).setOnClickListener { testConnection() }
    }

    private fun selectedUnits(): GlucoseUnit? = when (unitsSpinner.selectedItemPosition) {
        1 -> GlucoseUnit.MGDL
        2 -> GlucoseUnit.MMOL
        else -> null
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
        settings.acceptedDisclaimerVersion = Settings.DISCLAIMER_VERSION
        settings.nightscoutUrl = normalized.toString()
        settings.token = tokenInput.text.toString()
        settings.unitsOverride = selectedUnits()
        urlInput.setText(settings.nightscoutUrl)
        status.setText(R.string.settings_saved)
        return true
    }

    private fun testConnection() {
        if (!save()) return
        status.setText(R.string.settings_testing)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val snapshot = settings.client().fetchSnapshot(settings.unitsOverride)
                    val units = settings.unitsOverride ?: snapshot.serverUnits ?: GlucoseUnit.MGDL
                    DisplayFormatter.format(snapshot, System.currentTimeMillis(), DisplaySettings(units = units))
                }
            }
            status.text = result.fold(
                onSuccess = { "Connected.\n${it.glucose}  (${it.age})\n${it.iobCob}\n${it.basal}" },
                onFailure = { "Couldn't connect: ${it.message}" },
            )
        }
    }
}
