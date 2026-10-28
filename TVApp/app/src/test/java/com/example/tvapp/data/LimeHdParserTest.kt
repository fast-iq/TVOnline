package com.example.tvapp.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LimeHdParserTest {

    @Test
    fun parseRegionsSkipsInvalidAndSortsByName() {
        val array = JSONArray(
            """
            [
              {"code": 5, "name": "Брянская", "tz": [3, "x"]},
              {"code": 0, "name": "Нулевой"},
              {"code": 3, "name": "Амурская", "tz": []},
              {"code": 7, "name": "   "}
            ]
            """.trimIndent()
        )
        val regions = LimeHdParser.parseRegions(array)
        assertEquals(listOf("Амурская", "Брянская"), regions.map { it.name })
        assertEquals(listOf(3), regions[1].tzOffsets)
        assertTrue(regions[0].tzOffsets.isEmpty())
    }

    @Test
    fun parseRegionsNullReturnsEmpty() {
        assertTrue(LimeHdParser.parseRegions(null).isEmpty())
    }

    @Test
    fun parseTzOffsetsParsesValuesAndSkipsGarbage() {
        assertEquals(listOf(5, 6), LimeHdParser.parseTzOffsets(JSONArray("[5, 6]")))
        assertEquals(listOf(-1, 3), LimeHdParser.parseTzOffsets(JSONArray("[-1, \"x\", 3]")))
        assertTrue(LimeHdParser.parseTzOffsets(null).isEmpty())
        assertTrue(LimeHdParser.parseTzOffsets(JSONArray("[]")).isEmpty())
    }

    @Test
    fun parseChannelsFiltersInvalidAndSortsByNumber() {
        val array = JSONArray(
            """
            [
              {
                "number": 2, "our_id": 123, "name_ru": "Первый канал", "available": true,
                "is_federal": true,
                "stream": {"common": "https://a/common.m3u8", "cdn": "https://a/cdn.m3u8"},
                "foreign_url": "https://a/foreign.m3u8",
                "url": "https://a/common.m3u8",
                "cached_logo_url": "https://logo/a.png",
                "drm": true,
                "region_code": 66,
                "current": [
                  {"title": "Вечер", "timestart": 300, "timestop": 400},
                  {"title": "Утро", "timestart": 100, "timestop": 200}
                ]
              },
              {"number": 1, "our_id": 45, "title": "Тест", "url": "https://b/x"},
              {"number": 9, "available": false, "our_id": 7, "title": "Выкл", "stream": {"common": "https://dead"}},
              {"number": 8, "our_id": 8, "title": "Дизейбл", "is_disabled_player": 1, "stream": {"common": "https://x"}},
              {"number": 7, "title": "Нет id", "stream": {"common": "https://noid"}},
              {"number": 6, "our_id": 6, "title": "Нет потока"}
            ]
            """.trimIndent()
        )
        val channels = LimeHdParser.parseChannels(array)
        assertEquals(2, channels.size)

        val first = channels[0]
        assertEquals("limehd_45", first.id)
        assertEquals("Тест", first.name)
        assertEquals("https://b/x", first.streamUrl)
        assertEquals(0, first.regionCode)

        val second = channels[1]
        assertEquals("limehd_123", second.id)
        assertEquals("Первый канал", second.name)
        assertEquals("https://a/common.m3u8", second.streamUrl)
        assertEquals(
            listOf(
                "https://a/common.m3u8",
                "https://a/foreign.m3u8",
                "https://a/cdn.m3u8"
            ),
            second.fallbackStreamUrls
        )
        assertEquals("https://logo/a.png", second.logoUrl)
        assertEquals("federal", second.category)
        assertEquals("123", second.epgId)
        assertTrue(second.isDrm)
        assertEquals(66, second.regionCode)
        assertEquals("Вечер", second.currentProgramTitle)
        assertEquals(300_000L, second.currentProgramStart)
        assertEquals(400_000L, second.currentProgramEnd)
        assertEquals(listOf("Утро", "Вечер"), second.programs.map { it.title })
    }

    @Test
    fun parseEpgSortsAndSkipsInvalid() {
        val root = JSONObject(
            """
            {
              "epg": [
                {
                  "data": [
                    {"title": "Позже", "timestart": 200, "timestop": 300, "preview": "https://img"},
                    {"title": "", "timestart": 10, "timestop": 20},
                    {"title": "Без времен", "timestart": 0, "timestop": 0},
                    {"title": "Раньше", "timestart": 100, "timestop": 150, "desc": "Описание"}
                  ]
                }
              ]
            }
            """.trimIndent()
        )
        val programs = LimeHdParser.parseEpg(root, "limehd_1")
        assertEquals(2, programs.size)
        assertEquals("Раньше", programs[0].title)
        assertEquals(100_000L, programs[0].startTime)
        assertEquals(150_000L, programs[0].endTime)
        assertEquals("Описание", programs[0].description)
        assertEquals("https://img", programs[1].iconUrl)
        assertNull(programs[1].description)
    }

    @Test
    fun parseEpgWithoutArrayReturnsEmpty() {
        assertTrue(LimeHdParser.parseEpg(JSONObject("{}"), "limehd_1").isEmpty())
    }

    @Test
    fun programTimesAreUtcEpochSecondsScaledToMillis() {
        val root = JSONObject(
            """
            {
              "epg": [
                {"data": [{"title": "Т", "timestart": 1761600000, "timestop": 1761603600}]}
              ]
            }
            """.trimIndent()
        )
        val program = LimeHdParser.parseEpg(root, "limehd_1").single()
        assertEquals(Instant.parse("2025-10-27T21:20:00Z").toEpochMilli(), program.startTime)
    }
}
