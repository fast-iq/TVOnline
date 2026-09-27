package com.example.tvapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ChannelRepository {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val epgApiBase = "https://api.epgservice.ru"
    private val epgToken = AppPreferences.EPG_SERVICE_TOKEN

    private var cachedChannels: List<Channel>? = null
    private var channelHrefCache: Map<String, String>? = null

    suspend fun getChannels(): List<Channel> {
        cachedChannels?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val channels = fetchChannelsFromEpgService()
                if (channels.isNotEmpty()) {
                    cachedChannels = channels
                    ChannelList.updateChannels(channels)
                } else {
                    ChannelList.channels
                }
            } catch (e: Exception) {
                ChannelList.channels
            }
        }
    }

    private fun fetchChannelsFromEpgService(): List<Channel> {
        if (epgToken.isBlank()) return emptyList()
        val url = "$epgApiBase/v1/index"
        val xmlStr = httpGet(url, authHeader = "Bearer $epgToken") ?: return emptyList()

        val channels = mutableListOf<Channel>()
        val chRegex = Regex("<channel\\s+id=\"([^\"]+)\"[^>]*>(.*?)</channel>", RegexOption.DOT_MATCHES_ALL)
        for (match in chRegex.findAll(xmlStr)) {
            val body = match.groupValues[2]
            val name = Regex("<display-name[^>]*>([^<]*)</display-name>").find(body)?.groupValues?.get(1)?.trim() ?: ""
            val href = Regex("<href>([^<]+)</href>").find(body)?.groupValues?.get(1)?.trim() ?: ""
            if (name.isBlank()) continue

            channels.add(Channel(
                id = name.lowercase().replace(" ", "_"),
                name = name,
                logoUrl = "",
                streamUrl = href.replace("http://", "https://"),
                category = "general"
            ))
        }
        return channels
    }

    private fun httpGet(url: String, authHeader: String? = null): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            if (authHeader != null) {
                connection.setRequestProperty("Authorization", authHeader)
            }
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            val code = connection.responseCode
            if (code != 200) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
