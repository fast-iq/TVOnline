package com.example.tvapp.settings

import android.os.Bundle
import android.view.KeyEvent
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences

class SettingsActivity : BaseActivity() {

    private lateinit var timezoneSeekBar: SeekBar
    private lateinit var timezoneValueText: TextView
    private lateinit var qualityModeText: TextView
    private lateinit var languageText: TextView

    private val preferences by lazy { AppPreferences(applicationContext) }

    private val timezones = listOf(
        -12 to "-12 (UTC-12)",
        -11 to "-11 (UTC-11)",
        -10 to "-10 (UTC-10)",
        -9 to "-9 (UTC-9)",
        -8 to "-8 (UTC-8)",
        -7 to "-7 (UTC-7)",
        -6 to "-6 (UTC-6)",
        -5 to "-5 (UTC-5)",
        -4 to "-4 (UTC-4)",
        -3 to "-3 (UTC-3)",
        -2 to "-2 (UTC-2)",
        -1 to "-1 (UTC-1)",
        0 to "0 (UTC)",
        1 to "+1 (UTC+1)",
        2 to "+2 (UTC+2)",
        3 to "+3 (UTC+3 - Москва)",
        4 to "+4 (UTC+4)",
        5 to "+5 (UTC+5)",
        6 to "+6 (UTC+6)",
        7 to "+7 (UTC+7)",
        8 to "+8 (UTC+8)",
        9 to "+9 (UTC+9)",
        10 to "+10 (UTC+10)",
        11 to "+11 (UTC+11)",
        12 to "+12 (UTC+12)"
    )

    private val qualityModes = listOf(
        AppPreferences.QualityMode.MINIMUM,
        AppPreferences.QualityMode.MEDIUM,
        AppPreferences.QualityMode.MAXIMUM,
        AppPreferences.QualityMode.AUTO
    )

    private val languages = listOf("ru" to "Русский", "en" to "English")

    private var currentTimezoneIndex = 15
    private var currentQualityIndex = 3
    private var currentLanguageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        timezoneSeekBar = findViewById(R.id.timezoneSeekBar)
        timezoneValueText = findViewById(R.id.timezoneValueText)
        qualityModeText = findViewById(R.id.qualityModeText)
        languageText = findViewById(R.id.languageText)

        loadCurrentSettings()
        setupTimezoneSelector()
        setupQualitySelector()
        setupLanguageSelector()
    }

    private fun loadCurrentSettings() {
        val currentOffset = preferences.timezoneOffset
        currentTimezoneIndex = timezones.indexOfFirst { it.first == currentOffset }.takeIf { it != -1 } ?: 15
        currentQualityIndex = qualityModes.indexOf(preferences.qualityMode).takeIf { it != -1 } ?: 3
        currentLanguageIndex = languages.indexOfFirst { it.first == preferences.language }.takeIf { it != -1 } ?: 0
        updateTimezoneDisplay()
        updateQualityDisplay()
        updateLanguageDisplay()
    }

    private fun setupTimezoneSelector() {
        timezoneSeekBar.max = timezones.size - 1
        timezoneSeekBar.progress = currentTimezoneIndex
        updateTimezoneDisplay()

        timezoneSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentTimezoneIndex = progress
                    updateTimezoneDisplay()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val selectedOffset = timezones[currentTimezoneIndex].first
                preferences.timezoneOffset = selectedOffset
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.timezone_value, timezones[currentTimezoneIndex].second),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun setupQualitySelector() {
        updateQualityDisplay()
        findViewById<TextView>(R.id.qualityModeButton).setOnClickListener {
            currentQualityIndex = (currentQualityIndex + 1) % qualityModes.size
            preferences.qualityMode = qualityModes[currentQualityIndex]
            updateQualityDisplay()
            Toast.makeText(this, getString(R.string.quality_value, getQualityName(qualityModes[currentQualityIndex])), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupLanguageSelector() {
        updateLanguageDisplay()
        findViewById<TextView>(R.id.languageButton).setOnClickListener {
            currentLanguageIndex = (currentLanguageIndex + 1) % languages.size
            preferences.language = languages[currentLanguageIndex].first
            updateLanguageDisplay()
            Toast.makeText(this, getString(R.string.language_value, languages[currentLanguageIndex].second), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTimezoneDisplay() {
        timezoneValueText.text = getString(R.string.timezone_value, timezones[currentTimezoneIndex].second)
    }

    private fun updateQualityDisplay() {
        qualityModeText.text = getString(R.string.quality_value, getQualityName(qualityModes[currentQualityIndex]))
    }

    private fun updateLanguageDisplay() {
        languageText.text = getString(R.string.language_value, languages[currentLanguageIndex].second)
    }

    private fun getQualityName(mode: AppPreferences.QualityMode): String {
        return when (mode) {
            AppPreferences.QualityMode.MINIMUM -> getString(R.string.quality_minimum)
            AppPreferences.QualityMode.MEDIUM -> getString(R.string.quality_medium)
            AppPreferences.QualityMode.MAXIMUM -> getString(R.string.quality_maximum)
            AppPreferences.QualityMode.AUTO -> getString(R.string.quality_auto)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { finish(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
