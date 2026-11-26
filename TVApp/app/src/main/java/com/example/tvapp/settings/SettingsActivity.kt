package com.example.tvapp.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.example.tvapp.BaseActivity
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.ChannelRepository
import com.example.tvapp.data.EPGRepository
import com.example.tvapp.data.LimeHdClient
import com.example.tvapp.data.LimeHdRegion
import com.example.tvapp.data.RegionsCodec
import com.example.tvapp.data.SettingsBackup
import com.example.tvapp.data.UpdateChecker
import com.example.tvapp.data.UpdateInfo
import com.example.tvapp.data.UpdateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : BaseActivity() {

    private lateinit var regionButton: TextView
    private lateinit var qualityOptions: LinearLayout
    private lateinit var languageOptions: LinearLayout
    private lateinit var refreshButton: TextView

    private val preferences by lazy { AppPreferences(applicationContext) }
    private val limeHdClient by lazy { LimeHdClient(applicationContext) }
    private val channelRepository by lazy { ChannelRepository(applicationContext) }
    private val epgRepository by lazy { EPGRepository(applicationContext) }
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())

    private var regions: List<Pair<Int, String>> = emptyList()
    private var limeHdRegions: List<LimeHdRegion> = emptyList()
    private var regionsLoading = false
    private val pendingRegionCallbacks = mutableListOf<() -> Unit>()

    private val qualityModes = listOf(
        AppPreferences.QualityMode.MINIMUM,
        AppPreferences.QualityMode.MEDIUM,
        AppPreferences.QualityMode.MAXIMUM,
        AppPreferences.QualityMode.AUTO
    )

    private val languages = listOf("ru" to "Русский", "en" to "English")

    private var currentRegionIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        regionButton = findViewById(R.id.regionButton)
        qualityOptions = findViewById(R.id.qualityOptions)
        languageOptions = findViewById(R.id.languageOptions)

        setupRegionSelector()
        ensureRegionsLoaded {}
        setupQualitySelector()
        setupLanguageSelector()
        setupRefreshButton()
        setupUpdateButton()
        setupBackupButtons()
    }

    private fun setupRegionSelector() {
        limeHdRegions = RegionsCodec.decode(preferences.limehdRegionsCache)
        if (limeHdRegions.isNotEmpty()) {
            rebuildRegionList()
        } else {
            regions = fallbackRegions()
            currentRegionIndex = indexOfRegion(preferences.limehdRegion)
        }
        updateRegionDisplay()

        regionButton.setOnClickListener { showRegionDialog() }
    }

    private fun showRegionDialog(waitedForLoad: Boolean = false) {
        if (!waitedForLoad && limeHdRegions.isEmpty()) {
            ensureRegionsLoaded {
                if (!isFinishing && !isDestroyed) showRegionDialog(waitedForLoad = true)
            }
            return
        }
        if (regions.isEmpty()) return
        val currentCode = regions[currentRegionIndex].first
        val labels = regions.map { (code, name) ->
            val base = if (code == 0) getString(R.string.region_auto) else name
            if (code == currentCode) "$base ${getString(R.string.source_current)}" else base
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

    private fun ensureRegionsLoaded(onLoaded: () -> Unit) {
        if (limeHdRegions.isNotEmpty()) {
            onLoaded()
            return
        }
        if (regionsLoading) {
            pendingRegionCallbacks.add(onLoaded)
            return
        }
        regionsLoading = true
        uiScope.launch {
            val loaded = withContext(Dispatchers.IO) { limeHdClient.getRegions() }
            regionsLoading = false
            if (loaded.isNotEmpty()) {
                limeHdRegions = loaded
                preferences.limehdRegionsCache = RegionsCodec.encode(loaded)
                rebuildRegionList()
                updateRegionDisplay()
            } else {
                android.widget.Toast.makeText(
                    this@SettingsActivity,
                    R.string.regions_load_failed,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            val pending = pendingRegionCallbacks.toList()
            pendingRegionCallbacks.clear()
            onLoaded()
            pending.forEach { it() }
        }
    }

    private fun rebuildRegionList() {
        val savedCode = preferences.limehdRegion
        val list = buildRegionList().toMutableList()
        val savedIndex = list.indexOfFirst { it.first == savedCode }
        if (savedCode != 0 && savedIndex < 0) {
            list.add(savedCode to (preferences.limehdRegionName ?: savedCode.toString()))
            currentRegionIndex = list.size - 1
        } else {
            currentRegionIndex = savedIndex.coerceAtLeast(0)
        }
        regions = list
    }

    private fun fallbackRegions(): List<Pair<Int, String>> {
        val savedCode = preferences.limehdRegion
        val savedName = preferences.limehdRegionName
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

        val regionTz = regionData?.tzOffsets?.firstOrNull()
        if (regionTz != null && regionTz != preferences.timezoneOffset) {
            preferences.timezoneOffset = regionTz
            ChannelRepository.invalidateContent()
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

    private fun setupRefreshButton() {
        refreshButton = findViewById(R.id.refreshButton)
        refreshButton.setOnClickListener {
            refreshButton.isEnabled = false
            refreshButton.text = getString(R.string.refreshing)
            uiScope.launch {
                try {
                    channelRepository.refreshChannels()
                    EPGRepository.invalidateAllCaches()
                    android.widget.Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.refresh_done),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.refresh_failed, e.message ?: "unknown"),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    refreshButton.isEnabled = true
                    refreshButton.text = getString(R.string.refresh_channels)
                }
            }
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

    private fun setupUpdateButton() {
        val button: TextView = findViewById(R.id.updateButton)
        button.setOnClickListener {
            button.isEnabled = false
            button.text = getString(R.string.update_checking)
            uiScope.launch {
                val result = try {
                    withContext(Dispatchers.IO) { UpdateChecker.checkDetailed(this@SettingsActivity) }
                } catch (e: Exception) {
                    UpdateResult.Failed
                }
                button.isEnabled = true
                button.text = getString(R.string.update_check)
                preferences.lastUpdateCheckMs = System.currentTimeMillis()
                when (result) {
                    is UpdateResult.Available -> showUpdateDialog(result.info)
                    UpdateResult.UpToDate -> Toast.makeText(this@SettingsActivity, R.string.update_latest, Toast.LENGTH_SHORT).show()
                    UpdateResult.Failed -> Toast.makeText(this@SettingsActivity, R.string.update_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available, info.version))
            .setMessage(info.notes.ifBlank { getString(R.string.update_no_notes) })
            .setPositiveButton(R.string.update_download) { _, _ ->
                try {
                    val url = info.apkUrl ?: info.url
                    if (url.isNotBlank()) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, R.string.update_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun backupFile(): File {
        val dir = getExternalFilesDir(null) ?: filesDir
        return File(dir, SETTINGS_BACKUP_FILE)
    }

    private fun setupBackupButtons() {
        val exportButton: TextView = findViewById(R.id.exportButton)
        exportButton.setOnClickListener {
            uiScope.launch {
                val path = try {
                    withContext(Dispatchers.IO) {
                        val values = PreferenceManager.getDefaultSharedPreferences(this@SettingsActivity).all
                        val file = backupFile()
                        file.writeText(SettingsBackup.encode(values), Charsets.UTF_8)
                        file.absolutePath
                    }
                } catch (e: Exception) {
                    null
                }
                if (path != null) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.export_done, path), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@SettingsActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }

        val importButton: TextView = findViewById(R.id.importButton)
        importButton.setOnClickListener {
            uiScope.launch {
                val result = try {
                    withContext(Dispatchers.IO) {
                        val file = backupFile()
                        if (!file.exists()) return@withContext null
                        val values = SettingsBackup.decode(file.readText(Charsets.UTF_8))
                        val prefs = PreferenceManager.getDefaultSharedPreferences(this@SettingsActivity)
                        if (SettingsBackup.apply(prefs, values)) values.size else null
                    }
                } catch (e: Exception) {
                    null
                }
                if (result != null) {
                    Toast.makeText(this@SettingsActivity, R.string.import_done, Toast.LENGTH_SHORT).show()
                    ChannelRepository.invalidateContent()
                    recreate()
                } else {
                    Toast.makeText(this@SettingsActivity, R.string.import_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val SETTINGS_BACKUP_FILE = "tvapp_settings.json"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> {
            finish()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
