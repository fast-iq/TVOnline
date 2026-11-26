package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkedProgramsCodecTest {

    private val mark = MarkedProgram(
        channelId = "limehd_101",
        channelName = "Первый канал",
        streamUrl = "https://mhd.example/1.m3u8?token=abc",
        title = "Время — «12:00»",
        startTime = 1_800_000_000_000L,
        endTime = 1_800_003_600_000L
    )

    @Test
    fun roundTripPreservesAllFields() {
        val decoded = MarkedProgramsCodec.decode(MarkedProgramsCodec.encode(listOf(mark)))
        assertEquals(listOf(mark), decoded)
    }

    @Test
    fun keyMatchesRowAdapterFormat() {
        assertEquals("limehd_101|1800000000000|1800003600000", mark.key)
    }

    @Test
    fun orderPreserved() {
        val marks = (1..5).map { mark.copy(channelId = "c$it", startTime = mark.startTime + it * 1000L) }
        val decoded = MarkedProgramsCodec.decode(MarkedProgramsCodec.encode(marks))
        assertEquals(marks.map { it.channelId }, decoded.map { it.channelId })
    }

    @Test
    fun emptyAndNullOrEmptyDecodeToEmptyList() {
        assertTrue(MarkedProgramsCodec.decode(null).isEmpty())
        assertTrue(MarkedProgramsCodec.decode("").isEmpty())
        assertTrue(MarkedProgramsCodec.decode(MarkedProgramsCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun invalidEntriesSkipped() {
        val json = """
            [
                {"channel_id":"ok","channel_name":"n","stream_url":"u","title":"t","start":100,"end":200},
                {"channel_id":"","start":100,"end":200},
                {"channel_id":"no-start","end":200},
                {"channel_id":"bad-range","start":300,"end":200},
                42,
                null
            ]
        """.trimIndent()
        val decoded = MarkedProgramsCodec.decode(json)
        assertEquals(listOf("ok"), decoded.map { it.channelId })
    }

    @Test
    fun malformedJsonDecodesToEmpty() {
        assertTrue(MarkedProgramsCodec.decode("{ не json").isEmpty())
    }

    @Test
    fun upcomingFiltersPastAndSorts() {
        val now = 1_000L
        val future1 = mark.copy(channelId = "f1", startTime = 3_000L, endTime = 4_000L)
        val future2 = mark.copy(channelId = "f2", startTime = 2_000L, endTime = 2_500L)
        val past = mark.copy(channelId = "p", startTime = 500L, endTime = 900L)
        val upcoming = ReminderScheduler.upcoming(listOf(future1, past, future2), now)
        assertEquals(listOf("f2", "f1"), upcoming.map { it.channelId })
    }
}
