package com.example.tvapp.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
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
}
