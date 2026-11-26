package com.example.tvapp.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ChannelRepository(private val context: Context) {

    companion object {
        private const val DISK_FILE = "channels_cache.json"

        @Volatile
        var contentVersion: Int = 0
            private set

        fun invalidateContent() {
            contentVersion++
        }

        internal fun cacheKeyFor(limehdRegion: Int, tz: Int): String = "limehd_${limehdRegion}_$tz"
    }

    private val prefs by lazy { AppPreferences(context) }
    private val limeHdClient by lazy { LimeHdClient(context) }
    private val diskFile by lazy { File(context.filesDir, DISK_FILE) }

    private var cachedChannels: List<Channel>? = null
    private var cacheKey: String? = null
    private var cachedContentVersion: Int = -1
    private var lastFetchTime: Long = 0L
    private val cacheDurationMs = 10 * 60 * 1000L

    suspend fun getChannels(forceRefresh: Boolean = false): List<Channel> {
        val key = currentCacheKey()
        val versionChanged = cachedContentVersion != contentVersion
        val fresh = !versionChanged && cacheKey == key && System.currentTimeMillis() - lastFetchTime < cacheDurationMs
        if (!forceRefresh && cachedChannels != null && fresh) {
            return cachedChannels!!
        }
        return withContext(Dispatchers.IO) {
            val result: List<Channel> = try {
                val channels = limeHdClient.getChannels()
                Log.d("ChannelRepo", "LimeHD raw channels count: ${channels.size}")
                if (channels.isNotEmpty()) {
                    Log.d("ChannelRepo", "LimeHD: ${channels.size} channels")
                    cachedChannels = channels
                    cacheKey = key
                    cachedContentVersion = contentVersion
                    lastFetchTime = System.currentTimeMillis()
                    saveToDisk(channels)
                    ChannelList.updateChannels(channels)
                    channels
                } else {
                    Log.w("ChannelRepo", "LimeHD returned no channels, keeping previous list")
                    ChannelList.channels
                }
            } catch (e: Exception) {
                Log.e("ChannelRepo", "LimeHD fetch failed: ${e.message}")
                val disk = loadFromDisk()
                if (disk.isNullOrEmpty()) {
                    Log.w("ChannelRepo", "no disk cache, using hardcoded fallback channels")
                    return@withContext hardcodedFallbackChannels(key)
                }
                ChannelList.channels
            }
            result
        }
    }

    /**
     * Жёстко заданные федеральные каналы с проверенными потоками.
     * Используются только как последний шанс, когда LimeHD недоступен и диск-кеша нет.
     */
    private fun hardcodedFallbackChannels(cacheKey: String): List<Channel> = listOf(
        Channel(
            id = "fallback_c1r",
            name = "Первый канал",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina18/index.m3u8",
            category = "federal",
            epgId = "1",
            number = 1
        ),
        Channel(
            id = "fallback_rossiya1",
            name = "Россия 1",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina10/index.m3u8",
            category = "federal",
            epgId = "2",
            number = 2
        ),
        Channel(
            id = "fallback_match",
            name = "Матч ТВ",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina4/index.m3u8",
            category = "sport",
            epgId = "3",
            number = 3
        ),
        Channel(
            id = "fallback_ntv",
            name = "НТВ",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/ntv0_hd/index.m3u8",
            category = "general",
            epgId = "4",
            number = 4
        ),
        Channel(
            id = "fallback_5tv",
            name = "Пятый канал",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina8/index.m3u8",
            category = "general",
            epgId = "5",
            number = 5
        ),
        Channel(
            id = "fallback_kultura",
            name = "Культура",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina12/index.m3u8",
            category = "culture",
            epgId = "6",
            number = 6
        ),
        Channel(
            id = "fallback_zvezda",
            name = "Звезда",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina2/index.m3u8",
            category = "general",
            epgId = "7",
            number = 7
        ),
        Channel(
            id = "fallback_pz",
            name = "Пятница!",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina7/index.m3u8",
            category = "entertainment",
            epgId = "8",
            number = 8
        ),
        Channel(
            id = "fallback_sts",
            name = "СТС",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina14/index.m3u8",
            category = "entertainment",
            epgId = "9",
            number = 9
        ),
        Channel(
            id = "fallback_domashniy",
            name = "Домашний",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina1/index.m3u8",
            category = "entertainment",
            epgId = "10",
            number = 10
        ),
        Channel(
            id = "fallback_tnt",
            name = "ТНТ",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina17/index.m3u8",
            category = "entertainment",
            epgId = "11",
            number = 11
        ),
        Channel(
            id = "fallback_ren",
            name = "РЕН ТВ",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina9/index.m3u8",
            category = "general",
            epgId = "12",
            number = 12
        ),
        Channel(
            id = "fallback_karusel",
            name = "Карусель",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina20/index.m3u8",
            category = "kids",
            epgId = "13",
            number = 13
        ),
        Channel(
            id = "fallback_rossiya24",
            name = "Россия 24",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina11/index.m3u8",
            category = "news",
            epgId = "14",
            number = 14
        ),
        Channel(
            id = "fallback_tvc",
            name = "ТВ Центр",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina15/index.m3u8",
            category = "general",
            epgId = "15",
            number = 15
        ),
        Channel(
            id = "fallback_spas",
            name = "СПАС",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina13/index.m3u8",
            category = "religious",
            epgId = "16",
            number = 16
        ),
        Channel(
            id = "fallback_tv3",
            name = "ТВ-3",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina16/index.m3u8",
            category = "series",
            epgId = "17",
            number = 17
        ),
        Channel(
            id = "fallback_mir",
            name = "МИР",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina3/index.m3u8",
            category = "documentary",
            epgId = "18",
            number = 18
        ),
        Channel(
            id = "fallback_muztv",
            name = "Муз-ТВ",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina5/index.m3u8",
            category = "music",
            epgId = "19",
            number = 19
        ),
        Channel(
            id = "fallback_otv",
            name = "ОТР",
            logoUrl = "",
            streamUrl = "https://cdn.ntv.ru/vitrina6/index.m3u8",
            category = "news",
            epgId = "20",
            number = 20
        )
    ).also { channels ->
        cachedChannels = channels
        this.cacheKey = cacheKey
        cachedContentVersion = contentVersion
        lastFetchTime = System.currentTimeMillis()
        saveToDisk(channels)
        ChannelList.updateChannels(channels)
    }

    suspend fun refreshChannels(): List<Channel> = getChannels(forceRefresh = true)

    /**
     * Возвращает последний сохранённый на диске список каналов (без сети).
     * Нужен, чтобы показать сетку мгновенно при старте, пока идёт фоновое обновление.
     */
    fun loadFromDisk(): List<Channel>? {
        if (!diskFile.exists()) return null
        return try {
            ChannelCacheCodec.deserialize(diskFile.readText())
        } catch (e: Exception) {
            Log.e("ChannelRepo", "loadFromDisk failed: ${e.message}")
            null
        }
    }

    private fun saveToDisk(channels: List<Channel>) {
        try {
            diskFile.writeText(ChannelCacheCodec.serialize(channels))
        } catch (e: Exception) {
            Log.e("ChannelRepo", "saveToDisk failed: ${e.message}")
        }
    }

    /**
     * Возвращает свежий URL потока для канала, минуя 10-минутный кэш.
     * Нужен, чтобы получить свежий (ещё не истёкший) токен LimeHD при зависании буферизации.
     */
    suspend fun refreshStreamUrl(channelId: String): String? = withContext(Dispatchers.IO) {
        try {
            val fresh = limeHdClient.getChannels()
            fresh.find { it.id == channelId }?.streamUrl
        } catch (e: Exception) {
            Log.e("ChannelRepo", "refreshStreamUrl failed: ${e.message}")
            null
        }
    }

    private fun currentCacheKey(): String = cacheKeyFor(prefs.limehdRegion, prefs.programTimezoneOffset())
}

/**
 * Кодирование списка каналов в JSON для диск-кеша и обратно.
 * Вынесено из ChannelRepository: чистая логика без Context, покрывается JVM-тестами.
 */
internal object ChannelCacheCodec {

    fun serialize(channels: List<Channel>): String {
        val arr = JSONArray()
        for (c in channels) {
            val o = JSONObject()
            o.put("id", c.id)
            o.put("name", c.name)
            o.put("logoUrl", c.logoUrl)
            o.put("streamUrl", c.streamUrl)
            o.put("category", c.category)
            o.put("epgId", c.epgId)
            o.put("channelImageUrl", c.channelImageUrl)
            o.put("fallbackStreamUrls", JSONArray(c.fallbackStreamUrls))
            o.put("currentProgramTitle", c.currentProgramTitle)
            o.put("currentProgramStart", c.currentProgramStart)
            o.put("currentProgramEnd", c.currentProgramEnd)
            val progArr = JSONArray()
            for (p in c.programs) {
                val po = JSONObject()
                po.put("channelId", p.channelId)
                po.put("title", p.title)
                po.put("description", p.description)
                po.put("startTime", p.startTime)
                po.put("endTime", p.endTime)
                po.put("iconUrl", p.iconUrl)
                progArr.put(po)
            }
            o.put("programs", progArr)
            o.put("isDrm", c.isDrm)
            o.put("regionCode", c.regionCode)
            o.put("number", c.number)
            o.put("archiveUrl", c.archiveUrl)
            o.put("dayArchive", c.dayArchive)
            arr.put(o)
        }
        return arr.toString()
    }

    fun deserialize(json: String): List<Channel> {
        val arr = JSONArray(json)
        val list = mutableListOf<Channel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val fallbacks = mutableListOf<String>()
            val fa = o.optJSONArray("fallbackStreamUrls")
            if (fa != null) {
                for (j in 0 until fa.length()) fallbacks.add(fa.optString(j))
            }
            val progs = mutableListOf<Program>()
            val pa = o.optJSONArray("programs")
            if (pa != null) {
                for (j in 0 until pa.length()) {
                    val po = pa.optJSONObject(j) ?: continue
                    progs.add(
                        Program(
                            channelId = po.optString("channelId"),
                            title = po.optString("title"),
                            description = po.optString("description").takeIf { it.isNotEmpty() },
                            startTime = po.optLong("startTime"),
                            endTime = po.optLong("endTime"),
                            iconUrl = po.optString("iconUrl").takeIf { it.isNotEmpty() }
                        )
                    )
                }
            }
            list.add(
                Channel(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    logoUrl = o.optString("logoUrl"),
                    streamUrl = o.optString("streamUrl"),
                    category = o.optString("category", "general"),
                    epgId = o.optString("epgId").takeIf { it.isNotEmpty() },
                    channelImageUrl = o.optString("channelImageUrl").takeIf { it.isNotEmpty() },
                    fallbackStreamUrls = fallbacks,
                    currentProgramTitle = o.optString("currentProgramTitle").takeIf { it.isNotEmpty() },
                    currentProgramStart = o.optLong("currentProgramStart").takeIf { it > 0L },
                    currentProgramEnd = o.optLong("currentProgramEnd").takeIf { it > 0L },
                    programs = progs,
                    isDrm = o.optBoolean("isDrm", false),
                    regionCode = o.optInt("regionCode", 0),
                    number = o.optInt("number", 0),
                    archiveUrl = o.optString("archiveUrl"),
                    dayArchive = o.optInt("dayArchive", 0)
                )
            )
        }
        return list
    }
}
