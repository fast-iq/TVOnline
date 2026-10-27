package com.example.tvapp.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppPreferencesTest {

    @Test
    fun regionPersistedAcrossInstances() {
        val store = FakeSharedPreferences()
        val first = AppPreferences(store)
        assertEquals(0, first.limehdRegion)
        assertNull(first.limehdRegionName)
        assertEquals(emptyList<Int>(), first.limehdRegionTzOffsets)

        first.limehdRegion = 77
        first.limehdRegionName = "Москва"
        first.limehdRegionTzOffsets = listOf(0, 3, 5)
        first.regionId = 29

        val second = AppPreferences(store)
        assertEquals(77, second.limehdRegion)
        assertEquals("Москва", second.limehdRegionName)
        assertEquals(listOf(0, 3, 5), second.limehdRegionTzOffsets)
        assertEquals(29, second.regionId)
    }

    @Test
    fun allSettingsPersistAcrossInstances() {
        val store = FakeSharedPreferences()
        val first = AppPreferences(store)
        first.qualityMode = AppPreferences.QualityMode.MINIMUM
        first.language = "en"
        first.contentSource = AppPreferences.ContentSource.IVI
        first.lastChannelId = "ntv"
        first.timezoneOffset = 5

        val second = AppPreferences(store)
        assertEquals(AppPreferences.QualityMode.MINIMUM, second.qualityMode)
        assertEquals("en", second.language)
        assertEquals(AppPreferences.ContentSource.IVI, second.contentSource)
        assertEquals("ntv", second.lastChannelId)
        assertEquals(5, second.timezoneOffset)
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = map[key] as? MutableSet<String> ?: defValues

    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var cleared = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            pending[key!!] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            pending[key!!] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            pending[key!!] = null
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            cleared = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (cleared) {
                map.clear()
                cleared = false
            }
            pending.forEach { (key, value) ->
                if (value == null) {
                    map.remove(key)
                } else {
                    map[key] = value
                }
            }
            pending.clear()
        }
    }
}
