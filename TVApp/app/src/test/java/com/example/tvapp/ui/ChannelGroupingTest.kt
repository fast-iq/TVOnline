package com.example.tvapp.ui

import com.example.tvapp.data.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelGroupingTest {

    private fun channel(id: String, category: String = "general") = Channel(
        id = id,
        name = "Канал $id",
        logoUrl = "",
        streamUrl = "https://example.com/$id.m3u8",
        category = category
    )

    @Test
    fun categoriesInOrderKeepsFirstSeenOrder() {
        val channels = listOf(
            channel("a", "federal"),
            channel("b", "general"),
            channel("c", "federal"),
            channel("d", "sport")
        )
        assertEquals(listOf("federal", "general", "sport"), ChannelGrouping.categoriesInOrder(channels))
    }

    @Test
    fun buildItemsInterleavesHeadersAndCards() {
        val channels = listOf(
            channel("a", "federal"),
            channel("b", "general"),
            channel("c", "federal")
        )
        val items = ChannelGrouping.buildItems(channels) { cat -> cat.uppercase() }
        assertEquals(5, items.size)
        assertEquals(GridItem.Header("federal", "FEDERAL"), items[0])
        assertEquals("a", (items[1] as GridItem.Card).channel.id)
        assertEquals("c", (items[2] as GridItem.Card).channel.id)
        assertEquals(GridItem.Header("general", "GENERAL"), items[3])
        assertEquals("b", (items[4] as GridItem.Card).channel.id)
    }

    @Test
    fun favoriteChannelsFiltersByIds() {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        val favorites = setOf("a", "c", "missing")
        val result = ChannelGrouping.favoriteChannels(channels, favorites)
        assertEquals(listOf("a", "c"), result.map { it.id })
    }

    @Test
    fun historyChannelsFollowsHistoryOrderAndSkipsUnknown() {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        val result = ChannelGrouping.historyChannels(channels, listOf("c", "gone", "a"))
        assertEquals(listOf("c", "a"), result.map { it.id })
    }

    @Test
    fun emptyInputsProduceEmptyResults() {
        assertTrue(ChannelGrouping.categoriesInOrder(emptyList()).isEmpty())
        assertTrue(ChannelGrouping.buildItems(emptyList()) { it }.isEmpty())
        assertTrue(ChannelGrouping.favoriteChannels(emptyList(), setOf("a")).isEmpty())
        assertTrue(ChannelGrouping.historyChannels(listOf(channel("a")), emptyList()).isEmpty())
    }
}
