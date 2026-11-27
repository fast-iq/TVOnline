package com.example.tvapp.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Оркестрация клиента LimeHD (парсинг тел ответов покрыт LimeHdParserTest):
 * последовательность auth -> playlist, форма запроса, фолбэки при ошибках,
 * кэш регионов. Транспорт подменён заглушкой — сеть не трогается.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LimeHdClientFlowTest {

    private class FakeClient(context: Context) : LimeHdClient(context) {
        val getResponses = mutableMapOf<String, String?>()
        var postResponse: String? = null
        val getUrls = mutableListOf<String>()
        val postUrls = mutableListOf<String>()
        val postBodies = mutableListOf<String>()

        override fun httpGet(url: String, headers: Map<String, String>, updateTimeOffset: Boolean): String? {
            getUrls += url
            return getResponses.entries.firstOrNull { url.contains(it.key) }?.value
        }

        override fun httpPostForm(url: String, body: String): String? {
            postUrls += url
            postBodies += body
            return postResponse
        }
    }

    private lateinit var client: FakeClient

    @Before
    fun setUp() {
        client = FakeClient(RuntimeEnvironment.getApplication())
    }

    private val playlistJson = """
        {
          "channels": [
            {
              "number": 1, "our_id": 45, "name_ru": "Первый", "available": true,
              "stream": {"common": "https://a/1.m3u8"}
            },
            {
              "number": 2, "our_id": 123, "name_ru": "Матч", "available": true,
              "url": "https://b/2.m3u8"
            },
            {"number": 3, "our_id": 7, "title": "Выкл", "available": false, "stream": {"common": "https://x"}}
          ]
        }
    """.trimIndent()

    @Test
    fun getChannelsAuthsThenPostsPlaylistAndParsesChannels() {
        client.getResponses["auth"] = null // сервер аутентификации недоступен -> фолбэк-ключ
        client.postResponse = playlistJson

        val channels = client.getChannels()

        assertEquals(2, channels.size)
        assertEquals("limehd_45", channels[0].id)
        assertEquals("Первый", channels[0].name)
        assertEquals("limehd_123", channels[1].id)
        // Сначала GET /auth, затем POST /api/v4/playlist.
        assertTrue("auth не вызван: ${client.getUrls}", client.getUrls.any { it.contains("auth") })
        assertEquals(1, client.postUrls.size)
        assertTrue(client.postUrls[0].contains("api/v4/playlist"))
        assertTrue(client.postUrls[0].contains("region="))
        assertTrue(client.postUrls[0].contains("tz="))
        assertTrue("тело не в форме payload=", client.postBodies[0].startsWith("payload="))
    }

    @Test
    fun getChannelsReturnsEmptyWhenPlaylistFails() {
        client.getResponses["auth"] = "{}"
        client.postResponse = null

        assertEquals(emptyList<Channel>(), client.getChannels())
    }

    @Test
    fun getChannelsReturnsEmptyWhenPlaylistHasNoChannelsKey() {
        client.getResponses["auth"] = "{}"
        client.postResponse = """{"error":"quota"}"""

        assertEquals(emptyList<Channel>(), client.getChannels())
    }

    @Test
    fun getChannelsReturnsEmptyWhenServerIssuesGarbageKey() {
        // Кривой ключ с сервера ломает шифрование payload — падать нельзя, только пустой список.
        client.getResponses["auth"] = """{"public_key":"это не base64 !!!"}"""
        client.postResponse = playlistJson

        assertEquals(emptyList<Channel>(), client.getChannels())
    }

    @Test
    fun fetchEpgRequestsByIdAndDayWindowAndParsesPrograms() {
        client.getResponses["epg?id=42"] = """
            {"epg": [{"data": [
              {"title": "Утро", "timestart": 100, "timestop": 200},
              {"title": "Вечер", "timestart": 300, "timestop": 400}
            ]}]}
        """.trimIndent()

        val programs = client.fetchEpg(epgId = "42", channelId = "limehd_45", epgFrom = 2, epgLimit = 3)

        assertEquals(listOf("Утро", "Вечер"), programs.map { it.title })
        val url = client.getUrls.single()
        assertTrue(url.contains("api/v4/epg"))
        assertTrue(url.contains("id=42"))
        assertTrue(url.contains("epg_from=2"))
        assertTrue(url.contains("epg_limit=3"))
    }

    @Test
    fun fetchEpgReturnsEmptyOnNetworkError() {
        client.getResponses["epg"] = null

        assertEquals(emptyList<Program>(), client.fetchEpg("42", "limehd_45"))
    }

    @Test
    fun getRegionsParsesConfigSortsAndExposesAutoRegion() {
        client.getResponses["config"] = """
            {"data": {
              "regions": [
                {"code": 77, "name": "Москва", "tz": [3]},
                {"code": 1, "name": "Адыгея"},
                {"code": 0, "name": "Без кода"}
              ],
              "region": {"code": 77}
            }}
        """.trimIndent()

        val regions = client.getRegions()

        assertEquals(listOf("Адыгея", "Москва"), regions.map { it.name })
        assertEquals(listOf(3), regions.last().tzOffsets)
        assertEquals(77, client.autoRegionCode())
    }

    @Test
    fun getRegionsCachesSuccessfulFetch() {
        client.getResponses["config"] = """{"regions":[{"code":5,"name":"Петербург"}]}"""

        assertEquals(1, client.getRegions().size)
        assertEquals(1, client.getRegions().size)
        assertEquals(1, client.getUrls.count { it.contains("config") })
    }

    @Test
    fun getRegionsReturnsEmptyAndNoAutoCodeOnGarbage() {
        client.getResponses["config"] = "не json"

        assertTrue(client.getRegions().isEmpty())
        assertNull(client.autoRegionCode())
    }
}
