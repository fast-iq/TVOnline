package com.example.tvapp.ui

import android.content.Context
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

/** Цвет текущей темы активности по семантическому атрибуту (tv*-attr из attrs.xml). */
internal fun Context.themeColor(@AttrRes attr: Int): Int {
    val value = TypedValue()
    theme.resolveAttribute(attr, value, true)
    return if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
}
