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

    private lateinit var regionSeekBar: SeekBar
    private lateinit var regionValueText: TextView
    private lateinit var qualityModeText: TextView
    private lateinit var languageText: TextView
    private lateinit var sourceText: TextView

    private val preferences by lazy { AppPreferences(applicationContext) }

    private val regions = listOf(
        1 to "Москва",
        2 to "Московская область",
        3 to "Санкт-Петербург",
        4 to "Ленинградская область",
        5 to "Вологодская область",
        6 to "Воронежская область",
        7 to "Ивановская область",
        8 to "Калужская область",
        9 to "Костромская область",
        10 to "Курская область",
        11 to "Липецкая область",
        12 to "Московская область (запад)",
        13 to "Московская область (восток)",
        14 to "Новгородская область",
        15 to "Псковская область",
        16 to "Рязанская область",
        17 to "Смоленская область",
        18 to "Тверская область",
        19 to "Тульская область",
        20 to "Тамбовская область",
        21 to "Ярославская область",
        22 to "Белгородская область",
        23 to "Брянская область",
        24 to "Владимирская область",
        25 to "Калининградская область",
        26 to "Орловская область",
        27 to "Саратовская область",
        28 to "Сахалинская область",
        29 to "Свердловская область",
        30 to "Челябинская область",
        31 to "Ямало-Ненецкий АО",
        32 to "Амурская область",
        33 to "Архангельская область",
        34 to "Астраханская область",
        35 to "Бурятия",
        36 to "Волгоградская область",
        37 to "Вологодская область (север)",
        38 to "Дагестан",
        39 to "Еврейская АО",
        40 to "Забайкальский край",
        41 to "Иркутская область",
        42 to "Камчатский край",
        43 to "Кемеровская область",
        44 to "Кировская область",
        45 to "Краснодарский край",
        46 to "Красноярский край",
        47 to "Крым",
        48 to "Магаданская область",
        49 to "Марий Эл",
        50 to "Мордовия",
        51 to "Мурманская область",
        52 to "Ненецкий АО",
        53 to "Нижегородская область",
        54 to "Омская область",
        55 to "Оренбургская область",
        56 to "Пензенская область",
        57 to "Пермский край",
        58 to "Приморский край",
        59 to "Ростовская область",
        60 to "Рязанская область (юг)",
        61 to "Ставропольский край",
        62 to "Татарстан",
        63 to "Томская область",
        64 to "Тульская область (юг)",
        65 to "Тыва",
        66 to "Удмуртия",
        67 to "Хабаровский край",
        68 to "Ханты-Мансийский АО",
        69 to "Чечня",
        70 to "Чувашия",
        71 to "Якутия"
    )

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
    private var currentQualityIndex = 3
    private var currentLanguageIndex = 0
    private var currentSourceIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        regionSeekBar = findViewById(R.id.regionSeekBar)
        regionValueText = findViewById(R.id.regionValueText)
        qualityModeText = findViewById(R.id.qualityModeText)
        languageText = findViewById(R.id.languageText)
        sourceText = findViewById(R.id.sourceText)

        loadCurrentSettings()
        setupRegionSelector()
        setupQualitySelector()
        setupLanguageSelector()
        setupSourceSelector()
    }

    private fun loadCurrentSettings() {
        val currentRegion = preferences.regionId
        currentRegionIndex = regions.indexOfFirst { it.first == currentRegion }.takeIf { it != -1 } ?: 0
        currentQualityIndex = qualityModes.indexOf(preferences.qualityMode).takeIf { it != -1 } ?: 3
        currentLanguageIndex = languages.indexOfFirst { it.first == preferences.language }.takeIf { it != -1 } ?: 0
        currentSourceIndex = sources.indexOf(preferences.contentSource).takeIf { it != -1 } ?: 0
        updateRegionDisplay()
        updateQualityDisplay()
        updateLanguageDisplay()
        updateSourceDisplay()
    }

    private fun setupRegionSelector() {
        regionSeekBar.max = regions.size - 1
        regionSeekBar.progress = currentRegionIndex
        updateRegionDisplay()

        regionSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentRegionIndex = progress
                    updateRegionDisplay()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val selectedRegionId = regions[currentRegionIndex].first
                preferences.regionId = selectedRegionId
                Toast.makeText(
                    this@SettingsActivity,
                    getString(R.string.region_value, regions[currentRegionIndex].second),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun updateRegionDisplay() {
        val regionName = regions[currentRegionIndex].second
        regionValueText.text = regionName
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

    private fun setupSourceSelector() {
        updateSourceDisplay()
        findViewById<TextView>(R.id.sourceButton).setOnClickListener {
            currentSourceIndex = (currentSourceIndex + 1) % sources.size
            preferences.contentSource = sources[currentSourceIndex]
            updateSourceDisplay()
            Toast.makeText(this, getString(R.string.source_value, getSourceName(sources[currentSourceIndex])), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateQualityDisplay() {
        qualityModeText.text = getString(R.string.quality_value, getQualityName(qualityModes[currentQualityIndex]))
    }

    private fun updateLanguageDisplay() {
        languageText.text = getString(R.string.language_value, languages[currentLanguageIndex].second)
    }

    private fun updateSourceDisplay() {
        sourceText.text = getString(R.string.source_value, getSourceName(sources[currentSourceIndex]))
    }

    private fun getQualityName(mode: AppPreferences.QualityMode): String = when (mode) {
        AppPreferences.QualityMode.MINIMUM -> getString(R.string.quality_minimum)
        AppPreferences.QualityMode.MEDIUM -> getString(R.string.quality_medium)
        AppPreferences.QualityMode.MAXIMUM -> getString(R.string.quality_maximum)
        AppPreferences.QualityMode.AUTO -> getString(R.string.quality_auto)
    }

    private fun getSourceName(source: AppPreferences.ContentSource): String = when (source) {
        AppPreferences.ContentSource.PREMIER -> getString(R.string.source_premier)
        AppPreferences.ContentSource.IVI -> getString(R.string.source_ivi)
        AppPreferences.ContentSource.SMOTRESHKA -> getString(R.string.source_smotreshka)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> {
            finish()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
