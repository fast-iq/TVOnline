package com.example.tvapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePlaybackTest {

    private val dayMs = 24L * 3600_000L

    private fun archivedChannel(days: Int = 3) = Channel(
        id = "limehd_129",
        name = "ТВ ЦЕНТР",
        logoUrl = "",
        streamUrl = "https://mhd.example/live/index.m3u8",
        archiveUrl = "https://hlsarchive.iptv2022.com/token123/",
        dayArchive = days
    )

    @Test
    fun buildUrlMatchesServerFormat() {
        val url = ArchivePlayback.buildUrl("https://hlsarchive.example/tok/", 1_000L, 2_000L)
        assertEquals("https://hlsarchive.example/tok/index-1000-2000.m3u8", url)
    }

    @Test
    fun buildUrlToleratesMissingTrailingSlash() {
        val url = ArchivePlayback.buildUrl("https://hlsarchive.example/tok", 5L, 6L)
        assertEquals("https://hlsarchive.example/tok/index-5-6.m3u8", url)
    }

    @Test
    fun hasArchiveRequiresUrlAndDepth() {
        assertTrue(ArchivePlayback.hasArchive(archivedChannel()))
        assertTrue(!ArchivePlayback.hasArchive(archivedChannel().copy(dayArchive = 0)))
        assertTrue(!ArchivePlayback.hasArchive(archivedChannel().copy(archiveUrl = "")))
    }

    @Test
    fun windowForFinishedProgramUsesFullProgramWindow() {
        val now = 10L * dayMs
        val ch = archivedChannel(days = 3)
        val start = now - 5 * 3600_000L
        val end = now - 4 * 3600_000L
        val window = ArchivePlayback.windowFor(ch, start, end, now)
        assertEquals(Pair(start, end), window)
    }

    @Test
    fun windowForOngoingProgramClampsEndByMargin() {
        val now = 10L * dayMs
        val ch = archivedChannel(days = 3)
        val start = now - 2 * 3600_000L
        val end = now + 3_600_000L
        val window = ArchivePlayback.windowFor(ch, start, end, now)
        assertEquals(Pair(start, now - ArchivePlayback.END_MARGIN_MS), window)
    }

    @Test
    fun windowForClampsStartToArchiveDepth() {
        val now = 10L * dayMs
        val ch = archivedChannel(days = 1)
        val start = now - 2 * dayMs
        val end = now - 1 * dayMs + 2 * 3600_000L
        val window = ArchivePlayback.windowFor(ch, start, end, now)
        assertEquals(Pair(now - dayMs, end), window)
    }

    @Test
    fun windowForReturnsNullWhenOutOfDepthOrFuture() {
        val now = 10L * dayMs
        val ch = archivedChannel(days = 1)
        assertNull(ArchivePlayback.windowFor(ch, now - 3 * dayMs, now - 2 * dayMs, now))
        assertNull(ArchivePlayback.windowFor(ch, now + 1_000L, now + 2_000L, now))
        assertNull(ArchivePlayback.windowFor(archivedChannel(days = 0), now - 2_000L, now - 1_000L, now))
    }

    @Test
    fun windowForReturnsNullWhenWindowTooSmall() {
        val now = 10L * dayMs
        val ch = archivedChannel(days = 1)
        val start = now - 30_000L
        val end = now - 10_000L
        assertNull(ArchivePlayback.windowFor(ch, start, end, now))
    }
}
