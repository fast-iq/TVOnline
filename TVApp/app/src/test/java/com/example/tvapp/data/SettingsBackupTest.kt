package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    @Test
    fun roundTripPreservesAllTypes() {
        val source = mapOf<String, Any>(
            "language" to "ru",
            "timezone_offset" to 3,
            "some_long" to 1234567890123L,
            "quality_float" to 0.75f,
            "flag" to true,
            "favorite_channel_ids" to setOf("limehd_1", "limehd_2")
        )
        val decoded = SettingsBackup.decode(SettingsBackup.encode(source))
        assertEquals("ru", decoded["language"])
        assertEquals(3, decoded["timezone_offset"])
        assertEquals(1234567890123L, decoded["some_long"])
        assertEquals(0.75f, decoded["quality_float"])
        assertEquals(true, decoded["flag"])
        assertEquals(setOf("limehd_1", "limehd_2"), decoded["favorite_channel_ids"])
    }

    @Test
    fun emptyMapEncodesToEmptySettings() {
        val decoded = SettingsBackup.decode(SettingsBackup.encode(emptyMap<String, Any>()))
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun unsupportedTypesAreSkipped() {
        val source = mapOf<String, Any>(
            "ok" to "value",
            "unsupported" to listOf(1, 2, 3)
        )
        val decoded = SettingsBackup.decode(SettingsBackup.encode(source))
        assertEquals(mapOf<String, Any>("ok" to "value"), decoded)
    }

    @Test
    fun decodeKeepsExactTypesForPreferencePut() {
        val decoded = SettingsBackup.decode(SettingsBackup.encode(mapOf<String, Any>("i" to 42, "l" to 42L)))
        assertTrue(decoded["i"] is Int)
        assertTrue(decoded["l"] is Long)
    }

    @Test
    fun decodeSkipsGarbageEntries() {
        val json = """
            {"settings": [
                {"key":"good","type":"string","value":"v"},
                {"key":"","type":"string","value":"x"},
                {"type":"string","value":"no-key"},
                {"key":"bad","type":"unknown","value":"x"},
                42
            ]}
        """.trimIndent()
        val decoded = SettingsBackup.decode(json)
        assertEquals(mapOf<String, Any>("good" to "v"), decoded)
    }

    @Test
    fun decodeMissingSettingsArrayReturnsEmpty() {
        assertTrue(SettingsBackup.decode("{}").isEmpty())
    }

    @Test
    fun decodeMalformedJsonThrows() {
        assertThrows(org.json.JSONException::class.java) {
            SettingsBackup.decode("not json at all")
        }
    }
}
