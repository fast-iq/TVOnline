package com.example.tvapp.data

/**
 * Построение ссылок на архив (time-shift) LimeHD.
 * Формат: {archive_base}index-{startMs}-{endMs}.m3u8 — сервер отдаёт готовый
 * плейлист на любой интервал в пределах глубины архива канала (day_archive дней).
 */
object ArchivePlayback {

    /** Сервер отстаёт от живого эфира: конец окна берём с запасом. */
    const val END_MARGIN_MS = 60_000L

    private const val DAY_MS = 24L * 3600_000L

    fun hasArchive(channel: Channel): Boolean = channel.archiveUrl.isNotEmpty() && channel.dayArchive > 0

    fun buildUrl(archiveBase: String, startMs: Long, endMs: Long): String {
        val base = archiveBase.trimEnd('/')
        return "$base/index-$startMs-$endMs.m3u8"
    }

    /**
     * Окно [start, end] для просмотра передачи из архива или null, если
     * передача недоступна в архиве (нет архива / глубина не покрывает / ещё не началась).
     */
    fun windowFor(channel: Channel, programStartMs: Long, programEndMs: Long, nowMs: Long): Pair<Long, Long>? {
        if (!hasArchive(channel)) return null
        val earliest = nowMs - channel.dayArchive * DAY_MS
        if (programEndMs <= earliest || programStartMs >= nowMs) return null
        val start = maxOf(programStartMs, earliest)
        val end = minOf(programEndMs, nowMs - END_MARGIN_MS)
        return if (end > start) Pair(start, end) else null
    }
}
