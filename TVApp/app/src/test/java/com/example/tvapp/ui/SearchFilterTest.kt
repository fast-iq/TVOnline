package com.example.tvapp.ui

import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {

    private fun channel(id: String, name: String) = Channel(
        id = id,
        name = name,
        logoUrl = "",
        streamUrl = "https://example.com/$id.m3u8"
    )

    @After
    fun tearDown() {
        ChannelList.updateChannels(emptyList())
    }

    @Test
    fun emptyQueryReturnsAllChannels() {
        ChannelList.updateChannels(listOf(channel("1", "Первый"), channel("2", "НТВ")))
        assertEquals(2, SearchActivity.filterChannels("").size)
        assertEquals(2, SearchActivity.filterChannels("   ").size)
    }

    @Test
    fun queryIsCaseInsensitiveAndIgnoresSpaces() {
        ChannelList.updateChannels(
            listOf(
                channel("1", "Первый канал"),
                channel("2", "Матч ТВ"),
                channel("3", "НТВ")
            )
        )
        assertEquals(listOf("1"), SearchActivity.filterChannels("первыйканал").map { it.id })
        assertEquals(listOf("1"), SearchActivity.filterChannels("ПЕРВЫЙ КАНАЛ").map { it.id })
        assertEquals(listOf("2"), SearchActivity.filterChannels("матч тв").map { it.id })
    }

    @Test
    fun substringMatchOnlyNoResults() {
        ChannelList.updateChannels(listOf(channel("1", "НТВ"), channel("2", "ТНТ")))
        assertEquals(2, SearchActivity.filterChannels("т").size)
        assertEquals(1, SearchActivity.filterChannels("тв").size)
        assertTrue(SearchActivity.filterChannels("нет такого").isEmpty())
    }

    @Test
    fun emptyChannelListReturnsEmpty() {
        ChannelList.updateChannels(emptyList())
        assertTrue(SearchActivity.filterChannels("нтв").isEmpty())
    }
}
