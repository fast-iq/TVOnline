package com.example.tvapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import java.util.Locale
import java.util.TimeZone

class AppPreferences(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(PreferenceManager.getDefaultSharedPreferences(context.applicationContext))

    companion object {
        private const val KEY_LAST_CHANNEL = "last_channel_id"
        private const val KEY_TIMEZONE_OFFSET = "timezone_offset"
        private const val KEY_QUALITY_MODE = "quality_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_LIMEHD_DEVICE_ID = "limehd_device_id"
        private const val KEY_LIMEHD_REGION = "limehd_region"
        private const val KEY_LIMEHD_REGION_NAME = "limehd_region_name"
        private const val KEY_LIMEHD_REGION_TZ = "limehd_region_tz"
        private const val DEFAULT_LIMEHD_REGION = 0
        const val DEFAULT_MOSCOW_OFFSET = 3
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

    var limehdDeviceId: String?
        get() = prefs.getString(KEY_LIMEHD_DEVICE_ID, null)
        set(value) {
            prefs.edit { putString(KEY_LIMEHD_DEVICE_ID, value) }
        }

    var limehdRegion: Int
        get() = prefs.getInt(KEY_LIMEHD_REGION, DEFAULT_LIMEHD_REGION)
        set(value) {
            prefs.edit { putInt(KEY_LIMEHD_REGION, value) }
        }

    var limehdRegionName: String?
        get() = prefs.getString(KEY_LIMEHD_REGION_NAME, null)
        set(value) {
            prefs.edit { putString(KEY_LIMEHD_REGION_NAME, value) }
        }

    var limehdRegionTzOffsets: List<Int>
        get() {
            val raw = prefs.getString(KEY_LIMEHD_REGION_TZ, "") ?: ""
            return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
        set(value) {
            prefs.edit { putString(KEY_LIMEHD_REGION_TZ, value.joinToString(",")) }
        }

    fun programTimeZone(): java.util.TimeZone {
        val regionOffset = if (limehdRegion != 0) limehdRegionTzOffsets.firstOrNull() else null
        val offset = regionOffset ?: timezoneOffset
        val id = "GMT" + (if (offset >= 0) "+" else "-") + String.format(java.util.Locale.US, "%02d:00", Math.abs(offset))
        return java.util.TimeZone.getTimeZone(id)
    }

    enum class QualityMode {
        MINIMUM,
        MEDIUM,
        MAXIMUM,
        AUTO
    }
}
