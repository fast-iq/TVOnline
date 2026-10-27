package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EpgParserTest {

    @Test
    fun parseXmltvTimeConvertsWithTimezone() {
        assertEquals(Instant.parse("2026-09-27T03:00:00Z").toEpochMilli(), EpgParser.parseXmltvTime("20260927060000 +0300"))
        assertEquals(Instant.parse("2026-09-27T17:00:00Z").toEpochMilli(), EpgParser.parseXmltvTime("20260927120000 -0500"))
        assertEquals(Instant.parse("2026-09-27T00:00:00Z").toEpochMilli(), EpgParser.parseXmltvTime("20260927000000 +0000"))
    }

    @Test
    fun parseXmltvTimeRejectsMalformed() {
        assertNull(EpgParser.parseXmltvTime("2026-09-27 06:00"))
        assertNull(EpgParser.parseXmltvTime(""))
        assertNull(EpgParser.parseXmltvTime("20260927060000"))
    }

    @Test
    fun computeNameMatchScore() {
        assertEquals(10, EpgParser.computeNameMatchScore("нтв", "нтв"))
        assertEquals(8, EpgParser.computeNameMatchScore("нтв", "нтв мир"))
        assertEquals(6, EpgParser.computeNameMatchScore("первый канал", "первый"))
        assertEquals(4, EpgParser.computeNameMatchScore("россия сегодня", "россия 1 сегодня"))
        assertEquals(1, EpgParser.computeNameMatchScore("матч тв", "матч планета"))
        assertEquals(0, EpgParser.computeNameMatchScore("абвгд", "дёргф"))
    }

    @Test
    fun findMatchingBrace() {
        val text = "xx {a{b}c} yy"
        val open = text.indexOf('{')
        assertEquals(9, EpgParser.findMatchingBrace(text, open))
        assertNull(EpgParser.findMatchingBrace(text, 0))
        assertNull(EpgParser.findMatchingBrace("{unclosed", 0))
    }

    @Test
    fun getWeekMondayReturnsMoscowWeekStart() {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Europe/Moscow")
        val sunday = sdf.parse("2026-10-25 12:00")!!
        val monday = sdf.parse("2026-10-19 23:59")!!
        val formatter = SimpleDateFormat("yyyyMMdd", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("Europe/Moscow")
        assertEquals("20261019", formatter.format(EpgParser.getWeekMondayDate(sunday)))
        assertEquals("20261019", formatter.format(EpgParser.getWeekMondayDate(monday)))
    }

    @Test
    fun generateFallbackEpgIsContinuousFromMoscowMidnight() {
        val channel = ChannelList.hardcodedChannels.first { it.id == "c1r" }
        val baseDate = Date()
        val programs = EpgParser.generateFallbackEPG("c1r", baseDate)
        assertTrue(programs.isNotEmpty())
        assertEquals(channel.id, programs.first().channelId)

        val templates = EpgParser.getChannelTemplates("c1r")
        assertEquals(templates.size, programs.size)

        for (i in 1 until programs.size) {
            assertEquals(programs[i - 1].endTime, programs[i].startTime)
        }
        assertTrue(programs.first().title.isNotBlank())

        val midnight = EpgParser.getWeekMondayDate(baseDate).time
        assertTrue(programs.first().startTime >= midnight)
    }

    @Test
    fun generateFallbackEpgUnknownChannelIsEmpty() {
        assertTrue(EpgParser.generateFallbackEPG("nesuschestvuet", Date()).isEmpty())
    }

    @Test
    fun currentAndNextProgramSelection() {
        val repo = EPGRepository()
        val now = System.currentTimeMillis()
        val programs = listOf(
            Program("ch", "Было", null, now - 7_200_000, now - 3_600_000),
            Program("ch", "Сейчас", null, now - 600_000, now + 3_600_000),
            Program("ch", "Будет", null, now + 3_600_000, now + 7_200_000)
        )
        assertEquals("Сейчас", repo.getCurrentProgram(programs, now)?.title)
        assertEquals("Будет", repo.getNextProgram(programs, now)?.title)
        assertNull(repo.getCurrentProgram(emptyList(), now))
        assertNotNull(repo.getNextProgram(programs, now))
    }
}
