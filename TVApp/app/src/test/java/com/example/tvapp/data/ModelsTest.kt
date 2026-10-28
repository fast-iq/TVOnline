package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun channelListUpdatesAtomically() {
        val first = listOf(Channel("limehd_1", "Первый", "", "https://example.com/1.m3u8"))
        ChannelList.updateChannels(first)
        assertEquals(1, ChannelList.channels.size)
        assertEquals("limehd_1", ChannelList.channels[0].id)

        val second = listOf(
            Channel("limehd_2", "Россия 1", "", "https://example.com/2.m3u8"),
            Channel("limehd_3", "НТВ", "", "https://example.com/3.m3u8")
        )
        ChannelList.updateChannels(second)
        assertEquals(2, ChannelList.channels.size)
        assertEquals("limehd_3", ChannelList.channels[1].id)
    }

    @Test
    fun channelDefaultsAreSane() {
        val channel = Channel("limehd_1", "Канал", "", "https://example.com/1.m3u8")
        assertEquals("general", channel.category)
        assertEquals(emptyList<String>(), channel.fallbackStreamUrls)
        assertEquals(emptyList<Program>(), channel.programs)
        assertFalse(channel.isDrm)
        assertEquals(0, channel.regionCode)
    }

    @Test
    fun programIsLiveIsInclusiveAndHelpers() {
        val program = Program("ch", "T", null, 1000, 5000)
        assertTrue(program.isLive(1000))
        assertTrue(program.isLive(3000))
        assertTrue(program.isLive(5000))
        assertFalse(program.isLive(999))
        assertFalse(program.isLive(5001))
        assertEquals(4000, program.getDuration())
        assertEquals(0, program.getElapsedTime(500))
        assertEquals(2000, program.getElapsedTime(3000))
    }
}
