package com.example.tvapp.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.RegionList

class SettingsActivity : BaseActivity() {

    private lateinit var regionSeekBar: SeekBar
    private lateinit var regionValueText: TextView
    private lateinit var qualityOptions: LinearLayout
    private lateinit var languageOptions: LinearLayout
    private lateinit var sourceOptions: LinearLayout

    private val preferences by lazy { AppPreferences(applicationContext) }

    private val regions = RegionList.regions

    private val qualityModes = listOf(
        AppPreferences.QualityMode.MINIMUM,
        AppPreferences.QualityMode.MEDIUM,
        AppPreferences.QualityMode.MAXIMUM,
        AppPreferences.QualityMode.AUTO
    )

    private val languages = listOf("ru" to "Русский", "en" to "English")

    private val sources = listOf(
        AppPreferences.ContentSource.PREMIER,
        AppPreferences.ContentSource.IVI,
        AppPreferences.ContentSource.SMOTRESHKA
    )

    private var currentRegionIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        regionSeekBar = findViewById(R.id.regionSeekBar)
        regionValueText = findViewById(R.id.regionValueText)
        qualityOptions = findViewById(R.id.qualityOptions)
        languageOptions = findViewById(R.id.languageOptions)
        sourceOptions = findViewById(R.id.sourceOptions)

        setupRegionSelector()
        setupQualitySelector()
        setupLanguageSelector()
        setupSourceSelector()
    }

    private fun setupRegionSelector() {
        currentRegionIndex = regions.indexOfFirst { it.first == preferences.regionId }.takeIf { it != -1 } ?: 0
        regionSeekBar.max = regions.size - 1
        regionSeekBar.progress = currentRegionIndex
        updateRegionDisplay()

        regionSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentRegionIndex = progress
                    preferences.regionId = regions[progress].first
                    updateRegionDisplay()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.region_value, regions[currentRegionIndex].second),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun updateRegionDisplay() {
        regionValueText.text = regions[currentRegionIndex].second
    }

    private fun setupQualitySelector() {
        val labels = qualityModes.map { mode ->
            when (mode) {
                AppPreferences.QualityMode.MINIMUM -> getString(R.string.quality_minimum)
                AppPreferences.QualityMode.MEDIUM -> getString(R.string.quality_medium)
                AppPreferences.QualityMode.MAXIMUM -> getString(R.string.quality_maximum)
                AppPreferences.QualityMode.AUTO -> getString(R.string.quality_auto)
            }
        }
        val selected = qualityModes.indexOf(preferences.qualityMode).takeIf { it != -1 } ?: qualityModes.size - 1
        buildOptionRow(qualityOptions, labels, selected) { index ->
            preferences.qualityMode = qualityModes[index]
        }
    }

    private fun setupLanguageSelector() {
        val selected = languages.indexOfFirst { it.first == preferences.language }.takeIf { it != -1 } ?: 0
        buildOptionRow(languageOptions, languages.map { it.second }, selected) { index ->
            preferences.language = languages[index].first
            recreate()
        }
    }

    private fun setupSourceSelector() {
        val labels = sources.map { source ->
            when (source) {
                AppPreferences.ContentSource.PREMIER -> getString(R.string.source_premier)
                AppPreferences.ContentSource.IVI -> getString(R.string.source_ivi)
                AppPreferences.ContentSource.SMOTRESHKA -> getString(R.string.source_smotreshka)
            }
        }
        val selected = sources.indexOf(preferences.contentSource).takeIf { it != -1 } ?: 0
        buildOptionRow(sourceOptions, labels, selected) { index ->
            preferences.contentSource = sources[index]
        }
    }

    private fun buildOptionRow(
        container: LinearLayout,
        labels: List<String>,
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ) {
        container.removeAllViews()
        labels.forEachIndexed { index, label ->
            val button = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(ContextCompat.getColorStateList(this@SettingsActivity, R.color.option_button_text))
                setBackgroundResource(R.drawable.option_button_bg)
                setPadding(dp(24), dp(12), dp(24), dp(12))
                isClickable = true
                isFocusable = true
                isSelected = index == selectedIndex
                setOnClickListener {
                    onSelect(index)
                    for (i in 0 until container.childCount) {
                        (container.getChildAt(i) as TextView).isSelected = i == index
                    }
                }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.marginStart = dp(16)
            }
            container.addView(button, params)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> {
            finish()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
