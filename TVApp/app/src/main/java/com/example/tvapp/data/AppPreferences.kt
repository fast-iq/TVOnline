package com.example.tvapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    companion object {
        private const val KEY_LAST_CHANNEL = "last_channel_id"
        private const val KEY_TIMEZONE_OFFSET = "timezone_offset"
        private const val KEY_QUALITY_MODE = "quality_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_CONTENT_SOURCE = "content_source"
        const val DEFAULT_MOSCOW_OFFSET = 3

        // epgservice.ru API token (получается через https://t.me/EPGServiceSupportBot)
        // Пустая строка = EPG-сервис отключён, используется фолбэк
        const val EPG_SERVICE_TOKEN = "987c6312-e354-4abe-b964-6862ab29175"
    }

    var lastChannelId: String?
        get() = prefs.getString(KEY_LAST_CHANNEL, null)
        set(value) {
            prefs.edit { putString(KEY_LAST_CHANNEL, value) }
        }

    var timezoneOffset: Int
        get() {
            val stored = prefs.getInt(KEY_TIMEZONE_OFFSET, Int.MIN_VALUE)
            if (stored == Int.MIN_VALUE) {
                val offsetMinutes = java.util.TimeZone.getDefault().getRawOffset() / 60000
                return offsetMinutes / 60
            }
            return stored
        }
        set(value) {
            prefs.edit { putInt(KEY_TIMEZONE_OFFSET, value) }
        }

    var qualityMode: QualityMode
        get() {
            val idx = prefs.getInt(KEY_QUALITY_MODE, QualityMode.AUTO.ordinal)
            return if (idx in QualityMode.values().indices) QualityMode.values()[idx] else QualityMode.AUTO
        }
        set(value) {
            prefs.edit { putInt(KEY_QUALITY_MODE, value.ordinal) }
        }

    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ru") ?: "ru"
        set(value) {
            prefs.edit { putString(KEY_LANGUAGE, value) }
        }

    var contentSource: ContentSource
        get() {
            val idx = prefs.getInt(KEY_CONTENT_SOURCE, ContentSource.PREMIER.ordinal)
            return if (idx in ContentSource.values().indices) ContentSource.values()[idx] else ContentSource.PREMIER
        }
        set(value) {
            prefs.edit { putInt(KEY_CONTENT_SOURCE, value.ordinal) }
        }

    enum class QualityMode {
        MINIMUM,
        MEDIUM,
        MAXIMUM,
        AUTO
    }

    enum class ContentSource {
        PREMIER,
        IVI,
        SMOTRESHKA
    }
}
