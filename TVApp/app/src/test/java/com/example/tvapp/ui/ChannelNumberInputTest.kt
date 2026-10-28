package com.example.tvapp.ui

import com.example.tvapp.data.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNumberInputTest {

    private fun ch(number: Int, name: String = "ch$number"): Channel = Channel(id = "id$number", name = name, logoUrl = "", streamUrl = "u$number", number = number)

    private val channels = listOf(
        ch(1, "Первый канал"),
        ch(2, "Россия 1"),
        ch(11, "Матч ТВ"),
        ch(12, "НТВ"),
        ch(111, "Канал 111"),
        ch(113, "Канал 113"),
        ch(21, "Канал 21"),
        ch(0, "Без номера")
    )

    @Test
    fun prefixMatchesSortedByNumber() {
        val result = ChannelNumberInput.matches(channels, "1")
        assertEquals(listOf(1, 11, 12, 111, 113), result.map { it.number })
    }

    @Test
    fun exactSelectPreferred() {
        assertEquals(1, ChannelNumberInput.select(channels, "1")?.number)
        assertEquals(11, ChannelNumberInput.select(channels, "11")?.number)
    }

    @Test
    fun noExactSelectsFirstMatch() {
        val few = listOf(ch(111), ch(113), ch(2))
        assertEquals(111, ChannelNumberInput.select(few, "11")?.number)
    }

    @Test
    fun unnumberedChannelsIgnored() {
        assertEquals(emptyList<Channel>(), ChannelNumberInput.matches(channels, "0"))
    }

    @Test
    fun noMatchReturnsNull() {
        assertNull(ChannelNumberInput.select(channels, "9"))
        assertNull(ChannelNumberInput.select(channels, ""))
        assertEquals(emptyList<Channel>(), ChannelNumberInput.matches(channels, ""))
    }
}
