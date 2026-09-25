package com.example.tvapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class EPGRepository {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val epgApiBase = "https://api.epgservice.ru"
    private val epgToken = AppPreferences.EPG_SERVICE_TOKEN

    private var channelIndexCache: Map<String, String>? = null

    suspend fun getProgramForChannel(channelId: String, date: Date): List<Program> {
        return withContext(Dispatchers.IO) {
            try {
                when (channelId) {
                    "c1r" -> fetchC1RSchedule(date).ifEmpty { fetchEpgServiceSchedule(channelId, date) ?: generateFallbackEPG(channelId, date) }
                    else -> fetchEpgServiceSchedule(channelId, date) ?: generateFallbackEPG(channelId, date)
                }
            } catch (e: Exception) {
                generateFallbackEPG(channelId, date)
            }
        }
    }

    suspend fun getProgramsForAllChannels(date: Date): Map<String, List<Program>> {
        return withContext(Dispatchers.IO) {
            val programs = mutableMapOf<String, List<Program>>()
            for (channel in ChannelList.channels) {
                try {
                    val channelPrograms = getProgramForChannel(channel.id, date)
                    programs[channel.id] = channelPrograms
                } catch (e: Exception) {
                    programs[channel.id] = generateFallbackEPG(channel.id, date)
                }
            }
            programs
        }
    }

    private fun fetchEpgServiceSchedule(channelId: String, date: Date): List<Program>? {
        if (epgToken.isBlank()) return null
        try {
            val index = loadChannelIndex() ?: return null
            val epgChannelId = index[channelId] ?: return null
            val weekMonday = getWeekMonday(date)
            val url = "$epgApiBase/v1/schedule/$epgChannelId?week=$weekMonday"
            val jsonStr = httpGet(url, authHeader = "Bearer $epgToken") ?: return null
            val json = JSONObject(jsonStr)
            val programms = json.optJSONArray("programms") ?: return emptyList()

            val result = mutableListOf<Program>()
            for (i in 0 until programms.length()) {
                val item = programms.getJSONObject(i)
                val startUt = item.optLong("start_ut", 0L)
                val stopUt = item.optLong("stop_ut", 0L)
                if (startUt == 0L || stopUt == 0L) continue

                val iconArray = item.optJSONArray("icon")
                var iconUrl: String? = null
                if (iconArray != null && iconArray.length() > 0) {
                    iconUrl = iconArray.getJSONObject(0).optString("src", "")
                    if (iconUrl.isBlank()) iconUrl = null
                }

                result.add(
                    Program(
                        channelId = channelId,
                        title = item.optString("title", ""),
                        description = item.optString("desc_short", "").ifBlank { item.optString("desc", "") }.ifBlank { null },
                        startTime = startUt * 1000,
                        endTime = stopUt * 1000,
                        iconUrl = iconUrl
                    )
                )
            }
            return result
        } catch (e: Exception) {
            return null
        }
    }

    private fun loadChannelIndex(): Map<String, String>? {
        channelIndexCache?.let { return it }
        val url = "$epgApiBase/v1/index"
        val jsonStr = httpGet(url, authHeader = "Bearer $epgToken") ?: return null
        try {
            val json = JSONObject(jsonStr)
            val channels = json.optJSONArray("channel") ?: return null
            val nameToId = mutableMapOf<String, String>()

            for (i in 0 until channels.length()) {
                val item = channels.getJSONObject(i)
                val epgId = item.optString("id", "")
                val name = item.optString("display_name", "")
                if (epgId.isBlank() || name.isBlank()) continue
                nameToId[name.trim().lowercase()] = epgId
            }

            val cache = mutableMapOf<String, String>()
            for (channel in ChannelList.channels) {
                val key = channel.name.trim().lowercase()
                val exactHit = nameToId[key]
                if (exactHit != null) {
                    cache[channel.id] = exactHit
                    continue
                }
                for ((displayName, id) in nameToId) {
                    if (key.contains(displayName) || displayName.contains(key)) {
                        cache[channel.id] = id
                        break
                    }
                }
            }

            channelIndexCache = cache
            return cache
        } catch (e: Exception) {
            return null
        }
    }

    private fun getWeekMonday(date: Date): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow")).apply {
            time = date
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(calendar.time)
    }

    private fun fetchC1RSchedule(date: Date): List<Program> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = dateFormat.format(date)
        val url = "https://www.1tv.ru/schedule?date=$dateStr"

        val html = httpGet(url) ?: return emptyList()

        val scheduleDataMatch = Regex("\"scheduleData\"\\s*:\\s*\\{").find(html) ?: return emptyList()
        val startIdx = scheduleDataMatch.range.last + 1
        val jsonEnd = findMatchingBrace(html, startIdx - 1) ?: return emptyList()
        val jsonString = html.substring(startIdx - 1, jsonEnd + 1)

        return try {
            val scheduleJson = JSONObject(jsonString)
            val programs = mutableListOf<Program>()

            for (dateKey in scheduleJson.keys()) {
                val dayArray = scheduleJson.getJSONArray(dateKey)
                for (i in 0 until dayArray.length()) {
                    val item = dayArray.getJSONObject(i)
                    val startSec = item.optLong("datetimeStart", 0L)
                    val endSec = item.optLong("datetimeEnd", 0L)
                    if (startSec == 0L || endSec == 0L) continue

                    programs.add(
                        Program(
                            channelId = "c1r",
                            title = item.optString("title", ""),
                            description = item.optString("lead", ""),
                            startTime = startSec * 1000,
                            endTime = endSec * 1000,
                            iconUrl = item.optString("photo", "")
                        )
                    )
                }
            }
            programs
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun findMatchingBrace(text: String, openIdx: Int): Int? {
        if (openIdx >= text.length || text[openIdx] != '{') return null
        var depth = 0
        for (i in openIdx until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun httpGet(url: String, authHeader: String? = null): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json, text/html")
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

    private fun generateFallbackEPG(channelId: String, baseDate: Date): List<Program> {
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
        var currentTime = calendar.timeInMillis - moscowTz.getOffset(calendar.timeInMillis).toLong()

        for (i in 0 until templates.size) {
            val durationMs = templates[i].second * 60L * 1000L
            val startTime = currentTime
            val endTime = startTime + durationMs
            programs.add(
                Program(
                    channelId = channelId,
                    title = templates[i].first,
                    description = null,
                    startTime = startTime,
                    endTime = endTime,
                    iconUrl = null
                )
            )
            currentTime = endTime
        }
        return programs
    }

    private fun getChannelTemplates(channelId: String): List<Pair<String, Int>> {
        val channel = ChannelList.channels.find { it.id == channelId }
        return when (channel?.category) {
            "news" -> listOf(
                "Утренние новости" to 60, "Новости" to 30, "Специальный репортаж" to 45,
                "Новости" to 30, "Аналитическая программа" to 60, "Новости" to 30,
                "Вечерние новости" to 60, "Общественно-политическое шоу" to 90,
                "Новости" to 30, "Ночной эфир" to 120
            )
            "entertainment" -> listOf(
                "Утреннее шоу" to 120, "Ток-шоу" to 60, "Сериал" to 90,
                "Новости" to 30, "Развлекательное шоу" to 60, "Сериал" to 90,
                "Вечернее шоу" to 120, "Премьера" to 60, "Поздний фильм" to 120
            )
            "movies" -> listOf(
                "Утренний фильм" to 100, "Сериал" to 90, "Детектив" to 100,
                "Новости кино" to 30, "Мелодрама" to 110, "Боевик" to 120,
                "Вечерний фильм" to 120, "Премьера" to 110, "Ночной фильм" to 130
            )
            "kids" -> listOf(
                "Утренние мультфильмы" to 60, "Образовательная программа" to 30,
                "Мультсериал" to 45, "Игровая программа" to 30, "Мультфильм" to 60,
                "Детское шоу" to 45, "Мультсериал" to 60, "Вечерний мультфильм" to 90
            )
            "sport" -> listOf(
                "Утренняя аэробика" to 30, "Спортивный обзор" to 60, "Трансляция матча" to 120,
                "Спортивная аналитика" to 60, "Трансляция матча" to 120, "Новости спорта" to 30,
                "Вечерний спортивный обзор" to 90, "Документальный о спорте" to 60
            )
            "music" -> listOf(
                "Утренние хиты" to 120, "Музыкальный чарт" to 60, "Клипы и премьеры" to 90,
                "Концерт" to 120, "Музыкальное шоу" to 90, "Вечерние хиты" to 120,
                "Ночной микс" to 180
            )
            "culture" -> listOf(
                "Утренняя культурная программа" to 60, "Телевизионный театр" to 90,
                "Документальный фильм" to 60, "Культурные новости" to 30,
                "Концерт" to 90, "Вечерний спектакль" to 120, "Ночной кинопоказ" to 100
            )
            "nature" -> listOf(
                "Утренние дикие животные" to 60, "Документальный о природе" to 90,
                "Путешествия" to 60, "Новости науки" to 30, "Дикая природа" to 90,
                "Вечерний документальный" to 120, "Ночной эфир" to 120
            )
            "documentary" -> listOf(
                "Утренний документальный" to 60, "Историческая программа" to 90,
                "Научный фильм" to 60, "Репортаж" to 45, "Великие открытия" to 90,
                "Вечерний документальный" to 120, "Ночной эфир" to 120
            )
            "lifestyle" -> listOf(
                "Утренние советы" to 60, "Кулинарное шоу" to 45, "Дизайн интерьера" to 30,
                "Здоровый образ жизни" to 45, "Путешествия" to 60, "Вечернее шоу" to 90,
                "Ночной эфир" to 120
            )
            "hunting_fishing" -> listOf(
                "Утренняя рыбалка" to 60, "Охота и природа" to 90, "Мастер-класс" to 45,
                "Рыболовный репортаж" to 60, "Охотничьи истории" to 90,
                "Вечерний эфир" to 120, "Ночной эфир" to 120
            )
            "relax" -> listOf(
                "Утренние медитации" to 60, "Релакс-музыка" to 90, "Йога и здоровье" to 45,
                "Природа в движении" to 60, "Спокойный вечер" to 120, "Ночной релакс" to 180
            )
            "series" -> listOf(
                "Утренний сериал" to 90, "Детективный сериал" to 90, "Ситком" to 30,
                "Мелодрама" to 90, "Криминальный сериал" to 90, "Вечерний сериал" to 120,
                "Ночной эфир" to 120
            )
            "regional" -> listOf(
                "Утренние новости региона" to 60, "Общественная программа" to 45,
                "Культурная афиша" to 30, "Новости" to 30, "Вечерние новости" to 60,
                "Региональное шоу" to 90, "Ночной эфир" to 120
            )
            "religious" -> listOf(
                "Утреннее богослужение" to 60, "Религиозная программа" to 45,
                "Православные новости" to 30, "Лекторий" to 60, "Вечернее богослужение" to 90,
                "Ночной эфир" to 120
            )
            else -> listOf(
                "Утренние новости" to 60, "Общественная программа" to 60,
                "Сериал" to 90, "Новости" to 30, "Ток-шоу" to 60,
                "Вечерние новости" to 60, "Фильм" to 120, "Ночной эфир" to 120
            )
        }
    }

    fun getCurrentProgram(programs: List<Program>, currentTime: Long = System.currentTimeMillis()): Program? {
        return programs.find { it.isLive(currentTime) }
    }

    fun getNextProgram(programs: List<Program>, currentTime: Long = System.currentTimeMillis()): Program? {
        return programs.filter { it.startTime > currentTime }.minByOrNull { it.startTime }
    }
}
