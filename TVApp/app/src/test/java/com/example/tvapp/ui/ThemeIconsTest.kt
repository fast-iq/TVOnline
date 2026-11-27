package com.example.tvapp.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.tvapp.TVApplication
import com.example.tvapp.data.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Переключение лаунчер-алиасов по теме: активен ровно один, повторный вызов
 * не дёргает PackageManager без нужды (setComponentEnabledSetting дорог на ТВ).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeIconsTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun state(alias: String): Int = context.packageManager.getComponentEnabledSetting(ComponentName(context, "${context.packageName}.$alias"))

    @Test
    fun applyingEmeraldEnablesOnlyEmeraldAlias() {
        ThemeIcons.apply(context, UiTheme.EMERALD)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state("LauncherEmerald"))
        for (alias in listOf("LauncherMidnight", "LauncherSunset", "LauncherLight")) {
            assertEquals("алиас $alias должен быть выключен", PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(alias))
        }
    }

    @Test
    fun switchingThemeMovesActiveAlias() {
        ThemeIcons.apply(context, UiTheme.SUNSET)
        ThemeIcons.apply(context, UiTheme.LIGHT)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state("LauncherLight"))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state("LauncherSunset"))
    }

    @Test
    fun applyingTwiceIsIdempotent() {
        ThemeIcons.apply(context, UiTheme.MIDNIGHT)
        ThemeIcons.apply(context, UiTheme.MIDNIGHT)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state("LauncherMidnight"))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state("LauncherEmerald"))
    }

    /**
     * Прикладной краш-фикс: при ЗАПУСКЕ мы только включаем нужный алиас —
     * отключение «живого» лаунчер-алиаса система наказывает убийством процесса.
     */
    @Test
    fun enableForLaunchNeverDisablesOtherAliases() {
        ThemeIcons.enableForLaunch(context, UiTheme.EMERALD)

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state("LauncherEmerald"))
        for (alias in listOf("LauncherMidnight", "LauncherSunset", "LauncherLight")) {
            assertTrue(
                "алиас $alias не должен быть отключён при запуске",
                state(alias) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            )
        }
    }

    /** Фоновый отложенный своп из TVApplication: полный apply, активен ровно один. */
    @Test
    fun deferredBackgroundSwapActivatesOnlySavedTheme() {
        AppPreferences(context).uiTheme = "sunset"
        val app = RuntimeEnvironment.getApplication() as TVApplication

        app.applyLauncherIconNow()

        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, state("LauncherSunset"))
        for (alias in listOf("LauncherMidnight", "LauncherEmerald", "LauncherLight")) {
            assertEquals("алиас $alias должен быть выключен", PackageManager.COMPONENT_ENABLED_STATE_DISABLED, state(alias))
        }
    }
}
