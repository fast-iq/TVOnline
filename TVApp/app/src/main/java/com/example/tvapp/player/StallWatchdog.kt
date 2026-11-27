package com.example.tvapp.player

/** Действие по итогам проверки залипания воспроизведения. */
internal enum class StallAction { NONE, RECOVER }

/**
 * Детектор «заморозки» живого эфира: плеер в состоянии playing, но позиция не
 * движется дольше [stallTimeoutMs] — поток завис без ошибки, ExoPlayer сам не оклемается.
 * Чистая логика с инъекцией времени — покрыта тестами; периодический вызов — из TVPlayerManager.
 */
internal class StallWatchdog(
    private val stallTimeoutMs: Long = 30_000L,
    private val cooldownMs: Long = 60_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var lastPosition = -1L
    private var progressAt = 0L
    private var recoveryCooldownUntil = 0L

    /** Сбрасывает состояние (при смене канала/перезапуске). */
    fun reset() {
        lastPosition = -1L
        progressAt = 0L
    }

    /**
     * @param isPlaying плеер реально играет (пауза/буфер — не залипание, их лечит другая логика)
     * @param positionMs текущая позиция воспроизведения
     */
    fun onTick(isPlaying: Boolean, positionMs: Long): StallAction {
        val now = clock()
        if (!isPlaying) {
            reset()
            return StallAction.NONE
        }
        if (positionMs != lastPosition) {
            lastPosition = positionMs
            progressAt = now
            return StallAction.NONE
        }
        if (progressAt == 0L) {
            progressAt = now
            return StallAction.NONE
        }
        if (now < recoveryCooldownUntil) return StallAction.NONE
        if (now - progressAt < stallTimeoutMs) return StallAction.NONE
        recoveryCooldownUntil = now + cooldownMs
        reset()
        return StallAction.RECOVER
    }
}
