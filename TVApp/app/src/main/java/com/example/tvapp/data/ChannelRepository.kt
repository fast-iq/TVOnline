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
                ChannelList.channels
            }
            result
        }
    }

    suspend fun refreshChannels(): List<Channel> = getChannels(forceRefresh = true)

    /**
     * Возвращает последний сохранённый на диске список каналов (без сети).
     * Нужен, чтобы показать сетку мгновенно при старте, пока идёт фоновое обновление.
     */
    fun loadFromDisk(): List<Channel>? {
        if (!diskFile.exists()) return null
        return try {
            deserialize(diskFile.readText())
        } catch (e: Exception) {
            Log.e("ChannelRepo", "loadFromDisk failed: ${e.message}")
            null
        }
    }

    private fun saveToDisk(channels: List<Channel>) {
        try {
            diskFile.writeText(serialize(channels))
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

    private fun serialize(channels: List<Channel>): String {
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
            arr.put(o)
        }
        return arr.toString()
    }

    private fun deserialize(json: String): List<Channel> {
        val arr = JSONArray(json)
        val list = mutableListOf<Channel>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val fallbacks = mutableListOf<String>()
            val fa = o.optJSONArray("fallbackStreamUrls")
            for (j in 0 until fa.length()) fallbacks.add(fa.optString(j))
            val progs = mutableListOf<Program>()
            val pa = o.optJSONArray("programs")
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
                    number = o.optInt("number", 0)
                )
            )
        }
        return list
    }

    private fun currentCacheKey(): String = cacheKeyFor(prefs.limehdRegion, prefs.programTimezoneOffset())
}
