package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class EpgParserTest {

    @Test
    fun generateFallbackEpgIsContinuousFromMoscowMidnight() {
        ChannelList.updateChannels(
            listOf(
                Channel(
                    id = "limehd_105",
                    name = "Первый канал",
                    logoUrl = "",
                    streamUrl = "https://example.com/1.m3u8",
                    category = "news"
                )
            )
        )
        val baseDate = Date()
        val programs = EpgParser.generateFallbackEPG("limehd_105", baseDate)
        assertTrue(programs.isNotEmpty())
        assertEquals("limehd_105", programs.first().channelId)

        val templates = EpgParser.getChannelTemplates("limehd_105")
        assertTrue(programs.size >= templates.size)

        for (i in 1 until programs.size) {
            assertEquals(programs[i - 1].endTime, programs[i].startTime)
        }
        assertTrue(programs.first().title.isNotBlank())

        val moscowMidnight = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            time = baseDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(moscowMidnight, programs.first().startTime)
        assertTrue(programs.last().endTime >= moscowMidnight + 24L * 60L * 60L * 1000L)
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
