package com.example.tvapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {

    private fun report(throwable: Throwable): String = CrashReporter.formatReport(
        thread = Thread("player-thread"),
        throwable = throwable,
        versionName = "1.2.3",
        nowMs = 1_764_000_000_000L,
        model = "Xiaomi TV Box S",
        osVersion = "11",
        sdkInt = 30
    )

    @Test
    fun headerDescribesDeviceThreadAndVersion() {
        val text = report(IllegalStateException("boom"))
        val lines = text.lines()
        assertEquals("time_ms=1764000000000", lines[0])
        assertEquals("app_version=1.2.3", lines[1])
        assertEquals("thread=player-thread", lines[2])
        assertEquals("model=Xiaomi TV Box S", lines[3])
        assertEquals("android=11 (api 30)", lines[4])
        assertEquals("", lines[5])
    }

    @Test
    fun bodyContainsExceptionClassAndMessage() {
        val text = report(IllegalStateException("buffer exploded"))
        assertTrue(text.contains("java.lang.IllegalStateException: buffer exploded"))
        assertTrue(text.contains(CrashReporterTest::class.java.name))
    }

    @Test
    fun causeChainIsPreserved() {
        val cause = IllegalArgumentException("первопричина")
        val text = report(RuntimeException("внешняя", cause))
        assertTrue(text.contains("Caused by: java.lang.IllegalArgumentException"))
        assertTrue(text.contains("первопричина"))
    }

    @Test
    fun pruneKeepsEverythingWithinLimit() {
        assertTrue(CrashReporter.prune(emptyList(), 5).isEmpty())
        assertTrue(CrashReporter.prune(listOf("crash-a.txt", "crash-b.txt"), 5).isEmpty())
        assertTrue(CrashReporter.prune(listOf("crash-a.txt", "crash-b.txt"), 2).isEmpty())
    }

    @Test
    fun pruneReturnsOldestNamesBeyondLimit() {
        val names = listOf(
            "crash-20261126-120000-main.txt",
            "crash-20261126-080000-main.txt",
            "crash-20261126-100000-player.txt"
        )
        assertEquals(
            listOf("crash-20261126-080000-main.txt"),
            CrashReporter.prune(names, 2)
        )
    }
}
