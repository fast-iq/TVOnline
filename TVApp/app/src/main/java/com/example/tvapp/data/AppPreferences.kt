package com.example.tvapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    companion object {
        private const val KEY_LAST_CHANNEL = "last_channel_id"
        private const val KEY_TIMEZONE_OFFSET = "timezone_offset"
        private const val KEY_QUALITY_MODE = "quality_mode"
        private const val KEY_LANGUAGE = "language"
        const val DEFAULT_MOSCOW_OFFSET = 3

        // epgservice.ru API token (получается через https://t.me/EPGServiceSupportBot)
        // Пустая строка = EPG-сервис отключён, используется фолбэк
        const val EPG_SERVICE_TOKEN = ""
    }

    var lastChannelId: String?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)
        set(value) = prefs.edit().putString(KEY_LAST_CHANNEL, value).apply()

    var timezoneOffset: Int
        get() = prefs.getInt(KEY_TIMEZONE_OFFSET, DEFAULT_MOSCOW_OFFSET)
        set(value) = prefs.edit().putInt(KEY_TIMEZONE_OFFSET, value).apply()

    var qualityMode: QualityMode
        get() {
            val idx = prefs.getInt(KEY_QUALITY_MODE, QualityMode.AUTO.ordinal)
            return if (idx in QualityMode.values().indices) QualityMode.values()[idx] else QualityMode.AUTO
        }
        set(value) = prefs.edit().putInt(KEY_QUALITY_MODE, value.ordinal).apply()

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ru") ?: "ru"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    enum class QualityMode {
        MINIMUM,
        MEDIUM,
        MAXIMUM,
        AUTO
    }
}
