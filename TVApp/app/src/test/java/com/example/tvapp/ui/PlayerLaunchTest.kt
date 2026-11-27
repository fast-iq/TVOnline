package com.example.tvapp.ui

import android.content.Context
import com.example.tvapp.data.ArchivePlayback
import com.example.tvapp.data.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Единая сборка плеер-интентов: on не должен терять/путать extras. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerLaunchTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun channel(archiveDays: Int = 0) = Channel(
        id = "ch1",
        name = "Первый",
        logoUrl = "https://logo/1.png",
        streamUrl = "https://stream/ch1.m3u8",
        dayArchive = archiveDays,
        archiveUrl = if (archiveDays > 0) "https://archive/ch1" else ""
    )

    @Test
    fun forChannelCarriesIdentityAndLogo() {
        val intent = PlayerLaunch.forChannel(context, channel())

        assertEquals("ch1", intent.getStringExtra("channel_id"))
        assertEquals("Первый", intent.getStringExtra("channel_name"))
        assertEquals("https://stream/ch1.m3u8", intent.getStringExtra("stream_url"))
        assertEquals("https://logo/1.png", intent.getStringExtra("logo_url"))
        assertNull(intent.getLongExtra("archive_start", -1L).takeIf { it >= 0 })
    }

    @Test
    fun openArchivedWithoutArchiveWindowReturnsFalse() {
        val opened = PlayerLaunch.openArchived(context, channel(archiveDays = 0), System.currentTimeMillis() - 3_600_000, System.currentTimeMillis() - 1_800_000, System.currentTimeMillis())

        assertTrue("нет архива — не открываем", !opened)
    }

    @Test
    fun archiveWindowMathHonoursDepth() {
        val now = System.currentTimeMillis()
        val ch = channel(archiveDays = 1)

        val window = ArchivePlayback.windowFor(ch, now - 3_600_000, now - 1_800_000, now)

        assertEquals(now - 3_600_000, window?.first)
        assertEquals(now - 1_800_000, window?.second)
    }
}
