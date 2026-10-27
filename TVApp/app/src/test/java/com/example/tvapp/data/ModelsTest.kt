package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    private val requiredFederalIds = setOf(
        "c1r", "rossiya1", "ntv", "5tv", "kultura", "zvezda", "pz", "sts",
        "domashniy", "tnt", "ren", "karusel", "match", "rossiya24", "tvc", "spas"
    )

    @Test
    fun hardcodedChannelIdsAreUnique() {
        val ids = ChannelList.hardcodedChannels.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun allFederalChannelsRequiredBySpecArePresent() {
        val ids = ChannelList.hardcodedChannels.map { it.id }.toSet()
        assertTrue(requiredFederalIds.all { it in ids })
    }

    @Test
    fun channelsHaveNameAndStream() {
        for (channel in ChannelList.hardcodedChannels) {
            assertTrue("empty name for ${channel.id}", channel.name.isNotBlank())
            assertTrue("empty stream for ${channel.id}", channel.streamUrl.isNotBlank())
            assertTrue("id must be lowercase ascii: ${channel.id}", channel.id.matches(Regex("[a-z0-9_]+")))
        }
    }

    @Test
    fun fallbackStreamsDoNotContainPrimary() {
        for (channel in ChannelList.hardcodedChannels) {
            assertFalse("duplicate fallback in ${channel.id}", channel.fallbackStreamUrls.contains(channel.streamUrl))
            assertTrue("unique fallbacks in ${channel.id}", channel.fallbackStreamUrls.size == channel.fallbackStreamUrls.toSet().size)
        }
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

    @Test
    fun regionIdsAreUnique() {
        val ids = RegionList.regions.map { it.first }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it <= 0 })
    }

    @Test
    fun regionOffsetsMatchTimezones() {
        assertEquals(-1, RegionList.offset(25))
        assertEquals(0, RegionList.offset(1))
        assertEquals(0, RegionList.offset(3))
        assertEquals(1, RegionList.offset(34))
        assertEquals(2, RegionList.offset(29))
        assertEquals(3, RegionList.offset(54))
        assertEquals(4, RegionList.offset(46))
        assertEquals(5, RegionList.offset(41))
        assertEquals(6, RegionList.offset(71))
        assertEquals(7, RegionList.offset(58))
        assertEquals(8, RegionList.offset(48))
        assertEquals(9, RegionList.offset(42))
        assertEquals(0, RegionList.offset(99999))
    }
}
