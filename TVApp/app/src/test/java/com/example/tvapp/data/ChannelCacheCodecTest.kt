package com.example.tvapp.data

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelCacheCodecTest {

    private val fullChannel = Channel(
        id = "limehd_10100",
        name = "Первый канал",
        logoUrl = "https://cdn.example/logo.png",
        streamUrl = "https://mhd.iptv2022.com/1kanal/variant.m3u8?token=abc",
        category = "federal",
        epgId = "10100",
        channelImageUrl = "https://cdn.example/splash.jpg",
        fallbackStreamUrls = listOf("https://alt1/index.m3u8", "https://alt2/index.m3u8"),
        currentProgramTitle = "Время",
        currentProgramStart = 1764000000000L,
        currentProgramEnd = 1764001800000L,
        programs = listOf(
            Program(
                channelId = "limehd_10100",
                title = "Время",
                description = "Информационная программа",
                startTime = 1764000000000L,
                endTime = 1764001800000L,
                iconUrl = "https://cdn.example/epg.jpg"
            ),
            Program(
                channelId = "limehd_10100",
                title = "Вечерние новости",
                description = null,
                startTime = 1764001800000L,
                endTime = 1764003000000L,
                iconUrl = null
            )
        ),
        isDrm = true,
        regionCode = 66,
        number = 1
    )

    @Test
    fun roundTripPreservesEveryField() {
        val decoded = ChannelCacheCodec.deserialize(ChannelCacheCodec.serialize(listOf(fullChannel)))
        assertEquals(1, decoded.size)
        assertEquals(fullChannel, decoded[0])
    }

    @Test
    fun roundTripPreservesOrderAndCount() {
        val channels = (1..50).map {
            fullChannel.copy(id = "ch$it", name = "Канал $it", number = it)
        }
        val decoded = ChannelCacheCodec.deserialize(ChannelCacheCodec.serialize(channels))
        assertEquals(channels, decoded)
        assertEquals((1..50).toList(), decoded.map { it.number })
    }

    @Test
    fun roundTripKeepsCyrillicAndSpecialCharacters() {
        val channel = fullChannel.copy(
            name = "Пятница! \"Коты\" и \n Собаки\t 123",
            currentProgramTitle = "Ток-шоу: «Утро» — 100% эфир",
            programs = listOf(
                Program(
                    channelId = "x",
                    title = "Иван Васильевич меняет профессию",
                    description = "К/ф, 1973. Режиссёр: Л. Гайдай",
                    startTime = 1L,
                    endTime = 2L
                )
            )
        )
        val decoded = ChannelCacheCodec.deserialize(ChannelCacheCodec.serialize(listOf(channel)))
        assertEquals(channel, decoded[0])
    }

    @Test
    fun minimalChannelUsesDefaults() {
        val channel = Channel(
            id = "min",
            name = "Минимальный",
            logoUrl = "",
            streamUrl = "https://host/index.m3u8"
        )
        val decoded = ChannelCacheCodec.deserialize(ChannelCacheCodec.serialize(listOf(channel)))
        assertEquals(channel, decoded[0])
        assertNull(decoded[0].epgId)
        assertNull(decoded[0].currentProgramTitle)
        assertNull(decoded[0].currentProgramStart)
        assertTrue(decoded[0].programs.isEmpty())
        assertTrue(decoded[0].fallbackStreamUrls.isEmpty())
    }

    @Test
    fun channelWithoutOptionalArraysStillDecodes() {
        val decoded = ChannelCacheCodec.deserialize("""[{"id":"a","name":"A","streamUrl":"u"}]""")
        assertEquals(1, decoded.size)
        assertEquals("a", decoded[0].id)
        assertEquals("general", decoded[0].category)
        assertTrue(decoded[0].fallbackStreamUrls.isEmpty())
        assertTrue(decoded[0].programs.isEmpty())
        assertNull(decoded[0].currentProgramStart)
    }

    @Test
    fun zeroProgramTimesBecomeNull() {
        val channel = fullChannel.copy(currentProgramStart = 0L, currentProgramEnd = 0L)
        val decoded = ChannelCacheCodec.deserialize(ChannelCacheCodec.serialize(listOf(channel)))
        assertNull(decoded[0].currentProgramStart)
        assertNull(decoded[0].currentProgramEnd)
    }

    @Test
    fun emptyProgramStringsBecomeNull() {
        val json = """
            [{"id":"a","name":"A","streamUrl":"u","programs":[
              {"channelId":"a","title":"T","description":"","startTime":10,"endTime":20,"iconUrl":""}
            ]}]
        """.trimIndent()
        val decoded = ChannelCacheCodec.deserialize(json)
        assertEquals(1, decoded[0].programs.size)
        val program = decoded[0].programs[0]
        assertNull(program.description)
        assertNull(program.iconUrl)
        assertEquals(10L, program.startTime)
        assertEquals(20L, program.endTime)
    }

    @Test
    fun nonObjectAndGarbageEntriesAreSkipped() {
        val json = """["мусор", 42, null, {"id":"ok","name":"OK","streamUrl":"u"}]"""
        val decoded = ChannelCacheCodec.deserialize(json)
        assertEquals(1, decoded.size)
        assertEquals("ok", decoded[0].id)
    }

    @Test
    fun emptyListRoundTripsToEmptyList() {
        assertEquals("[]", ChannelCacheCodec.serialize(emptyList()))
        assertTrue(ChannelCacheCodec.deserialize("[]").isEmpty())
    }

    @Test(expected = JSONException::class)
    fun malformedJsonThrowsForCallerToCatch() {
        ChannelCacheCodec.deserialize("это не json")
    }
}
