package com.example.tvapp.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EPGRepository(context: Context? = null) {

    companion object {
        const val EPG_PAST_FROM = 0
        const val EPG_PAST_LIMIT = 1
        const val EPG_FUTURE_FROM = 1
        const val EPG_FUTURE_LIMIT = 1
        private const val PARALLEL_FETCH_LIMIT = 6
        private const val TAG = "EPGRepository"
        private const val EPG_DISK_FILE = "epg_cache.json"
        private const val EPG_DISK_MAX_AGE_MS = 6 * 60 * 60 * 1000L
        private const val EPG_DISK_KEEP_PAST_MS = 6 * 60 * 60 * 1000L
    }

    private val prefs = context?.let { AppPreferences(it) }
    private val limeHdClient by lazy { context?.let { LimeHdClient(it) } }
    private val appContext = context?.applicationContext

    private val limehdEpgCache: MutableMap<String, List<Program>> = Collections.synchronizedMap(LinkedHashMap())
    private var cachedEpgVersion: Int = -1

    private val diskFile by lazy { appContext?.let { File(it.filesDir, EPG_DISK_FILE) } }
    private var diskEntries: MutableMap<String, List<Program>>? = null
    private var diskDirty = false

    suspend fun getProgramForChannel(channelId: String, date: Date): List<Program> = withContext(Dispatchers.IO) {
        try {
            val limehd = fetchLimeHdSchedule(channelId, date)
            if (limehd != null && limehd.isNotEmpty()) {
                flushDiskCache()
                return@withContext limehd
            }
            generateFallbackEPG(channelId, date)
        } catch (e: Exception) {
            generateFallbackEPG(channelId, date)
        }
    }

    suspend fun getProgramsForAllChannels(channels: List<Channel>, date: Date): Map<String, List<Program>> = withContext(Dispatchers.IO) {
        val programs = mutableMapOf<String, List<Program>>()
        for (channel in channels) {
            try {
                val channelPrograms = getProgramForChannel(channel.id, date)
                programs[channel.id] = channelPrograms
            } catch (e: Exception) {
                programs[channel.id] = generateFallbackEPG(channel.id, date)
            }
        }
        programs
    }

    suspend fun getProgramsWithSurroundings(
        channels: List<Channel>,
        date: Date,
        onStage: (Map<String, List<Program>>) -> Unit = {}
    ): Map<String, List<Program>> = coroutineScope {
        val result = mutableMapOf<String, List<Program>>()
        for (chunk in channels.chunked(PARALLEL_FETCH_LIMIT)) {
            val part = chunk.map { channel ->
                async(Dispatchers.IO) {
                    Pair(channel.id, fetchWideSchedule(channel, date, EPG_PAST_FROM, EPG_PAST_LIMIT))
                }
            }.awaitAll()
            for ((id, programs) in part) result[id] = programs
            withContext(Dispatchers.Main) { onStage(result.toMap()) }
        }
        flushDiskCache()
        for (chunk in channels.chunked(PARALLEL_FETCH_LIMIT)) {
            val part = chunk.map { channel ->
                async(Dispatchers.IO) {
                    val future = fetchWideSchedule(channel, date, EPG_FUTURE_FROM, EPG_FUTURE_LIMIT)
                    val merged = (result[channel.id].orEmpty() + future)
                        .distinctBy { Triple(it.startTime, it.endTime, it.title) }
                        .sortedBy { it.startTime }
                    Pair(channel.id, merged)
                }
            }.awaitAll()
            for ((id, programs) in part) result[id] = programs
            withContext(Dispatchers.Main) { onStage(result.toMap()) }
        }
        flushDiskCache()
        for (channel in channels) {
            if (result[channel.id].isNullOrEmpty()) {
                result[channel.id] = generateFallbackEPG(channel.id, date)
            }
        }
        result
    }

    private fun fetchWideSchedule(channel: Channel, date: Date, epgFrom: Int, epgLimit: Int): List<Program> {
        if (!channel.id.startsWith("limehd_")) return emptyList()
        val limehdId = channel.epgId ?: return emptyList()
        val dayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(date)
        val cacheKey = "${channel.id}_${prefs?.limehdRegion ?: 0}_${prefs?.programTimezoneOffset() ?: 0}_${epgFrom}_${epgLimit}_$dayKey"
        ensureEpgCacheFresh()
        limehdEpgCache[cacheKey]?.let { return it }
        cachedOnDisk(cacheKey)?.let {
            limehdEpgCache[cacheKey] = it
            return it
        }
        val client = limeHdClient ?: return emptyList()
        val programs = try {
            client.fetchEpg(limehdId, channel.id, epgFrom, epgLimit)
        } catch (e: Exception) {
            emptyList()
        }
        if (programs.isEmpty()) return emptyList()
        limehdEpgCache[cacheKey] = programs
        rememberOnDisk(cacheKey, programs)
        return programs
    }

    private fun fetchLimeHdSchedule(channelId: String, date: Date): List<Program>? {
        val channel = ChannelList.channels.find { it.id == channelId } ?: return null
        if (!channel.id.startsWith("limehd_")) return null
        if (channel.programs.isNotEmpty()) return channel.programs
        val limehdId = channel.epgId ?: return null
        val dayKey = SimpleDateFormat("yyyyMMdd", Locale.US).format(date)
        val cacheKey = "${channel.id}_${prefs?.limehdRegion ?: 0}_${prefs?.programTimezoneOffset() ?: 0}_$dayKey"
        ensureEpgCacheFresh()
        limehdEpgCache[cacheKey]?.let { return it }
        cachedOnDisk(cacheKey)?.let {
            limehdEpgCache[cacheKey] = it
            return it
        }

        val client = limeHdClient ?: return null
        val programs = client.fetchEpg(limehdId, channel.id)
        if (programs.isEmpty()) return null
        limehdEpgCache[cacheKey] = programs
        rememberOnDisk(cacheKey, programs)
        return programs
    }

    private fun ensureEpgCacheFresh() {
        if (cachedEpgVersion != ChannelRepository.contentVersion) {
            limehdEpgCache.clear()
            cachedEpgVersion = ChannelRepository.contentVersion
        }
    }

    private fun cachedOnDisk(cacheKey: String): List<Program>? = diskCache()[cacheKey]

    private fun rememberOnDisk(cacheKey: String, programs: List<Program>) {
        if (appContext == null) return
        diskCache()[cacheKey] = programs
        diskDirty = true
    }

    /** Записывает кеш на диск. Вызывается по окончании стадии загрузки, а не на каждый канал. */
    fun flushDiskCache() {
        val file = diskFile ?: return
        if (!diskDirty) return
        val snapshot = synchronized(diskCache()) {
            val now = System.currentTimeMillis()
            diskCache().filterValues { programs -> programs.any { it.endTime > now - EPG_DISK_KEEP_PAST_MS } }
        }
        diskDirty = false
        try {
            file.writeText(EpgDiskCodec.encode(System.currentTimeMillis(), snapshot))
        } catch (e: Exception) {
            Log.e(TAG, "EPG disk cache write failed: ${e.message}")
        }
    }

    private fun diskCache(): MutableMap<String, List<Program>> {
        diskEntries?.let { return it }
        val loaded: MutableMap<String, List<Program>> = Collections.synchronizedMap(LinkedHashMap())
        try {
            val file = diskFile
            if (file != null && file.exists()) {
                loaded.putAll(EpgDiskCodec.decode(file.readText(), System.currentTimeMillis(), EPG_DISK_MAX_AGE_MS))
                Log.d(TAG, "EPG disk cache loaded ${loaded.size} entries")
            }
        } catch (e: Exception) {
            Log.e(TAG, "EPG disk cache read failed: ${e.message}")
        }
        diskEntries = loaded
        return loaded
    }

    private fun generateFallbackEPG(channelId: String, baseDate: Date): List<Program> = EpgParser.generateFallbackEPG(channelId, baseDate)

    fun getCurrentProgram(programs: List<Program>, currentTime: Long = System.currentTimeMillis()): Program? = programs.find { it.isLive(currentTime) }

    fun getNextProgram(programs: List<Program>, currentTime: Long = System.currentTimeMillis()): Program? = programs.filter { it.startTime > currentTime }.minByOrNull { it.startTime }
}

internal object EpgDiskCodec {

    fun encode(savedAt: Long, entries: Map<String, List<Program>>): String {
        val root = JSONObject()
        root.put("saved_at", savedAt)
        val obj = JSONObject()
        for ((key, programs) in entries) {
            val arr = JSONArray()
            for (p in programs) {
                val po = JSONObject()
                po.put("channelId", p.channelId)
                po.put("title", p.title)
                po.put("description", p.description)
                po.put("startTime", p.startTime)
                po.put("endTime", p.endTime)
                po.put("iconUrl", p.iconUrl)
                arr.put(po)
            }
            obj.put(key, arr)
        }
        root.put("entries", obj)
        return root.toString()
    }

    fun decode(json: String, nowMs: Long, maxAgeMs: Long): Map<String, List<Program>> {
        val root = JSONObject(json)
        val savedAt = root.optLong("saved_at", 0L)
        if (savedAt <= 0L || nowMs - savedAt > maxAgeMs) return emptyMap()
        val obj = root.optJSONObject("entries") ?: return emptyMap()
        val result = LinkedHashMap<String, List<Program>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val arr = obj.optJSONArray(key) ?: continue
            val programs = mutableListOf<Program>()
            for (i in 0 until arr.length()) {
                val po = arr.optJSONObject(i) ?: continue
                val title = po.optString("title")
                if (title.isEmpty()) continue
                programs.add(
                    Program(
                        channelId = po.optString("channelId"),
                        title = title,
                        description = po.optString("description").takeIf { it.isNotEmpty() },
                        startTime = po.optLong("startTime"),
                        endTime = po.optLong("endTime"),
                        iconUrl = po.optString("iconUrl").takeIf { it.isNotEmpty() }
                    )
                )
            }
            if (programs.isNotEmpty()) result[key] = programs.sortedBy { it.startTime }
        }
        return result
    }
}

internal object EpgParser {

    fun generateFallbackEPG(channelId: String, baseDate: Date): List<Program> {
        val channel = ChannelList.channels.find { it.id == channelId } ?: return emptyList()
        val moscowTz = TimeZone.getTimeZone("Europe/Moscow")
        val calendar = Calendar.getInstance(moscowTz).apply {
            time = baseDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val templates = getChannelTemplates(channelId)
        val programs = mutableListOf<Program>()
        var currentTime = calendar.timeInMillis
        val endLimit = currentTime + 48L * 60L * 60L * 1000L

        var index = 0
        while (currentTime < endLimit) {
            val durationMs = templates[index % templates.size].second * 60L * 1000L
            val startTime = currentTime
            val endTime = startTime + durationMs
            programs.add(
                Program(
                    channelId = channelId,
                    title = templates[index % templates.size].first,
                    description = null,
                    startTime = startTime,
                    endTime = endTime,
                    iconUrl = null
                )
            )
            currentTime = endTime
            index++
        }
        return programs
    }

    fun getChannelTemplates(channelId: String): List<Pair<String, Int>> {
        val channel = ChannelList.channels.find { it.id == channelId }
        return when (channel?.category) {
            "news" -> listOf(
                "Утренние новости" to 60,
                "Новости" to 30,
                "Специальный репортаж" to 45,
                "Новости" to 30,
                "Аналитическая программа" to 60,
                "Новости" to 30,
                "Вечерние новости" to 60,
                "Общественно-политическое шоу" to 90,
                "Новости" to 30,
                "Ночной эфир" to 120
            )
            "entertainment" -> listOf(
                "Утреннее шоу" to 120,
                "Ток-шоу" to 60,
                "Сериал" to 90,
                "Новости" to 30,
                "Развлекательное шоу" to 60,
                "Сериал" to 90,
                "Вечернее шоу" to 120,
                "Премьера" to 60,
                "Поздний фильм" to 120
            )
            "movies" -> listOf(
                "Утренний фильм" to 100,
                "Сериал" to 90,
                "Детектив" to 100,
                "Новости кино" to 30,
                "Мелодрама" to 110,
                "Боевик" to 120,
                "Вечерний фильм" to 120,
                "Премьера" to 110,
                "Ночной фильм" to 130
            )
            "kids" -> listOf(
                "Утренние мультфильмы" to 60,
                "Образовательная программа" to 30,
                "Мультсериал" to 45,
                "Игровая программа" to 30,
                "Мультфильм" to 60,
                "Детское шоу" to 45,
                "Мультсериал" to 60,
                "Вечерний мультфильм" to 90
            )
            "sport" -> listOf(
                "Утренняя аэробика" to 30,
                "Спортивный обзор" to 60,
                "Трансляция матча" to 120,
                "Спортивная аналитика" to 60,
                "Трансляция матча" to 120,
                "Новости спорта" to 30,
                "Вечерний спортивный обзор" to 90,
                "Документальный о спорте" to 60
            )
            "music" -> listOf(
                "Утренние хиты" to 120,
                "Музыкальный чарт" to 60,
                "Клипы и премьеры" to 90,
                "Концерт" to 120,
                "Музыкальное шоу" to 90,
                "Вечерние хиты" to 120,
                "Ночной микс" to 180
            )
            "culture" -> listOf(
                "Утренняя культурная программа" to 60,
                "Телевизионный театр" to 90,
                "Документальный фильм" to 60,
                "Культурные новости" to 30,
                "Концерт" to 90,
                "Вечерний спектакль" to 120,
                "Ночной кинопоказ" to 100
            )
            "nature" -> listOf(
                "Утренние дикие животные" to 60,
                "Документальный о природе" to 90,
                "Путешествия" to 60,
                "Новости науки" to 30,
                "Дикая природа" to 90,
                "Вечерний документальный" to 120,
                "Ночной эфир" to 120
            )
            "documentary" -> listOf(
                "Утренний документальный" to 60,
                "Историческая программа" to 90,
                "Научный фильм" to 60,
                "Репортаж" to 45,
                "Великие открытия" to 90,
                "Вечерний документальный" to 120,
                "Ночной эфир" to 120
            )
            "lifestyle" -> listOf(
                "Утренние советы" to 60,
                "Кулинарное шоу" to 45,
                "Дизайн интерьера" to 30,
                "Здоровый образ жизни" to 45,
                "Путешествия" to 60,
                "Вечернее шоу" to 90,
                "Ночной эфир" to 120
            )
            "hunting_fishing" -> listOf(
                "Утренняя рыбалка" to 60,
                "Охота и природа" to 90,
                "Мастер-класс" to 45,
                "Рыболовный репортаж" to 60,
                "Охотничьи истории" to 90,
                "Вечерний эфир" to 120,
                "Ночной эфир" to 120
            )
            "relax" -> listOf(
                "Утренние медитации" to 60,
                "Релакс-музыка" to 90,
                "Йога и здоровье" to 45,
                "Природа в движении" to 60,
                "Спокойный вечер" to 120,
                "Ночной релакс" to 180
            )
            "series" -> listOf(
                "Утренний сериал" to 90,
                "Детективный сериал" to 90,
                "Ситком" to 30,
                "Мелодрама" to 90,
                "Криминальный сериал" to 90,
                "Вечерний сериал" to 120,
                "Ночной эфир" to 120
            )
            "regional" -> listOf(
                "Утренние новости региона" to 60,
                "Общественная программа" to 45,
                "Культурная афиша" to 30,
                "Новости" to 30,
                "Вечерние новости" to 60,
                "Региональное шоу" to 90,
                "Ночной эфир" to 120
            )
            "religious" -> listOf(
                "Утреннее богослужение" to 60,
                "Религиозная программа" to 45,
                "Православные новости" to 30,
                "Лекторий" to 60,
                "Вечернее богослужение" to 90,
                "Ночной эфир" to 120
            )
            else -> listOf(
                "Утренние новости" to 60,
                "Общественная программа" to 60,
                "Сериал" to 90,
                "Новости" to 30,
                "Ток-шоу" to 60,
                "Вечерние новости" to 60,
                "Фильм" to 120,
                "Ночной эфир" to 120
            )
        }
    }
}
