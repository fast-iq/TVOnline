package com.example.tvapp.ui

import com.example.tvapp.data.Channel

/**
 * Позиции карточек в адаптере сетки каналов. В режиме ALL перед каждой
 * категорией стоит заголовок-строка (see [ChannelGrouping.buildItems]) —
 * всё отображение считается от него. Чистая логика, покрыта тестами.
 */
internal object GridPositions {

    /** Позиция карточки канала в ленте с заголовками категорий; -1 — канала в списке нет. */
    fun groupedPosition(channels: List<Channel>, channelId: String): Int {
        val channel = channels.find { it.id == channelId } ?: return -1
        val preceding = ChannelGrouping.categoriesInOrder(channels)
            .takeWhile { it != channel.category }
            .sumOf { 1 + ChannelGrouping.channelsByCategory(channels, it).size }
        val indexInCategory = ChannelGrouping.channelsByCategory(channels, channel.category)
            .indexOfFirst { it.id == channel.id }
        return if (indexInCategory < 0) -1 else preceding + 1 + indexInCategory
    }

    /** Индекс канала в плоском списке по позиции адаптера (в режиме без заголовков это она и есть). */
    fun cardIndexAt(position: Int, grouped: Boolean, isHeaderAt: (Int) -> Boolean): Int {
        if (!grouped) return position
        var cards = 0
        for (pos in 0 until position) {
            if (!isHeaderAt(pos)) cards++
        }
        return cards
    }
}
