package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamSelectorTest {

    private val ng = "https://zabava-htlive.cdn.ngenix.net/hls/"

    private fun channel() = Channel(
        id = "c1r",
        name = "Первый канал",
        logoUrl = "",
        streamUrl = "https://cdn.ntv.ru/vitrina18/index.m3u8",
        fallbackStreamUrls = listOf(
            ng + "CH_1TVSD/variant.m3u8",
            ng + "CH_1TVSD_2/variant.m3u8",
            ng + "CH_1TVSD_4/variant.m3u8",
            ng + "CH_1TVSD_7/variant.m3u8"
        )
    )

    @Test
    fun `regionOffsetOf parses ngenix offset`() {
        assertEquals(4, StreamSelector.regionOffsetOf(ng + "CH_1TVSD_4/variant.m3u8"))
        assertEquals(0, StreamSelector.regionOffsetOf(ng + "CH_NTV_0/variant.m3u8"))
    }

    @Test
    fun `regionOffsetOf null for base and foreign urls`() {
        assertNull(StreamSelector.regionOffsetOf(ng + "CH_1TVSD/variant.m3u8"))
        assertNull(StreamSelector.regionOffsetOf("https://cdn.ntv.ru/vitrina18/index.m3u8"))
    }

    @Test
    fun `preferredUrl null for zero or negative region offset`() {
        assertNull(StreamSelector.preferredUrl(channel(), 1))
        assertNull(StreamSelector.preferredUrl(channel(), 25))
    }

    @Test
    fun `preferredUrl picks exact region variant`() {
        assertEquals(
            ng + "CH_1TVSD_2/variant.m3u8",
            StreamSelector.preferredUrl(channel(), 29)
        )
        assertEquals(
            ng + "CH_1TVSD_7/variant.m3u8",
            StreamSelector.preferredUrl(channel(), 58)
        )
    }

    @Test
    fun `preferredUrl picks closest variant for unmatched offset`() {
        assertEquals(
            ng + "CH_1TVSD_7/variant.m3u8",
            StreamSelector.preferredUrl(channel(), 48)
        )
    }

    @Test
    fun `preferredUrl tie prefers first listed`() {
        assertEquals(
            ng + "CH_1TVSD_2/variant.m3u8",
            StreamSelector.preferredUrl(channel(), 54)
        )
    }

    @Test
    fun `preferredUrl null when no variants available`() {
        val plain = Channel(
            id = "solo",
            name = "Solo",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina18/index.m3u8"
        )
        assertNull(StreamSelector.preferredUrl(plain, 29))
    }

    @Test
    fun `allUrls includes primary first`() {
        val urls = StreamSelector.allUrls(channel())
        assertEquals(5, urls.size)
        assertEquals("https://cdn.ntv.ru/vitrina18/index.m3u8", urls.first())
    }
}
