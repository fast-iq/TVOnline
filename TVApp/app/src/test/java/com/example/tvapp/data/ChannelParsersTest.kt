package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChannelParsersTest {

    @Test
    fun extractNuxtDataReturnsScriptContent() {
        val html = """<html><script type="application/json" id="__NUXT_DATA__" data-ssr="true">[1,2]</script></html>"""
        assertEquals("[1,2]", ChannelParsers.extractNuxtData(html))
    }

    @Test
    fun extractNuxtDataMissingMarkerReturnsNull() {
        assertNull(ChannelParsers.extractNuxtData("<html><body>empty</body></html>"))
    }

    @Test
    fun parseNuxtArrayRejectsNonArrayJson() {
        assertNull(ChannelParsers.parseNuxtArray("{\"a\":1}"))
        assertNull(ChannelParsers.parseNuxtArray("not json"))
        val arr = ChannelParsers.parseNuxtArray("[1,\"a\",{\"k\":2},[3]]")
        assertEquals(4, arr?.size)
        @Suppress("UNCHECKED_CAST")
        val map = arr!![2] as Map<String, Any?>
        assertEquals(2, map["k"])
    }

    @Test
    fun resolveStringOutOfRangeReturnsNull() {
        val arr = listOf<Any?>("zero")
        assertEquals("zero", ChannelParsers.resolveString(arr, 0))
        assertNull(ChannelParsers.resolveString(arr, 1))
        assertNull(ChannelParsers.resolveString(arr, -1))
        assertNull(ChannelParsers.resolveString(arr, null))
    }

    @Test
    fun parseIsoDateTimeHandlesOffsetFormats() {
        assertEquals(Instant.parse("2026-09-27T03:10:00Z").toEpochMilli(), ChannelParsers.parseIsoDateTime("2026-09-27T06:10:00+03:00"))
        assertEquals(Instant.parse("2026-09-27T03:10:00Z").toEpochMilli(), ChannelParsers.parseIsoDateTime("2026-09-27T06:10:00+0300"))
        assertEquals(Instant.parse("2026-09-27T03:10:00Z").toEpochMilli(), ChannelParsers.parseIsoDateTime("2026-09-27T03:10:00Z"))
        assertNull(ChannelParsers.parseIsoDateTime(""))
        assertNull(ChannelParsers.parseIsoDateTime("not-a-date"))
    }

    @Test
    fun parseNtvDateUsesUtcPlusFive() {
        assertEquals(Instant.parse("2026-09-27T19:00:00Z").toEpochMilli(), ChannelParsers.parseNtvDate("09/28/2026 00:00:00"))
        assertNull(ChannelParsers.parseNtvDate(""))
        assertNull(ChannelParsers.parseNtvDate("28.09.2026"))
    }

    @Test
    fun extractIviInitialStateRespectsNestedBracesAndStrings() {
        val html = "prefix window.__INITIAL_STATE__ = {\"a\":{\"b\":\"} {\"},\"c\":2}; trailing garbage {"
        val json = ChannelParsers.extractIviInitialState(html)
        assertEquals("{\"a\":{\"b\":\"} {\"},\"c\":2}", json)
    }

    @Test
    fun extractIviInitialStateReturnsNullWhenUnbalanced() {
        assertNull(ChannelParsers.extractIviInitialState("window.__INITIAL_STATE__ = {\"a\":{"))
        assertNull(ChannelParsers.extractIviInitialState("no state here"))
    }

    @Test
    fun parsePremierChannelsResolvesIndexReferences() {
        val nuxtJson = """
            [
              {"nuxt-version":"3","tv-channels-list":1},
              [2],
              {"name":3,"slug":4,"logoImage":5,"tvPrograms":6},
              "Первый канал",
              "pervyi",
              "https://uma-static.rtbcdn.ru/pic/cardimage/pe/pervyi.png",
              [7],
              {"title":8,"startTs":9,"endTs":10},
              "Новости",
              "2026-09-27T06:10:00+03:00",
              "2026-09-27T07:00:00+03:00"
            ]
        """.trimIndent()
        val html = "<script id=\"__NUXT_DATA__\">$nuxtJson</script>"
        val channels = ChannelParsers.parsePremierChannels(html)

        assertEquals(1, channels.size)
        val ch = channels[0]
        assertEquals("c1r", ch.id)
        assertEquals("Первый канал", ch.name)
        assertEquals("https://uma-static.rtbcdn.ru/pic/cardimage/pe/pervyi.png", ch.logoUrl)

        val hardcoded = ChannelList.hardcodedChannels.first { it.id == "c1r" }
        assertEquals(hardcoded.streamUrl, ch.streamUrl)
        assertEquals(hardcoded.category, ch.category)
        assertEquals(hardcoded.fallbackStreamUrls, ch.fallbackStreamUrls)

        assertEquals("Новости", ch.currentProgramTitle)
        assertEquals(Instant.parse("2026-09-27T03:10:00Z").toEpochMilli(), ch.currentProgramStart)
        assertEquals(Instant.parse("2026-09-27T04:00:00Z").toEpochMilli(), ch.currentProgramEnd)
    }

    @Test
    fun parsePremierChannelsUnknownSlugKeptAsIsWithoutStream() {
        val nuxtJson = """
            [
              {"tv-channels-list":1},
              [2],
              {"name":3,"slug":4,"logoImage":5},
              "Неизвестный",
              "neizvestny",
              ""
            ]
        """.trimIndent()
        val html = "<script id=\"__NUXT_DATA__\">$nuxtJson</script>"
        val channels = ChannelParsers.parsePremierChannels(html)
        assertEquals(1, channels.size)
        assertEquals("neizvestny", channels[0].id)
        assertEquals("", channels[0].streamUrl)
        assertEquals("general", channels[0].category)
    }

    @Test
    fun parseIviChannelsMapsHruAndBuildsLogoUrl() {
        val html = """
            <html>
            <script>window.__INITIAL_STATE__ = {"state":{"tvChannels":{"tvChannels":{"7":{"title":"НТВ","hru":"NTV","logo":[{"path":"/x/ntv.jpg"}],"current":{"title":"Сегодня","start":"2026-10-27T19:00:00+03:00","end":"2026-10-27T20:00:00+03:00"}}}}}};</script>
            </html>
        """.trimIndent()
        val channels = ChannelParsers.parseIviChannels(html)
        assertEquals(1, channels.size)
        val ch = channels[0]
        assertEquals("ntv", ch.id)
        assertEquals("НТВ", ch.name)
        assertTrue(ch.logoUrl.startsWith("https://s3.dfs.ivi.ru/"))
        assertTrue(ch.logoUrl.endsWith("/x/ntv.jpg"))
        assertEquals("Сегодня", ch.currentProgramTitle)
        assertEquals(Instant.parse("2026-10-27T16:00:00Z").toEpochMilli(), ch.currentProgramStart)
    }

    @Test
    fun parseNtvProgramsMapsCodesAndSkipsInvalid() {
        val json = """
            {
              "data": [
                {"channel":{"code":"1tvch"},"program":{"title":"Время","date_start":"09/28/2026 00:00:00","date_stop":"09/28/2026 01:00:00"}},
                {"channel":{"code":"unknown_code"},"program":{"title":"Что-то","date_start":"09/28/2026 02:00:00","date_stop":"09/28/2026 03:00:00"}},
                {"channel":{"code":"ntv2"},"program":{"title":"Без дат","date_start":"","date_stop":""}},
                {"channel":{},"program":{"title":"Без кода","date_start":"09/28/2026 00:00:00","date_stop":"09/28/2026 01:00:00"}}
              ]
            }
        """.trimIndent()
        val programs = ChannelParsers.parseNtvPrograms(json)
        assertEquals(2, programs.size)
        val timeProgram = programs["c1r"]
        assertNotNull(timeProgram)
        assertEquals("Время", timeProgram?.title)
        assertEquals(Instant.parse("2026-09-27T19:00:00Z").toEpochMilli(), timeProgram?.startMs)
        val unknown = programs["unknown_code"]
        assertNotNull(unknown)
        assertTrue(!programs.containsKey("ntv2"))
    }

    @Test
    fun slugMapsMatchExpectedInternalIds() {
        assertEquals("c1r", ChannelParsers.premierSlugToIdMap["pervyi"])
        assertEquals("subbota", ChannelParsers.premierSlugToIdMap["subbota"])
        assertEquals("otv", ChannelParsers.premierSlugToIdMap["otr"])
        assertEquals("c1r", ChannelParsers.ntvCodeToIdMap["1tvch"])
        assertEquals("c1r", ChannelParsers.iviHruToIdMap["perviy"])
        assertEquals("pz", ChannelParsers.iviHruToIdMap["piatnitsa"])
        assertEquals("c1r", ChannelParsers.smotreshkaNameToIdMap["первый канал"])
    }
}
