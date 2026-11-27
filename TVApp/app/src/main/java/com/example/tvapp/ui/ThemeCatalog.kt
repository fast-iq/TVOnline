package com.example.tvapp.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences

/** Доступные темы оформления (цвета + фон + шрифт). id хранится в AppPreferences.uiTheme. */
enum class UiTheme(val id: String, @StringRes val labelRes: Int) {
    MIDNIGHT("midnight", R.string.theme_midnight),
    EMERALD("emerald", R.string.theme_emerald),
    SUNSET("sunset", R.string.theme_sunset),
    LIGHT("light", R.string.theme_light);

    companion object {
        val ordered: List<UiTheme> = listOf(MIDNIGHT, EMERALD, SUNSET, LIGHT)

        /** Неизвестный/повреждённый id — тема по умолчанию, падать нельзя. */
        fun fromId(id: String?): UiTheme = values().firstOrNull { it.id == id } ?: MIDNIGHT
    }
}

/** Соответствие темы стилям активности. */
object ThemeCatalog {

    /** black — чёрная сцена (плеер): палитра темы, но фон чёрный и текст светлый. */
    @StyleRes
    fun styleFor(theme: UiTheme, black: Boolean): Int = when (theme) {
        UiTheme.MIDNIGHT -> if (black) R.style.Theme_TVApp_Black else R.style.Theme_TVApp
        UiTheme.EMERALD -> if (black) R.style.Theme_TVApp_Emerald_Black else R.style.Theme_TVApp_Emerald
        UiTheme.SUNSET -> if (black) R.style.Theme_TVApp_Sunset_Black else R.style.Theme_TVApp_Sunset
        UiTheme.LIGHT -> if (black) R.style.Theme_TVApp_Light_Black else R.style.Theme_TVApp_Light
    }

    fun savedTheme(context: Context): UiTheme = UiTheme.fromId(AppPreferences(context.applicationContext).uiTheme)
}

/**
 * Иконка и баннер лаунчера под текущую тему: в манифесте четыре activity-alias
 * (.LauncherMidnight/.LauncherEmerald/.LauncherSunset/.LauncherLight), активен ровно один.
 */
object ThemeIcons {

    private fun aliasFor(theme: UiTheme): String = when (theme) {
        UiTheme.MIDNIGHT -> "LauncherMidnight"
        UiTheme.EMERALD -> "LauncherEmerald"
        UiTheme.SUNSET -> "LauncherSunset"
        UiTheme.LIGHT -> "LauncherLight"
    }

    fun aliasName(context: Context, theme: UiTheme): ComponentName = ComponentName(context, "${context.packageName}.${aliasFor(theme)}")

    /** Включаем алиас выбранной темы, остальные гасим. */
    fun apply(context: Context, theme: UiTheme) {
        val pm = context.packageManager
        for (candidate in UiTheme.ordered) {
            val alias = aliasName(context, candidate)
            val desired = if (candidate == theme) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (pm.getComponentEnabledSetting(alias) != desired) {
                pm.setComponentEnabledSetting(alias, desired, PackageManager.DONT_KILL_APP)
            }
        }
    }

    /**
     * Безопасный для запуска вариант: ТОЛЬКО включаем нужный алиас, ничего не
     * отключаем. Отключение видимого лаунчер-алиаса система наказывает убийством
     * приложения (даже с DONT_KILL_APP) — полное переключение делает [apply],
     * но только когда приложение ушло в фон.
     */
    fun enableForLaunch(context: Context, theme: UiTheme) {
        val pm = context.packageManager
        val alias = aliasName(context, theme)
        if (pm.getComponentEnabledSetting(alias) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
    }
}

/** Цвет текущей темы активности по семантическому атрибуту (tv*-attr из attrs.xml). */
internal fun Context.themeColor(@AttrRes attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
}
