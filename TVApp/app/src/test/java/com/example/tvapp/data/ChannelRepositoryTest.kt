package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChannelRepositoryTest {

    @Test
    fun cacheKeyChangesWithRegionAndTz() {
        assertEquals("limehd_0_3", ChannelRepository.cacheKeyFor(0, 3))
        assertEquals("limehd_66_5", ChannelRepository.cacheKeyFor(66, 5))
        assertNotEquals(ChannelRepository.cacheKeyFor(66, 5), ChannelRepository.cacheKeyFor(66, 7))
        assertNotEquals(ChannelRepository.cacheKeyFor(77, 3), ChannelRepository.cacheKeyFor(66, 3))
    }
}
