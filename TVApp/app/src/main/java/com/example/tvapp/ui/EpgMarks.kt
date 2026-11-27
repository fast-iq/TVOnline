package com.example.tvapp.ui

import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.MarkedProgram
import com.example.tvapp.data.Program

/**
 * Отмеченные передачи EPG: хранение, пруном просроченных, детектор «пора» и
 * одноразовые промпты. Отвязано от активности (часы инъектируются) — Activity
 * отвечает только за диалог и уведомления через [onDue].
 */
internal class EpgMarks(
    private val preferences: AppPreferences,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val notifyAheadMs: Long = 5 * 60_000L
) {
    private val marks = mutableMapOf<String, MarkedProgram>()
    private val promptedKeys = mutableSetOf<String>()

    /** Изменение набора отметок — активности перерисовать выделения/синхронизировать будильники. */
    var onChanged: (Set<String>) -> Unit = {}

    fun keys(): Set<String> = marks.keys.toSet()

    /** Загрузка из prefs с выбрасыванием передач, которые уже закончились. */
    fun restore() {
        val now = clock()
        val stored = preferences.markedPrograms
        val future = stored.filter { it.endTime > now }
        if (future.size != stored.size) {
            preferences.markedPrograms = future
        }
        marks.clear()
        future.forEach { marks[it.key] = it }
    }

    private fun persist() {
        preferences.markedPrograms = marks.values.toList()
        onChanged(marks.keys.toSet())
    }

    /** Снимает отметку и возвращает её, либо ставит новую (вернёт null). */
    fun toggle(channel: Channel, program: Program, key: String): MarkedProgram? {
        val removed = marks.remove(key)
        if (removed != null) {
            promptedKeys.remove(key)
            persist()
            return removed
        }
        marks[key] = MarkedProgram(
            channelId = channel.id,
            channelName = channel.name,
            streamUrl = channel.streamUrl,
            title = program.title,
            startTime = program.startTime,
            endTime = program.endTime
        )
        persist()
        return null
    }

    /**
     * Отметки, которые «через минуту» начинаются и ещё не показаны; помечает их
     * как показанные, чтобы не долбить пользователя повторно.
     */
    fun takeDue(): List<MarkedProgram> {
        if (marks.isEmpty()) return emptyList()
        val now = clock()
        val due = marks.filter { (key, mark) ->
            key !in promptedKeys && mark.startTime > now && mark.startTime - now <= notifyAheadMs
        }
        if (due.isEmpty()) return emptyList()
        due.keys.forEach { promptedKeys.add(it) }
        return due.values.sortedBy { it.startTime }
    }

    /** Пользователь выбрал «смотреть» — снять показанные отметки совсем. */
    fun consume(due: List<MarkedProgram>) {
        due.forEach { mark ->
            marks.entries.removeAll { it.value == mark }
            promptedKeys.remove(mark.key)
        }
        persist()
    }
}
