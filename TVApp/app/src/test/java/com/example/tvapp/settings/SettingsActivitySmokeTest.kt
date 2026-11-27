package com.example.tvapp.settings

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.LimeHdClient
import com.example.tvapp.data.SettingsBackup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Смоук-тесты экрана настроек: опции качества/языка отражают и сохраняют prefs,
 * отображение региона (в т.ч. fallback-список без сети), экспорт/импорт бэкапа, BACK.
 * Сеть заглушена мёртвым портом (регионы грузятся асинхронно и не мешают).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivitySmokeTest {

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences
    private var previousBaseUrl = ""

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        previousBaseUrl = LimeHdClient.baseUrl
        LimeHdClient.baseUrl = "http://127.0.0.1:9/"
        prefs = AppPreferences(context)
        backupFile().delete()
    }

    @After
    fun cleanUp() {
        LimeHdClient.baseUrl = previousBaseUrl
        backupFile().delete()
    }

    private fun backupFile(): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "tvapp_settings.json")

    private fun launch(): SettingsActivity {
        val activity = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        // Дать фоновой загрузке регионов упасть на мёртвом порте и main-треду разобрать колбэки.
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    private fun LinearLayout.button(index: Int): TextView = getChildAt(index) as TextView

    @Test
    fun qualityRowShowsFourOptionsWithSavedSelected() {
        prefs.qualityMode = AppPreferences.QualityMode.MEDIUM

        val activity = launch()
        val row = activity.findViewById<LinearLayout>(R.id.qualityOptions)

        assertEquals(4, row.childCount)
        assertTrue(row.button(1).isSelected)
        // Язык экрана задаёт BaseActivity — строки берём из активности, не из application-контекста.
        assertEquals(
            activity.getString(R.string.quality_medium),
            row.button(1).text.toString()
        )
    }

    @Test
    fun tappingQualityOptionPersistsItAndMovesSelection() {
        prefs.qualityMode = AppPreferences.QualityMode.AUTO

        val activity = launch()
        val row = activity.findViewById<LinearLayout>(R.id.qualityOptions)

        row.button(0).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(AppPreferences.QualityMode.MINIMUM, prefs.qualityMode)
        assertTrue(row.button(0).isSelected)
        assertTrue(!row.button(3).isSelected)
    }

    @Test
    fun languageRowStoresChoice() {
        prefs.language = "ru"

        val activity = launch()
        val row = activity.findViewById<LinearLayout>(R.id.languageOptions)

        assertEquals(2, row.childCount)
        row.button(1).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("en", prefs.language)
    }

    @Test
    fun regionShowsAutoByDefaultWithoutNetwork() {
        prefs.limehdRegion = 0

        val activity = launch()
        val regionButton = activity.findViewById<TextView>(R.id.regionButton)

        assertEquals(activity.getString(R.string.region_auto), regionButton.text.toString())
    }

    @Test
    fun savedRegionSurvivesWhenRegionsUnavailable() {
        prefs.limehdRegion = 66
        prefs.limehdRegionName = "Свердловская"

        val activity = launch()
        val regionButton = activity.findViewById<TextView>(R.id.regionButton)

        assertEquals("Свердловская", regionButton.text.toString())
    }

    @Test
    fun backKeyFinishesActivity() {
        val activity = launch()

        activity.onKeyDown(KeyEvent.KEYCODE_BACK, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))

        assertTrue(activity.isFinishing)
    }

    @Test
    fun exportButtonWritesBackupFile() {
        prefs.qualityMode = AppPreferences.QualityMode.MAXIMUM

        val activity = launch()
        activity.findViewById<TextView>(R.id.exportButton).performClick()
        // Экспорт идёт на Dispatchers.IO — подождать и раздать main-треду.
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        assertTrue("файл бэкапа не создан", backupFile().exists())
        val restored = SettingsBackup.decode(backupFile().readText(Charsets.UTF_8))
        assertTrue(restored.isNotEmpty())
    }

    @Test
    fun importButtonRestoresPreferences() {
        prefs.qualityMode = AppPreferences.QualityMode.MINIMUM
        val snapshot = PreferenceManager.getDefaultSharedPreferences(context).all
        backupFile().writeText(SettingsBackup.encode(snapshot), Charsets.UTF_8)
        prefs.qualityMode = AppPreferences.QualityMode.MAXIMUM

        val activity = launch()
        activity.findViewById<TextView>(R.id.importButton).performClick()
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        assertEquals(AppPreferences.QualityMode.MINIMUM, prefs.qualityMode)
    }
}
