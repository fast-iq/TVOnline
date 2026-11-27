package com.example.tvapp.data

import org.json.JSONArray
import org.json.JSONObject

/** Разбор ответов LimeHD API (плейлист/EPG/регионы). Чистые функции, покрыты LimeHdParserTest. */
internal object LimeHdParser {

    fun parseRegions(array: JSONArray?): List<LimeHdRegion> {
        if (array == null) return emptyList()
        val result = mutableListOf<LimeHdRegion>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val code = item.optInt("code", 0)
            val name = item.optString("name", "")
            if (code <= 0 || name.isBlank()) continue
            result.add(
                LimeHdRegion(
                    code = code,
                    name = name,
                    tzOffsets = parseTzOffsets(item.optJSONArray("tz"))
                )
            )
        }
        return result.sortedBy { it.name }
    }

    fun parseTzOffsets(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        val result = mutableListOf<Int>()
        for (i in 0 until array.length()) {
            val value = array.optInt(i, Int.MIN_VALUE)
            if (value != Int.MIN_VALUE) result.add(value)
        }
        return result.toList()
    }

    fun parseChannels(array: JSONArray): List<Channel> {
        val entries = mutableListOf<Pair<Int, Channel>>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (!item.optBoolean("available", true)) continue
            if (item.optInt("is_disabled_player", 0) != 0) continue

            val ourId = item.optLong("our_id", -1L)
            if (ourId < 0L) continue

            val stream = item.optJSONObject("stream")
            val streamUrl = firstNonBlank(
                stream?.optString("common"),
                item.optString("url"),
                stream?.optString("cdn"),
                item.optString("foreign_url")
            )
            if (streamUrl == null) continue

            val title = firstNonBlank(
                item.optString("name_ru"),
                item.optString("title"),
                item.optString("name")
            ) ?: continue

            val logoUrl = firstNonBlank(
                item.optString("cached_logo_url"),
                item.optString("image")
            ) ?: ""

            val fallbacks = mutableListOf<String>()
            addUrl(fallbacks, streamUrl)
            addUrl(fallbacks, item.optString("foreign_url"))
            addUrl(fallbacks, stream?.optString("cdn"))
            addUrl(fallbacks, item.optString("url"))

            val current = parseCurrentProgram(item.optJSONArray("current") ?: item.optJSONObject("current"))
            val programs = parseProgramList(item.optJSONArray("current") ?: item.optJSONObject("current"), "limehd_$ourId")

            val archiveUrl = firstNonBlank(
                stream?.optString("archive"),
                item.optString("url_archive")
            ) ?: ""
            val dayArchive = if (item.optBoolean("with_archive", archiveUrl.isNotEmpty())) {
                item.optInt("day_archive", if (archiveUrl.isNotEmpty()) 1 else 0)
            } else {
                0
            }

            val channel = Channel(
                id = "limehd_$ourId",
                name = title,
                logoUrl = logoUrl,
                streamUrl = streamUrl,
                category = if (item.optBoolean("is_federal")) "federal" else "general",
                epgId = ourId.toString(),
                channelImageUrl = logoUrl,
                fallbackStreamUrls = fallbacks.toList(),
                currentProgramTitle = current?.title,
                currentProgramStart = current?.startMs,
                currentProgramEnd = current?.endMs,
                programs = programs,
                isDrm = item.optBoolean("drm", false),
                regionCode = item.optInt("region_code", 0),
                number = item.optInt("number", 0),
                archiveUrl = archiveUrl,
                dayArchive = dayArchive
            )

            entries.add(Pair(item.optInt("number", Int.MAX_VALUE), channel))
        }

        return entries.sortedBy { it.first }.map { it.second }
    }

    private fun parseProgramList(value: Any?, channelId: String): List<Program> {
        val items = mutableListOf<JSONObject>()
        when (value) {
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    value.optJSONObject(i)?.let { items.add(it) }
                }
            }
            is JSONObject -> items.add(value)
        }

        val result = mutableListOf<Program>()
        for (item in items) {
            val startSec = item.optLong("timestart", 0L)
            val endSec = item.optLong("timestop", 0L)
            val title = item.optString("title", "")
            if (startSec <= 0L || endSec <= 0L || title.isBlank()) continue
            result.add(
                Program(
                    channelId = channelId,
                    title = title,
                    description = item.optString("desc", "").takeIf { it.isNotBlank() },
                    startTime = startSec * 1000L,
                    endTime = endSec * 1000L,
                    iconUrl = firstNonBlank(item.optString("preview"), item.optString("posterUrl"))
                )
            )
        }
        return result.sortedBy { it.startTime }
    }

    private fun parseCurrentProgram(value: Any?): LimeHdProgram? {
        val obj = when (value) {
            is JSONArray -> value.optJSONObject(0)
            is JSONObject -> value
            else -> null
        } ?: return null

        val title = obj.optString("title", "").takeIf { it.isNotBlank() } ?: return null
        val startSec = obj.optLong("timestart", 0L)
        val endSec = obj.optLong("timestop", 0L)
        return LimeHdProgram(
            title = title,
            startMs = if (startSec > 0L) startSec * 1000L else null,
            endMs = if (endSec > 0L) endSec * 1000L else null
        )
    }

    internal fun parseEpg(root: JSONObject, channelId: String): List<Program> {
        val days = root.optJSONArray("epg") ?: return emptyList()
        val result = mutableListOf<Program>()
        for (i in 0 until days.length()) {
            val day = days.optJSONObject(i) ?: continue
            val items = day.optJSONArray("data") ?: continue
            for (j in 0 until items.length()) {
                val item = items.optJSONObject(j) ?: continue
                val startSec = item.optLong("timestart", 0L)
                val endSec = item.optLong("timestop", 0L)
                if (startSec <= 0L || endSec <= 0L) continue
                val title = item.optString("title", "")
                if (title.isBlank()) continue
                result.add(
                    Program(
                        channelId = channelId,
                        title = title,
                        description = item.optString("desc", "").takeIf { it.isNotBlank() },
                        startTime = startSec * 1000L,
                        endTime = endSec * 1000L,
                        iconUrl = firstNonBlank(item.optString("preview"), item.optString("posterUrl"))
                    )
                )
            }
        }
        return result.sortedBy { it.startTime }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        for (value in values) {
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun addUrl(list: MutableList<String>, url: String?) {
        if (url.isNullOrBlank()) return
        if (!list.contains(url)) list.add(url)
    }
}

private data class LimeHdProgram(val title: String?, val startMs: Long?, val endMs: Long?)
