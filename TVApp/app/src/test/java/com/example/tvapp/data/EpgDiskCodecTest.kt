package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgDiskCodecTest {

    private val savedAt = 1_764_000_000_000L
    private val ttl = 6 * 60 * 60 * 1000L

    private fun program(title: String, start: Long, end: Long, description: String? = null) = Program(
        channelId = "limehd_1",
        title = title,
        description = description,
        startTime = start,
        endTime = end,
        iconUrl = null
    )

    @Test
    fun roundTripPreservesEntriesAndPrograms() {
        val entries = mapOf(
            "limehd_1_77_3_0_1_20261126" to listOf(
                program("Время", savedAt - 60_000L, savedAt + 1_200_000L, "Информационная программа"),
                program("Сериал", savedAt + 1_200_000L, savedAt + 4_200_000L)
            ),
            "limehd_2_77_3_0_1_20261126" to listOf(program("Новости", savedAt, savedAt + 1_800_000L))
        )
        val decoded = EpgDiskCodec.decode(EpgDiskCodec.encode(savedAt, entries), savedAt, ttl)
        assertEquals(entries, decoded)
    }

    @Test
    fun programsComeBackSortedByStartTime() {
        val entries = mapOf(
            "k" to listOf(
                program("Позже", savedAt + 3_600_000L, savedAt + 5_400_000L),
                program("Раньше", savedAt, savedAt + 1_800_000L)
            )
        )
        val decoded = EpgDiskCodec.decode(EpgDiskCodec.encode(savedAt, entries), savedAt, ttl)
        assertEquals(listOf("Раньше", "Позже"), decoded.getValue("k").map { it.title })
    }

    @Test
    fun expiredFileIsIgnored() {
        val json = EpgDiskCodec.encode(savedAt, mapOf("k" to listOf(program("Т", savedAt, savedAt + 1L))))
        assertTrue(EpgDiskCodec.decode(json, savedAt + ttl + 1L, ttl).isEmpty())
    }

    @Test
    fun fileNearTtlBoundaryIsAccepted() {
        val json = EpgDiskCodec.encode(savedAt, mapOf("k" to listOf(program("Т", savedAt, savedAt + 1L))))
        assertEquals(1, EpgDiskCodec.decode(json, savedAt + ttl - 1L, ttl).size)
    }

    @Test
    fun missingOrGarbageSavedAtYieldsNothing() {
        assertTrue(EpgDiskCodec.decode("""{"entries":{}}""", savedAt, ttl).isEmpty())
        assertTrue(EpgDiskCodec.decode("""{"saved_at":0,"entries":{}}""", savedAt, ttl).isEmpty())
        assertTrue(EpgDiskCodec.decode("""{"saved_at":"не число","entries":{}}""", savedAt, ttl).isEmpty())
    }

    @Test
    fun emptyProgramStringsBecomeNull() {
        val json = """
            {"saved_at":$savedAt,"entries":{"k":[{"channelId":"c","title":"Т","description":"","startTime":1,"endTime":2,"iconUrl":""}]}}
        """.trimIndent()
        val program = EpgDiskCodec.decode(json, savedAt, ttl).getValue("k").single()
        assertNull(program.description)
        assertNull(program.iconUrl)
    }

    @Test
    fun garbageEntriesAndProgramsAreSkipped() {
        val json = """
            {"saved_at":$savedAt,"entries":{
              "good":[{"title":"Т","channelId":"c","startTime":1,"endTime":2},"мусор",{"channelId":"c","startTime":3,"endTime":4}],
              "empty":[],
              "broken":42
            }}
        """.trimIndent()
        val decoded = EpgDiskCodec.decode(json, savedAt, ttl)
        assertEquals(listOf("good"), decoded.keys.toList())
        assertEquals(1, decoded.getValue("good").size)
        assertEquals("Т", decoded.getValue("good")[0].title)
    }

    @Test(expected = org.json.JSONException::class)
    fun malformedFileThrowsForCallerToCatch() {
        EpgDiskCodec.decode("это не json", savedAt, ttl)
    }
}
