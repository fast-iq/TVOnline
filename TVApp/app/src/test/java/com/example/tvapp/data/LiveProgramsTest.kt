package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveProgramsTest {

    private val now = 1_764_000_000_000L

    private fun program(title: String, start: Long, end: Long) = Program(
        channelId = "limehd_1",
        title = title,
        description = null,
        startTime = start,
        endTime = end
    )

    private val channel = Channel(
        id = "limehd_1",
        name = "Первый канал",
        logoUrl = "",
        streamUrl = "https://host/index.m3u8"
    )

    @Test
    fun currentComesFromScheduleWhenItCoversNow() {
        val withSchedule = channel.copy(
            programs = listOf(
                program("Утро", now - 3_600_000L, now - 1_800_000L),
                program("Время", now - 60_000L, now + 1_200_000L),
                program("Ночи", now + 1_200_000L, now + 4_200_000L)
            )
        )
        assertEquals("Время", LivePrograms.current(withSchedule, now)?.title)
    }

    @Test
    fun scheduleWinsOverStaleSnapshot() {
        val stale = channel.copy(
            currentProgramTitle = "Старая передача",
            currentProgramStart = now - 7_200_000L,
            currentProgramEnd = now - 3_600_000L,
            programs = listOf(program("Актуальная", now - 60_000L, now + 600_000L))
        )
        assertEquals("Актуальная", LivePrograms.current(stale, now)?.title)
    }

    @Test
    fun fallsBackToPlaylistSnapshot() {
        val snapshot = channel.copy(
            currentProgramTitle = "Вечерние новости",
            currentProgramStart = now - 300_000L,
            currentProgramEnd = now + 900_000L
        )
        assertEquals("Вечерние новости", LivePrograms.current(snapshot, now)?.title)
    }

    @Test
    fun returnsNullWhenSnapshotWindowPassed() {
        val ended = channel.copy(
            currentProgramTitle = "Фильм",
            currentProgramStart = now - 7_200_000L,
            currentProgramEnd = now - 3_600_000L
        )
        assertNull(LivePrograms.current(ended, now))
    }

    @Test
    fun returnsNullWhenSnapshotTitleMissing() {
        val noTitle = channel.copy(
            currentProgramStart = now - 300_000L,
            currentProgramEnd = now + 900_000L
        )
        assertNull(LivePrograms.current(noTitle, now))
    }

    @Test
    fun nextReturnsEarliestUpcoming() {
        val withSchedule = channel.copy(
            programs = listOf(
                program("Позже", now + 3_600_000L, now + 5_400_000L),
                program("Скоро", now + 600_000L, now + 1_800_000L),
                program("Идёт", now - 60_000L, now + 300_000L)
            )
        )
        assertEquals("Скоро", LivePrograms.next(withSchedule, now)?.title)
    }

    @Test
    fun nextIsNullWhenNothingScheduledAhead() {
        val past = channel.copy(programs = listOf(program("Вчера", now - 7_200_000L, now - 3_600_000L)))
        assertNull(LivePrograms.next(past, now))
    }

    @Test
    fun scheduledBuildsSingleProgramFromSnapshot() {
        val snapshot = channel.copy(
            currentProgramTitle = "Снимок",
            currentProgramStart = now - 1_000L,
            currentProgramEnd = now + 1_000L
        )
        val scheduled = LivePrograms.scheduled(snapshot)
        assertEquals(1, scheduled.size)
        assertEquals("Снимок", scheduled[0].title)
        assertEquals(snapshot.currentProgramStart, scheduled[0].startTime)
    }

    @Test
    fun scheduledPrefersFullScheduleAndEmptyWithoutData() {
        val withSchedule = channel.copy(programs = listOf(program("Раз", now, now + 1L)))
        assertEquals(1, LivePrograms.scheduled(withSchedule).size)
        assertTrue(LivePrograms.scheduled(channel).isEmpty())
    }
}
