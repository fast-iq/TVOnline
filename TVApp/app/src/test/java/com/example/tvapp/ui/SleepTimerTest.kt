package com.example.tvapp.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Механика таймера сна (состояния/перезапуск) — срабатывание по времени проверяет delay(). */
class SleepTimerTest {

    private fun timer(fired: (Int) -> Unit = {}): SleepTimer = SleepTimer(CoroutineScope(Dispatchers.Default + Job()), onFire = { fired(1) })

    @Test
    fun setActivatesAndTracksMinutes() {
        val t = timer()
        assertTrue(t.set(30))
        assertEquals(30, t.activeMinutes)
        assertTrue(t.isActive())
    }

    @Test
    fun setZeroTurnsOff() {
        val t = timer()
        t.set(30)
        assertFalse(t.set(0))
        assertEquals(0, t.activeMinutes)
        assertFalse(t.isActive())
    }

    @Test
    fun reSetReplacesPreviousTimer() {
        val t = timer()
        t.set(15)
        t.set(90)
        assertEquals(90, t.activeMinutes)
    }

    @Test
    fun negativeMinutesMeansOff() {
        val t = timer()
        assertFalse(t.set(-5))
        assertFalse(t.isActive())
    }

    @Test
    fun cancelTurnsOffActiveTimer() {
        val t = timer()
        t.set(60)
        t.cancel()
        assertFalse(t.isActive())
        assertEquals(0, t.activeMinutes)
    }
}
