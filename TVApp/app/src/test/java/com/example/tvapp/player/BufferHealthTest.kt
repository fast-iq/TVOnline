package com.example.tvapp.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Чистая логика health-guard'а: залипание в BUFFERING (эфир — рефетч, архив —
 * возврат в живой эфир) и ступенчатый даунгрейд качества при голодании буфера.
 */
class BufferHealthTest {

    private var now = 0L
    private fun health() = BufferHealth(clock = { now })

    @Test
    fun stuckBufferingInLiveRefreshesUrlAfterThreshold() {
        val h = health()
        assertEquals(HealthAction.NONE, h.onBufferingTick(true, isArchive = false)) // first tick stamps
        now = 5_000
        assertEquals(HealthAction.NONE, h.onBufferingTick(true, false)) // < 8s
        now = 9_000
        assertEquals(HealthAction.REFRESH_URL, h.onBufferingTick(true, false))
    }

    @Test
    fun stuckBufferingInArchiveReturnsToLive() {
        val h = health()
        h.onBufferingTick(true, isArchive = true)
        now = 9_000
        assertEquals(HealthAction.RETURN_TO_LIVE, h.onBufferingTick(true, isArchive = true))
    }

    @Test
    fun recoveryIsGatedByCooldown() {
        val h = health()
        h.onBufferingTick(true, false)
        now = 9_000
        assertEquals(HealthAction.REFRESH_URL, h.onBufferingTick(true, false))
        // Still buffering right after a recovery -> cooldown must suppress a second one.
        now = 12_000
        assertEquals(HealthAction.NONE, h.onBufferingTick(true, false))
        now = 9_000 + 60_000
        assertEquals(HealthAction.REFRESH_URL, h.onBufferingTick(true, false))
    }

    @Test
    fun leavingBufferingResetsTheStuckClock() {
        val h = health()
        h.onBufferingTick(true, false)
        now = 6_000
        assertEquals(HealthAction.NONE, h.onBufferingTick(false, false)) // recovered on its own
        now = 11_000 // > 8s after the stamp, but the clock was reset when buffering cleared
        assertEquals(HealthAction.NONE, h.onBufferingTick(true, false)) // new stamp
        now = 13_000
        assertEquals(HealthAction.NONE, h.onBufferingTick(true, false))
        now = 20_000
        assertEquals(HealthAction.REFRESH_URL, h.onBufferingTick(true, false))
    }

    @Test
    fun starvationDowngradesAutoQualityAfterConsecutiveTicks() {
        val h = health()
        assertEquals(HealthAction.NONE, h.onPlaybackTick(true, leadMs = 1_000, autoQuality = true))
        assertEquals(HealthAction.NONE, h.onPlaybackTick(true, 1_000, true))
        assertEquals(HealthAction.DOWNGRADE_QUALITY, h.onPlaybackTick(true, 1_000, true))
    }

    @Test
    fun healthyLeadResetsStarvationStreak() {
        val h = health()
        h.onPlaybackTick(true, 1_000, true)
        h.onPlaybackTick(true, 1_000, true)
        assertEquals(HealthAction.NONE, h.onPlaybackTick(true, 10_000, true)) // recovered -> streak cleared
        assertEquals(HealthAction.NONE, h.onPlaybackTick(true, 1_000, true))
        assertEquals(HealthAction.NONE, h.onPlaybackTick(true, 1_000, true))
        assertEquals(HealthAction.DOWNGRADE_QUALITY, h.onPlaybackTick(true, 1_000, true))
    }

    @Test
    fun downgradeNeverFiresOutsideAutoQuality() {
        val h = health()
        repeat(5) { assertEquals(HealthAction.NONE, h.onPlaybackTick(true, 1_000, autoQuality = false)) }
    }

    @Test
    fun downgradeIsRateLimitedByCooldown() {
        val h = health()
        h.onPlaybackTick(true, 1_000, true)
        h.onPlaybackTick(true, 1_000, true)
        assertEquals(HealthAction.DOWNGRADE_QUALITY, h.onPlaybackTick(true, 1_000, true))
        // Streak builds again but the 90s cooldown must hold the next step.
        now += 30_000
        repeat(3) { assertEquals(HealthAction.NONE, h.onPlaybackTick(true, 1_000, true)) }
        now += 90_000
        assertEquals(HealthAction.NONE, h.onPlaybackTick(true, 1_000, true)) // streak restarts after the gated tick
        assertEquals(HealthAction.NONE, h.onPlaybackTick(true, 1_000, true))
        assertEquals(HealthAction.DOWNGRADE_QUALITY, h.onPlaybackTick(true, 1_000, true))
    }

    @Test
    fun pausedPlaybackNeverCountsAsStarvation() {
        val h = health()
        repeat(5) { assertEquals(HealthAction.NONE, h.onPlaybackTick(false, 1_000, true)) }
    }

    @Test
    fun resetClearsStuckStateButKeepsCooldowns() {
        val h = health()
        h.onBufferingTick(true, false)
        h.reset()
        now = 9_000
        assertEquals(HealthAction.NONE, h.onBufferingTick(true, false)) // re-stamped after reset
    }
}
