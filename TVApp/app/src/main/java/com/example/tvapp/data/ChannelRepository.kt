package com.example.tvapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class ChannelRepository {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val premierUrl = "https://premier.one/tv/categories/besplatnye"

    private var cachedChannels: List<Channel>? = null
    private var lastFetchTime: Long = 0L
    private val cacheDurationMs = 10 * 60 * 1000L

    suspend fun getChannels(forceRefresh: Boolean = false): List<Channel> {
        if (!forceRefresh && cachedChannels != null && System.currentTimeMillis() - lastFetchTime < cacheDurationMs) {
            return cachedChannels!!
        }
        return withContext(Dispatchers.IO) {
            val result: List<Channel> = try {
                val channels = fetchChannelsFromPremier()
                if (channels.isNotEmpty()) {
                    cachedChannels = channels
                    lastFetchTime = System.currentTimeMillis()
                    ChannelList.updateChannels(channels)
                    channels
                } else {
                    ChannelList.channels
                }
            } catch (e: Exception) {
                ChannelList.channels
            }
            result
        }
    }

    suspend fun refreshChannels(): List<Channel> = getChannels(forceRefresh = true)

    private val slugToIdMap = mapOf(
        "pervyi" to "c1r",
        "rossiya_1" to "rossiya1",
        "ntv" to "ntv",
        "pyatyi_kanal" to "5tv",
        "kultura" to "kultura",
        "rossiya_24" to "rossiya24",
        "karusel" to "karusel",
        "tvc" to "tvc",
        "ren_tv" to "ren",
        "spas" to "spas",
        "sts" to "sts",
        "domashniy" to "domashniy",
        "tv3" to "tv3",
        "pyatnica" to "pz",
        "subbota" to "pz",
        "zvezda" to "zvezda",
        "mir" to "mir",
        "tnt" to "tnt",
        "muztv" to "muztv",
        "matchtv" to "match",
        "utr" to "utv",
        "sun" to "sun",
        "otr" to "otv"
    )

    private fun fetchChannelsFromPremier(): List<Channel> {
        val html = httpGet(premierUrl) ?: return emptyList()
        val nuxtData = extractNuxtData(html) ?: return emptyList()
        val arr = parseNuxtArray(nuxtData) ?: return emptyList()

        val hardcodedById = ChannelList.hardcodedChannels.associateBy { it.id.lowercase() }
        val channels = mutableListOf<Channel>()

        val tvChannelsListIdx = findKeyIndex(arr, "tv-channels-list") ?: return emptyList()
        val channelIndices = arr[tvChannelsListIdx] as? List<*> ?: return emptyList()

        for (item in channelIndices) {
            val idx = item as? Number ?: continue
            val chIdx = idx.toInt()
            if (chIdx >= arr.size) continue
            val chObj = arr[chIdx] as? Map<*, *> ?: continue

            val nameRef = chObj["name"] as? Number ?: continue
            val slugRef = chObj["slug"] as? Number ?: continue
            val logoRef = chObj["logoImage"] as? Number ?: continue

            val name = resolveString(arr, nameRef) ?: continue
            val slug = resolveString(arr, slugRef) ?: continue
            val logoUrl = resolveString(arr, logoRef) ?: ""

            val channelId = slugToIdMap[slug.lowercase()] ?: slug
            val hardcoded = hardcodedById[channelId.lowercase()]
            val streamUrl = hardcoded?.streamUrl ?: ""

            var progTitle: String? = null
            var progStart: Long? = null
            var progEnd: Long? = null

            val tvProgramsRef = chObj["tvPrograms"] as? Number
            if (tvProgramsRef != null) {
                val progListIdx = tvProgramsRef.toInt()
                if (progListIdx < arr.size) {
                    val progListVal = arr[progListIdx]
                    val firstProgIdx: Int? = when (progListVal) {
                        is Number -> progListVal.toInt()
                        is List<*> -> (progListVal.firstOrNull() as? Number)?.toInt()
                        else -> null
                    }
                    if (firstProgIdx != null && firstProgIdx < arr.size) {
                        val progObj = arr[firstProgIdx] as? Map<*, *>
                        if (progObj != null) {
                            val titleRef = progObj["title"] as? Number
                            val startRef = progObj["startTs"] as? Number
                            val endRef = progObj["endTs"] as? Number
                            progTitle = resolveString(arr, titleRef)
                            progStart = resolveDateTime(arr, startRef)
                            progEnd = resolveDateTime(arr, endRef)
                        }
                    }
                }
            }

            channels.add(
                Channel(
                    id = channelId,
                    name = name,
                    logoUrl = logoUrl,
                    streamUrl = streamUrl,
                    category = hardcoded?.category ?: "general",
                    fallbackStreamUrls = hardcoded?.fallbackStreamUrls ?: emptyList(),
                    currentProgramTitle = progTitle,
                    currentProgramStart = progStart,
                    currentProgramEnd = progEnd
                )
            )
        }
        return channels
    }

    private fun extractNuxtData(html: String): String? {
        val marker = "id=\"__NUXT_DATA__\""
        val startIdx = html.indexOf(marker)
        if (startIdx < 0) return null
        val contentStart = html.indexOf('>', startIdx) + 1
        val endIdx = html.indexOf("</script>", contentStart)
        if (contentStart <= 0 || endIdx < contentStart) return null
        return html.substring(contentStart, endIdx)
    }

    private fun parseNuxtArray(json: String): List<Any?>? {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("[")) return null
            JSONArray(trimmed).let { arr ->
                val list = mutableListOf<Any?>()
                for (i in 0 until arr.length()) {
                    list.add(jsonValueToKotlin(arr.get(i)))
                }
                list
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonValueToKotlin(value: Any?): Any? = when (value) {
        is JSONObject -> {
            val map = mutableMapOf<String, Any?>()
            val keys = value.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonValueToKotlin(value.get(key))
            }
            map
        }
        is JSONArray -> {
            val list = mutableListOf<Any?>()
            for (i in 0 until value.length()) {
                list.add(jsonValueToKotlin(value.get(i)))
            }
            list
        }
        else -> value
    }

    private fun findKeyIndex(arr: List<Any?>, key: String): Int? {
        for (i in arr.indices) {
            val item = arr[i] as? Map<*, *> ?: continue
            if (item.containsKey(key)) return i
        }
        return null
    }

    private fun resolveString(arr: List<Any?>, ref: Number?): String? {
        if (ref == null) return null
        val idx = ref.toInt()
        if (idx < 0 || idx >= arr.size) return null
        return arr[idx] as? String
    }

    private fun resolveDateTime(arr: List<Any?>, ref: Number?): Long? {
        if (ref == null) return null
        val idx = ref.toInt()
        if (idx < 0 || idx >= arr.size) return null
        val value = arr[idx] ?: return null
        return when (value) {
            is String -> parseIsoDateTime(value)
            is Number -> value.toLong()
            else -> null
        }
    }

    private fun parseIsoDateTime(str: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            sdf.parse(str)?.time
        } catch (e: Exception) {
            try {
                val sdf2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                sdf2.parse(str)?.time
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun httpGet(url: String, authHeader: String? = null): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
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
