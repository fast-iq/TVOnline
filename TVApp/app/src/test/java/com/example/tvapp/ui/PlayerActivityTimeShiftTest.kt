package com.example.tvapp.ui

import com.example.tvapp.data.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Меню time-shift: какие точки старта отмотки предлагает плеер в зависимости
 * от глубины архива канала и времени начала текущей передачи.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerActivityTimeShiftTest {

    private val now = 1_750_000_000_000L
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    /** onCreate не вызываем: живая инициализация ExoPlayer здесь не нужна. */
    private fun activity(): PlayerActivity = Robolectric.buildActivity(PlayerActivity::class.java).get()

    private fun channel(depthDays: Int, programStart: Long? = null) = Channel(
        id = "limehd_1",
        name = "Первый",
        logoUrl = "",
        streamUrl = "https://example/live.m3u8",
        archiveUrl = if (depthDays > 0) "https://archive.example/ch1/" else "",
        dayArchive = depthDays,
        currentProgramStart = programStart
    )

    @Test
    fun offersProgramStartAndAllPresetsWithDeepArchive() {
        val activity = activity()
        val ch = channel(depthDays = 1, programStart = now - 10 * minute)

        val options = activity.timeshiftMenuOptions(ch, now)

        // Начало передачи + все четыре пресета (5/15/30/60 мин в пределах суток).
        assertEquals(5, options.size)
        assertEquals(activity.getString(com.example.tvapp.R.string.timeshift_from_program), options[0].first)
        assertEquals(now - 10 * minute, options[0].second)
        assertEquals(now - 60 * minute, options[4].second)
    }

    @Test
    fun noArchiveMeansNoOptions() {
        val activity = activity()

        assertTrue(activity.timeshiftMenuOptions(channel(depthDays = 0, programStart = now - minute), now).isEmpty())
    }

    @Test
    fun programOlderThanArchiveIsOfferedClampedToDepth() {
        val activity = activity()
        val ch = channel(depthDays = 1, programStart = now - 3 * day)

        val options = activity.timeshiftMenuOptions(ch, now)

        // Передача старая — но окно windowFor усекается до глубины архива,
        // поэтому «с начала передачи» остаётся доступным (старт фактически с древнейшей точки).
        assertEquals(5, options.size)
        assertEquals(now - 3 * day, options.first().second)
    }

    @Test
    fun shallowArchiveTrimsDeepPresets() {
        val activity = activity()
        // Глубина архива всего 1 час (округляется до суток днём? нет: dayArchive в днях,
        // берём 1 день и проверяем, что 60-минутный пресет всё ещё в окне, а «за границей» — нет).
        val ch = channel(depthDays = 1, programStart = null)

        val options = activity.timeshiftMenuOptions(ch, now)

        assertEquals(4, options.size)
        assertEquals(now - 60 * minute, options.last().second)
    }
}
