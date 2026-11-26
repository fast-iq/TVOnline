package com.example.tvapp.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

/** Передача, отмеченная к просмотру. key совпадает с форматом EpgRowAdapter.keyOf. */
data class MarkedProgram(
    val channelId: String,
    val channelName: String,
    val streamUrl: String,
    val title: String,
    val startTime: Long,
    val endTime: Long
) {
    val key: String get() = "$channelId|$startTime|$endTime"
}

internal object MarkedProgramsCodec {

    fun encode(marks: Collection<MarkedProgram>): String {
        val array = JSONArray()
        for (mark in marks) {
            array.put(
                JSONObject()
                    .put("channel_id", mark.channelId)
                    .put("channel_name", mark.channelName)
                    .put("stream_url", mark.streamUrl)
                    .put("title", mark.title)
                    .put("start", mark.startTime)
                    .put("end", mark.endTime)
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<MarkedProgram> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<MarkedProgram>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val channelId = obj.optString("channel_id", "")
                val start = obj.optLong("start", 0L)
                val end = obj.optLong("end", 0L)
                if (channelId.isEmpty() || start <= 0L || end <= start) continue
                result.add(
                    MarkedProgram(
                        channelId = channelId,
                        channelName = obj.optString("channel_name", ""),
                        streamUrl = obj.optString("stream_url", ""),
                        title = obj.optString("title", ""),
                        startTime = start,
                        endTime = end
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Фоновые напоминания: на каждую будущую отметку ставится будильник AlarmManager
 * (не точный, окно 2 минуты — точные алармы требуют привилегий).
 * Синхронизация вызывается при изменении отметок, старте приложения и загрузке устройства.
 */
object ReminderScheduler {

    const val AHEAD_MS = 60_000L
    const val WINDOW_MS = 120_000L
    private const val EXTRA_MARK_KEY = "mark_key"

    fun sync(context: Context) {
        val marks = AppPreferences(context).markedPrograms
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        for (mark in marks) {
            manager.cancel(buildIntent(context, mark))
            if (mark.startTime - AHEAD_MS <= now) continue
            val triggerAt = mark.startTime - AHEAD_MS - WINDOW_MS
            manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_MS * 2, buildIntent(context, mark))
        }
    }

    fun cancel(context: Context, mark: MarkedProgram) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(buildIntent(context, mark))
    }

    internal fun buildIntent(context: Context, mark: MarkedProgram): PendingIntent {
        val intent = Intent(context, com.example.tvapp.ReminderReceiver::class.java).apply {
            putExtra(EXTRA_MARK_KEY, mark.key)
        }
        return PendingIntent.getBroadcast(
            context,
            mark.key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun markKeyExtra(intent: Intent): String? = intent.getStringExtra(EXTRA_MARK_KEY)

    internal fun upcoming(marks: List<MarkedProgram>, nowMs: Long): List<MarkedProgram> = marks.filter { it.startTime > nowMs }.sortedBy { it.startTime }
}
