package com.example.tvapp.settings

import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.ChannelRepository
import com.example.tvapp.data.LimeHdClient
import com.example.tvapp.data.LimeHdRegion
import com.example.tvapp.data.RegionList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : BaseActivity() {

    private lateinit var regionButton: TextView
    private lateinit var qualityOptions: LinearLayout
    private lateinit var languageOptions: LinearLayout
    private lateinit var sourceOptions: LinearLayout

    private val preferences by lazy { AppPreferences(applicationContext) }
    private val limeHdClient by lazy { LimeHdClient(applicationContext) }
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    private var regions: List<Pair<Int, String>> = emptyList()
    private var limeHdRegions: List<LimeHdRegion> = emptyList()

    private val qualityModes = listOf(
        AppPreferences.QualityMode.MINIMUM,
        AppPreferences.QualityMode.MEDIUM,
        AppPreferences.QualityMode.MAXIMUM,
        AppPreferences.QualityMode.AUTO
    )

    private val languages = listOf("ru" to "Русский", "en" to "English")

    private val sources = listOf(
        AppPreferences.ContentSource.LIMEHD,
        AppPreferences.ContentSource.PREMIER,
        AppPreferences.ContentSource.IVI,
        AppPreferences.ContentSource.SMOTRESHKA
    )

    private var currentRegionIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        regionButton = findViewById(R.id.regionButton)
        qualityOptions = findViewById(R.id.qualityOptions)
        languageOptions = findViewById(R.id.languageOptions)
        sourceOptions = findViewById(R.id.sourceOptions)

        setupRegionSelector()
        loadLimeHdRegions()
        setupQualitySelector()
        setupLanguageSelector()
        setupSourceSelector()
    }

    private fun setupRegionSelector() {
        regions = fallbackRegions()
        currentRegionIndex = indexOfRegion(preferences.limehdRegion)
        updateRegionDisplay()

        regionButton.setOnClickListener { showRegionDialog() }
    }

    private fun showRegionDialog() {
        if (regions.isEmpty()) return
        val labels = regions.map { (code, name) ->
            val base = if (code == 0) getString(R.string.region_auto) else name
            if (code == regions[currentRegionIndex].first) "$base ${getString(R.string.source_current)}" else base
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.region_select_title))
            .setSingleChoiceItems(labels.toTypedArray(), currentRegionIndex) { dialog, which ->
                currentRegionIndex = which
                applyRegion(regions[which])
                updateRegionDisplay()
                dialog.dismiss()
            }
            .show()
    }

    private fun loadLimeHdRegions() {
        uiScope.launch {
            val loaded = withContext(Dispatchers.IO) { limeHdClient.getRegions() }
            if (loaded.isEmpty()) return@launch

            limeHdRegions = loaded
            regions = buildRegionList()

            val savedCode = preferences.limehdRegion
            val savedIndex = regions.indexOfFirst { it.first == savedCode }
            if (savedCode != 0 && savedIndex < 0) {
                regions = regions + (savedCode to (preferences.limehdRegionName ?: savedCode.toString()))
                currentRegionIndex = regions.size - 1
            } else {
                currentRegionIndex = savedIndex.coerceAtLeast(0)
            }

            updateRegionDisplay()
        }
    }

    private fun fallbackRegions(): List<Pair<Int, String>> {
        val savedCode = preferences.limehdRegion
        val savedName = preferences.limehdRegionName
            ?: RegionList.regions.firstOrNull { it.first == savedCode }?.second
            ?: getString(R.string.region_default_moscow)

        val result = mutableListOf<Pair<Int, String>>()
        result.add(0 to getString(R.string.region_auto))
        if (savedCode != 0) {
            result.add(savedCode to savedName)
        }
        return result
    }

    private fun buildRegionList(): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        result.add(0 to getString(R.string.region_auto))
        result.addAll(limeHdRegions.filter { it.code != 0 }.map { it.code to it.name })
        return result
    }

    private fun indexOfRegion(code: Int): Int = regions.indexOfFirst { it.first == code }.takeIf { it != -1 } ?: 0

    private fun applyRegion(region: Pair<Int, String>) {
        val code = region.first
        val name = if (code == 0) getString(R.string.region_auto) else region.second

        if (code != preferences.limehdRegion) {
            ChannelRepository.invalidateContent()
            android.widget.Toast.makeText(this, getString(R.string.region_changed, name), android.widget.Toast.LENGTH_SHORT).show()
        }

        preferences.limehdRegion = code
        preferences.limehdRegionName = name

        val effectiveCode = if (code != 0) code else (limeHdClient.autoRegionCode() ?: 0)
        val regionData = limeHdRegions.firstOrNull { it.code == effectiveCode }

        preferences.limehdRegionTzOffsets = regionData?.tzOffsets ?: emptyList()

        val legacyId = legacyRegionId(effectiveCode, regionData?.name ?: name)
        if (legacyId != 0) {
            preferences.regionId = legacyId
        }
    }

    private fun updateRegionDisplay() {
        if (currentRegionIndex in regions.indices) {
            regionButton.text = displayRegionName(regions[currentRegionIndex].first)
        }
    }

    private fun displayRegionName(code: Int): String {
        if (code != 0) {
            return limeHdRegions.firstOrNull { it.code == code }?.name
                ?: preferences.limehdRegionName
                ?: code.toString()
        }

        val autoCode = limeHdClient.autoRegionCode()
        if (autoCode != null && autoCode > 0) {
            return limeHdRegions.firstOrNull { it.code == autoCode }?.name
                ?: getString(R.string.region_auto)
        }
        return getString(R.string.region_auto)
    }

    private fun legacyRegionId(code: Int, name: String): Int {
        val exact = RegionList.regions.firstOrNull { it.second.equals(name, ignoreCase = true) }?.first
        if (exact != null) return exact

        val normalized = normalizeRegionName(name)
        if (normalized.isBlank()) return 0

        return RegionList.regions.firstOrNull {
            val oldName = normalizeRegionName(it.second)
            !oldName.isBlank() && (oldName.contains(normalized) || normalized.contains(oldName))
        }?.first ?: 0
    }

    private fun normalizeRegionName(value: String): String = value.lowercase().replace(",", " ").replace(".", " ").replace("  ", " ").trim()

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
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
                AppPreferences.ContentSource.LIMEHD -> getString(R.string.source_limehd)
                AppPreferences.ContentSource.PREMIER -> getString(R.string.source_premier)
                AppPreferences.ContentSource.IVI -> getString(R.string.source_ivi)
                AppPreferences.ContentSource.SMOTRESHKA -> getString(R.string.source_smotreshka)
            }
        }
        val selected = sources.indexOf(preferences.contentSource).takeIf { it != -1 } ?: 0
        buildOptionRow(sourceOptions, labels, selected) { index ->
            if (sources[index] != preferences.contentSource) {
                ChannelRepository.invalidateContent()
            }
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
