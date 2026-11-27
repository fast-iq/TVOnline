package com.example.tvapp.ui

import com.example.tvapp.data.Channel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Позиции карточек в адаптере: сверка с фактической раскладкой
 * [ChannelGrouping.buildItems] (лента header+card) и счёт карточек через заголовки.
 */
class GridPositionsTest {

    private val channels = (1..4).map { i ->
        Channel(id = "f$i", name = "Фед $i", logoUrl = "", streamUrl = "https://e/f$i", category = "federal", number = i)
    } + (5..7).map { i ->
        Channel(id = "g$i", name = "Мест $i", logoUrl = "", streamUrl = "https://e/g$i", category = "general", number = i)
    }

    /** Порядок раскладки buildItems тот же, что у categoriesInOrder. */
    private fun headerAt(items: List<GridItem>, position: Int): Boolean = items[position] is GridItem.Header

    @Test
    fun groupedPositionMatchesBuildItemsLayout() {
        val items = ChannelGrouping.buildItems(channels) { it }

        for ((cardIndex, channel) in channels.withIndex()) {
            val actual = items.indexOfFirst { (it as? GridItem.Card)?.channel == channel }
            assertEquals("позиция ${channel.id} в ленте с заголовками", actual, GridPositions.groupedPosition(channels, channel.id))
            assertEquals("обратный ход (позиция -> индекс карточки)", cardIndex, GridPositions.cardIndexAt(actual, grouped = true) { headerAt(items, it) })
        }
    }

    @Test
    fun unknownChannelHasNoPosition() {
        assertEquals(-1, GridPositions.groupedPosition(channels, "нет-такого"))
    }

    @Test
    fun ungroupedPositionsAreIdentity() {
        // Режим избранного/истории: без заголовков позиция адаптера = индекс списка.
        assertEquals(2, GridPositions.cardIndexAt(2, grouped = false) { error("заголовки не опрашиваются") })
    }
}
