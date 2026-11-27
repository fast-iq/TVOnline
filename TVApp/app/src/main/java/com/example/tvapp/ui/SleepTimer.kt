package com.example.tvapp.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Таймер сна плеера: отсчёт до паузы. Диалог выбирает минуты из [options],
 * состояние (активные минуты) нужен диалогу для отметки «текущий».
 */
internal class SleepTimer(
    private val scope: CoroutineScope,
    val options: List<Int> = listOf(0, 15, 30, 60, 90),
    private val onFire: () -> Unit
) {
    private var job: Job? = null

    /** Минуты активного таймера; 0 — выключен. */
    var activeMinutes: Int = 0
        private set

    fun isActive(): Boolean = job?.isActive == true

    /** 0/off выключает; >0 перезапускает отсчёт. Возвращает true, если таймер включён. */
    fun set(minutes: Int): Boolean {
        job?.cancel()
        job = null
        activeMinutes = minutes
        if (minutes <= 0) return false
        job = scope.launch {
            delay(minutes * 60_000L)
            onFire()
        }
        return true
    }

    fun cancel() {
        set(0)
    }
}
