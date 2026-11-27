package com.example.tvapp.player

/** Решения health-guard'а плеера. */
internal enum class HealthAction { NONE, REFRESH_URL, RETURN_TO_LIVE, DOWNGRADE_QUALITY }

/**
 * Здоровье потока поверх [StallWatchdog]:
 * 1) залипание в BUFFERING дольше [stuckBufferingMs] — в эфире это REFRESH_URL
 *    (свежий токен), а при time-shift (архивный URL «протухает»/рвётся чаще) —
 *    RETURN_TO_LIVE: архив чинить бесполезно, возвращаемся в живой эфир (15);
 * 2) буфер впереди тоньше [lowLeadMs] подряд [lowLeadTicks] тиков — сеть не
 *    тянет текущий битрейт, DOWNGRADE_QUALITY для режима AUTO (14).
 * Чистая логика с инъекцией времени; тики — из 5-секундного опроса TVPlayerManager.
 */
internal class BufferHealth(
    private val stuckBufferingMs: Long = 8_000L,
    private val recoveryCooldownMs: Long = 60_000L,
    private val lowLeadMs: Long = 3_000L,
    private val lowLeadTicks: Int = 3,
    private val downgradeCooldownMs: Long = 90_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var bufferingSince = -1L
    private var recoveryUntil = 0L
    private var downgradeUntil = 0L
    private var lowLeadStreak = 0

    /** Смена канала/перезапуск: сбрасывает наблюдаемые состояния, кулдауны остаются. */
    fun reset() {
        bufferingSince = -1L
        lowLeadStreak = 0
    }

    /**
     * Тик состояния буферизации. [isBuffering] — плеер прямо сейчас STATE_BUFFERING,
     * [isArchive] — играем time-shift (архивное окно), а не живой эфир.
     */
    fun onBufferingTick(isBuffering: Boolean, isArchive: Boolean): HealthAction {
        if (!isBuffering) {
            bufferingSince = -1L
            return HealthAction.NONE
        }
        val now = clock()
        if (bufferingSince < 0L) {
            bufferingSince = now
            return HealthAction.NONE
        }
        if (now - bufferingSince < stuckBufferingMs) return HealthAction.NONE
        if (now < recoveryUntil) return HealthAction.NONE
        recoveryUntil = now + recoveryCooldownMs
        bufferingSince = -1L
        return if (isArchive) HealthAction.RETURN_TO_LIVE else HealthAction.REFRESH_URL
    }

    /**
     * Тик нормального воспроизведения: [leadMs] — запас буфера впереди
     * (bufferedPosition - currentPosition). Даунгрейд качества только в AUTO.
     */
    fun onPlaybackTick(isPlaying: Boolean, leadMs: Long, autoQuality: Boolean): HealthAction {
        if (!isPlaying || !autoQuality) {
            lowLeadStreak = 0
            return HealthAction.NONE
        }
        if (leadMs > lowLeadMs) {
            lowLeadStreak = 0
            return HealthAction.NONE
        }
        lowLeadStreak++
        if (lowLeadStreak < lowLeadTicks) return HealthAction.NONE
        val now = clock()
        if (now < downgradeUntil) {
            lowLeadStreak = 0
            return HealthAction.NONE
        }
        downgradeUntil = now + downgradeCooldownMs
        lowLeadStreak = 0
        return HealthAction.DOWNGRADE_QUALITY
    }
}
