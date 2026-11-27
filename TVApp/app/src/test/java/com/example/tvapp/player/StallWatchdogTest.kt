package com.example.tvapp.player

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Залипание живого эфира: позиция стоит >= 30 с при playing — RECOVER, с cooldown; всё остальное — NONE. */
class StallWatchdogTest {

    private var now = 1_000_000L
    private lateinit var watchdog: StallWatchdog

    @Before
    fun setUp() {
        now = 1_000_000L
        watchdog = StallWatchdog(stallTimeoutMs = 30_000L, cooldownMs = 60_000L, clock = { now })
    }

    private fun tick(isPlaying: Boolean = true, positionMs: Long) = watchdog.onTick(isPlaying, positionMs)

    @Test
    fun advancingPositionNeverRecovers() {
        var position = 0L
        repeat(20) {
            assertEquals(StallAction.NONE, tick(positionMs = position))
            position += 5_000
            now += 5_000
        }
    }

    @Test
    fun frozenPositionTriggersRecoverAfterTimeout() {
        assertEquals(StallAction.NONE, tick(positionMs = 100L))
        now += 10_000
        assertEquals(StallAction.NONE, tick(positionMs = 100L))
        now += 20_000
        assertEquals(StallAction.RECOVER, tick(positionMs = 100L))
    }

    @Test
    fun repeatedStallIsSuppressedByCooldown() {
        tick(positionMs = 100L)
        now += 31_000
        assertEquals(StallAction.RECOVER, tick(positionMs = 100L))
        // Позиция по-прежнему стоит, но кулдаун не даёт перезапускать каждые 5 секунд.
        now += 10_000
        assertEquals(StallAction.NONE, tick(positionMs = 100L))
        now += 30_000
        assertEquals(StallAction.NONE, tick(positionMs = 100L)) // 40 с без движения, но кулдаун ещё действует
        now += 40_000
        assertEquals(StallAction.RECOVER, tick(positionMs = 100L)) // кулдаун истёк, зависание подтверждено
    }

    @Test
    fun resumedProgressCancelsPendingStall() {
        tick(positionMs = 100L)
        now += 25_000
        assertEquals(StallAction.NONE, tick(positionMs = 150L))
        now += 25_000
        assertEquals(StallAction.NONE, tick(positionMs = 150L))
        now += 10_000
        assertEquals(StallAction.RECOVER, tick(positionMs = 150L))
    }

    @Test
    fun pausedPlayerIsNotStall() {
        tick(positionMs = 100L)
        now += 120_000
        assertEquals(StallAction.NONE, tick(isPlaying = false, positionMs = 100L))
        // После снятия с паузы отсчёт начинается заново.
        now += 29_000
        assertEquals(StallAction.NONE, tick(positionMs = 100L))
        now += 1_000
        assertEquals(StallAction.NONE, tick(positionMs = 100L))
        now += 30_000
        assertEquals(StallAction.RECOVER, tick(positionMs = 100L))
    }

    @Test
    fun resetForgetsHistory() {
        tick(positionMs = 100L)
        now += 25_000
        watchdog.reset()
        now += 25_000
        assertEquals(StallAction.NONE, tick(positionMs = 100L))
    }
}
