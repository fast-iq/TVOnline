package com.example.tvapp.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class ChannelRepository(private val context: Context) {

    private val prefs by lazy { AppPreferences(context) }
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private var cachedChannels: List<Channel>? = null
    private var lastFetchTime: Long = 0L
    private val cacheDurationMs = 10 * 60 * 1000L

    suspend fun getChannels(forceRefresh: Boolean = false): List<Channel> {
        if (!forceRefresh && cachedChannels != null && System.currentTimeMillis() - lastFetchTime < cacheDurationMs) {
            return cachedChannels!!
        }
        return withContext(Dispatchers.IO) {
            val result: List<Channel> = try {
                val channels = fetchFromPreferredSource()
                if (channels.isNotEmpty()) {
                    cachedChannels = channels
                    lastFetchTime = System.currentTimeMillis()
                    ChannelList.updateChannels(channels)
                    channels
                } else {
                    ChannelList.channels
                }
            } catch (e: Exception) {
                Log.e("ChannelRepo", "All sources failed: ${e.message}")
                ChannelList.channels
            }
            result
        }
    }

    suspend fun refreshChannels(): List<Channel> = getChannels(forceRefresh = true)

    private fun fetchFromPreferredSource(): List<Channel> {
        val preferred = prefs.contentSource
        Log.d("ChannelRepo", "Preferred source: $preferred")

        when (preferred) {
            AppPreferences.ContentSource.PREMIER -> {
                val result = tryFetchPremier()
                if (result.isNotEmpty()) return result
                Log.w("ChannelRepo", "Premier failed, trying IVI")
                val iviResult = tryFetchIvi()
                if (iviResult.isNotEmpty()) return iviResult
                Log.w("ChannelRepo", "IVI failed, trying Smotreshka")
                val smotreshkaResult = tryFetchSmotreshka()
                if (smotreshkaResult.isNotEmpty()) return smotreshkaResult
            }
            AppPreferences.ContentSource.IVI -> {
                val result = tryFetchIvi()
                if (result.isNotEmpty()) return result
                Log.w("ChannelRepo", "IVI failed, trying Premier")
                val premierResult = tryFetchPremier()
                if (premierResult.isNotEmpty()) return premierResult
                Log.w("ChannelRepo", "Premier failed, trying Smotreshka")
                val smotreshkaResult = tryFetchSmotreshka()
                if (smotreshkaResult.isNotEmpty()) return smotreshkaResult
            }
            AppPreferences.ContentSource.SMOTRESHKA -> {
                val result = tryFetchSmotreshka()
                if (result.isNotEmpty()) return result
                Log.w("ChannelRepo", "Smotreshka failed, trying IVI")
                val iviResult = tryFetchIvi()
                if (iviResult.isNotEmpty()) return iviResult
                Log.w("ChannelRepo", "IVI failed, trying Premier")
                val premierResult = tryFetchPremier()
                if (premierResult.isNotEmpty()) return premierResult
            }
        }
        return emptyList()
    }

    // ========== PREMIER.ONE ==========

    private fun tryFetchPremier(): List<Channel> {
        return try {
            val channels = fetchChannelsFromPremier()
            Log.d("ChannelRepo", "Premier: ${channels.size} channels")
            channels
        } catch (e: Exception) {
            Log.e("ChannelRepo", "Premier error: ${e.message}")
            emptyList()
        }
    }

    private val premierSlugToIdMap = mapOf(
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
        val html = httpGet("https://premier.one/tv/categories/besplatnye") ?: return emptyList()
        val nuxtData = extractNuxtData(html) ?: run {
            Log.w("ChannelRepo", "Premier: extractNuxtData failed, html length=${html.length}")
            return emptyList()
        }
        val arr = parseNuxtArray(nuxtData) ?: run {
            Log.w("ChannelRepo", "Premier: parseNuxtArray failed")
            return emptyList()
        }

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

            val channelId = premierSlugToIdMap[slug.lowercase()] ?: slug
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

    // ========== IVI.RU ==========

    private fun tryFetchIvi(): List<Channel> {
        return try {
            val channels = fetchChannelsFromIvi()
            Log.d("ChannelRepo", "IVI: ${channels.size} channels")
            channels
        } catch (e: Exception) {
            Log.e("ChannelRepo", "IVI error: ${e.message}")
            emptyList()
        }
    }

    private val iviHruToIdMap = mapOf(
        "russia1" to "rossiya1",
        "5tv" to "5tv",
        "russia24" to "rossiya24",
        "sts" to "sts",
        "domashniy" to "domashniy",
        "ntv" to "ntv",
        "perviy" to "c1r",
        "kultura" to "kultura",
        "zvezda" to "zvezda",
        "piatnitsa" to "pz",
        "tnt" to "tnt",
        "ren" to "ren",
        "karusel" to "karusel",
        "matchtv" to "match",
        "tvc" to "tvc",
        "spas" to "spas",
        "tv3" to "tv3",
        "mir" to "mir",
        "muztv" to "muztv"
    )

    private fun fetchChannelsFromIvi(): List<Channel> {
        val html = httpGet("https://www.ivi.ru/tvplus/tvchannels/federalnye-kanaly") ?: return emptyList()
        val stateJson = extractIviInitialState(html) ?: run {
            Log.w("ChannelRepo", "IVI: extractIviInitialState failed, html length=${html.length}")
            return emptyList()
        }

        val root = JSONObject(stateJson)
        val tvChannelsObj = root.optJSONObject("state")
            ?.optJSONObject("tvChannels")
            ?.optJSONObject("tvChannels") ?: run {
            Log.w("ChannelRepo", "IVI: state.tvChannels.tvChannels not found, keys=${root.keys()}")
            return emptyList()
        }

        val hardcodedById = ChannelList.hardcodedChannels.associateBy { it.id.lowercase() }
        val channels = mutableListOf<Channel>()
        val keys = tvChannelsObj.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val chObj = tvChannelsObj.optJSONObject(key) ?: continue
            val title = chObj.optString("title", "")
            if (title.isEmpty()) continue
            val hru = chObj.optString("hru", "").lowercase()

            val logoUrl = chObj.optJSONArray("logo")
                ?.optJSONObject(0)
                ?.optString("path", "") ?: ""
            val fullLogo = if (logoUrl.startsWith("http")) logoUrl else "https://s3.dfs.ivi.ru/f3d320408efc5ab66630b9ffc6c6cf2b/files_tv_channel_logo_rendered/${logoUrl.removePrefix("/")}"

            val channelId = iviHruToIdMap[hru] ?: hru
            val hardcoded = hardcodedById[channelId.lowercase()]
            val streamUrl = hardcoded?.streamUrl ?: ""

            var progTitle: String? = null
            var progStart: Long? = null
            var progEnd: Long? = null

            val currentObj = chObj.optJSONObject("current")
            if (currentObj != null) {
                progTitle = currentObj.optString("title", "").takeIf { it.isNotEmpty() }
                progStart = parseIsoDateTime(currentObj.optString("start", ""))
                progEnd = parseIsoDateTime(currentObj.optString("end", ""))
            }

            channels.add(
                Channel(
                    id = channelId,
                    name = title,
                    logoUrl = fullLogo,
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

    private fun extractIviInitialState(html: String): String? {
        val marker = "window.__INITIAL_STATE__"
        val startIdx = html.indexOf(marker)
        if (startIdx < 0) return null
        val jsonStart = html.indexOf('{', startIdx)
        if (jsonStart < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in jsonStart until html.length) {
            val c = html[i]
            if (escaped) { escaped = false; continue }
            when {
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return html.substring(jsonStart, i + 1)
                }
            }
        }
        return null
    }

    // ========== SMOTRESHKA.TV ==========

    private fun tryFetchSmotreshka(): List<Channel> {
        return try {
            val channels = fetchChannelsFromSmotreshka()
            Log.d("ChannelRepo", "Smotreshka: ${channels.size} channels")
            channels
        } catch (e: Exception) {
            Log.e("ChannelRepo", "Smotreshka error: ${e.message}")
            emptyList()
        }
    }

    private val smotreshkaNameToIdMap = mapOf(
        "первый канал" to "c1r",
        "россия 1" to "rossiya1",
        "нтв" to "ntv",
        "пятый канал" to "5tv",
        "культура" to "kultura",
        "звезда" to "zvezda",
        "пятница!" to "pz",
        "стс" to "sts",
        "домашний" to "domashniy",
        "тнт" to "tnt",
        "рен тв" to "ren",
        "карусель" to "karusel",
        "матч тв" to "match",
        "россия 24" to "rossiya24",
        "тв центр" to "tvc",
        "спас" to "spas",
        "тв-3" to "tv3",
        "мир" to "mir",
        "муз-тв" to "muztv"
    )

    private fun fetchChannelsFromSmotreshka(): List<Channel> {
        val token = httpGet("https://fe.smotreshka.tv/user/v1/asset-tokens") ?: return emptyList()
        val tokenJson = JSONObject(token)
        val tvAssetToken = tokenJson.optString("tvAssetToken", "")
        if (tvAssetToken.isEmpty()) return emptyList()

        val channelsResp = httpGet("https://fe.smotreshka.tv/channels?languageCode=ru") ?: return emptyList()
        val channelsObj = JSONObject(channelsResp)
        val channelsArr = channelsObj.optJSONArray("channels") ?: return emptyList()

        val mediasResp = httpGet("https://fe.smotreshka.tv/tv/v2/medias?tv-asset-token=$tvAssetToken") ?: return emptyList()
        val mediasObj = JSONObject(mediasResp)
        val mediasArr = mediasObj.optJSONArray("medias") ?: return emptyList()

        val onAirResp = httpGet(
            "https://fe.smotreshka.tv/epg/v2/on-air?languageCode=ru&tv-asset-token=$tvAssetToken" +
                "&appVersion=1.0.0&platform=android&osVersion=35"
        ) ?: return emptyList()
        val onAirObj = JSONObject(onAirResp)
        val schedulesArr = onAirObj.optJSONArray("schedules") ?: return emptyList()

        val scheduleIdToEvents = mutableMapOf<String, List<JSONObject>>()
        for (i in 0 until schedulesArr.length()) {
            val schedObj = schedulesArr.optJSONObject(i) ?: continue
            val schedId = schedObj.optString("scheduleId", "")
            val events = mutableListOf<JSONObject>()
            val eventsArr = schedObj.optJSONArray("events")
            if (eventsArr != null) {
                for (j in 0 until eventsArr.length()) {
                    val ev = eventsArr.optJSONObject(j) ?: continue
                    events.add(ev)
                }
            }
            if (events.isNotEmpty()) scheduleIdToEvents[schedId] = events
        }

        val mediaByChannel = mutableMapOf<String, JSONObject>()
        for (i in 0 until mediasArr.length()) {
            val media = mediasArr.optJSONObject(i) ?: continue
            val chId = media.optString("channelId", "")
            if (chId.isNotEmpty() && !mediaByChannel.containsKey(chId)) {
                mediaByChannel[chId] = media
            }
        }

        val hardcodedById = ChannelList.hardcodedChannels.associateBy { it.id.lowercase() }
        val channels = mutableListOf<Channel>()

        for (i in 0 until channelsArr.length()) {
            val chObj = channelsArr.optJSONObject(i) ?: continue
            val chId = chObj.optString("id", "")
            val info = chObj.optJSONObject("info") ?: continue
            val metaInfo = info.optJSONObject("metaInfo") ?: continue
            var title = metaInfo.optString("title", "")

            val prefixPattern = Regex("^\\d+_")
            if (prefixPattern.containsMatchIn(title)) {
                title = title.replace(prefixPattern, "")
            }
            if (title.isEmpty()) continue

            val logoUrl = info.optJSONObject("mediaInfo")
                ?.optJSONArray("thumbnails")
                ?.optJSONObject(0)
                ?.optString("url", "") ?: ""

            val media = mediaByChannel[chId]
            val scheduleId = media?.optString("scheduleId", "") ?: ""
            val events = scheduleIdToEvents[scheduleId]

            var progTitle: String? = null
            var progStart: Long? = null
            var progEnd: Long? = null

            if (events != null && events.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val currentEvent = events.firstOrNull { ev ->
                    val sf = ev.optJSONObject("scheduledFor") ?: return@firstOrNull false
                    val begin = parseIsoDateTime(sf.optString("begin", ""))
                    val end = parseIsoDateTime(sf.optString("end", ""))
                    begin != null && end != null && now in begin..end
                }
                if (currentEvent != null) {
                    progTitle = currentEvent.optString("title", "").takeIf { it.isNotEmpty() }
                    val sf = currentEvent.optJSONObject("scheduledFor")
                    progStart = parseIsoDateTime(sf?.optString("begin", "") ?: "")
                    progEnd = parseIsoDateTime(sf?.optString("end", "") ?: "")
                }
            }

            val normalizedTitle = title.lowercase().trim()
            val channelId = smotreshkaNameToIdMap[normalizedTitle] ?: chId
            val hardcoded = hardcodedById[channelId.lowercase()]
            val streamUrl = hardcoded?.streamUrl ?: ""

            channels.add(
                Channel(
                    id = channelId,
                    name = title,
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

    // ========== SHARED UTILS ==========

    private fun parseIsoDateTime(str: String): Long? {
        if (str.isEmpty()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            sdf.parse(str)?.time
        } catch (e: Exception) {
            try {
                val sdf2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
                sdf2.parse(str)?.time
            } catch (e2: Exception) {
                try {
                    val sdf3 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                    sdf3.parse(str)?.time
                } catch (e3: Exception) {
                    null
                }
            }
        }
    }

    private fun httpGet(url: String, authHeader: String? = null): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json, text/html,application/xhtml+xml")
            if (authHeader != null) {
                connection.setRequestProperty("Authorization", authHeader)
            }
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            val code = connection.responseCode
            if (code != 200) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("ChannelRepo", "httpGet failed for $url: ${e.message}")
            null
        } finally {
            connection.disconnect()
        }
    }
}
