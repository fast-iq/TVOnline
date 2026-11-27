package com.example.tvapp.ui

import android.content.Context
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.MarkedProgram
import com.example.tvapp.data.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Логика отметок EPG (хранение/дубликаты/«пора»/одноразовые промпты) вне активности. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgMarksTest {

    private var now = 1_000_000L
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun marks() = EpgMarks(AppPreferences(context), clock = { now }, notifyAheadMs = 60_000L)

    private fun program(startMs: Long, endMs: Long, title: String = "Матч"): Program = Program(channelId = "ch1", title = title, description = null, startTime = startMs, endTime = endMs)

    private fun mark(startMs: Long, endMs: Long, title: String = "Матч"): MarkedProgram = MarkedProgram("ch1", "Первый", "https://stream/ch1", title, startMs, endMs)

    @Test
    fun toggleAddsThenRemoves() {
        val m = marks()
        val ch = Channel(id = "ch1", name = "Первый", logoUrl = "", streamUrl = "https://stream/ch1")
        val p = program(now + 3_600_000, now + 7_200_000)

        assertNull(m.toggle(ch, p, "k1")) // added
        assertEquals(setOf("k1"), m.keys())

        val removed = m.toggle(ch, p, "k1") // removed
        assertNotNull(removed)
        assertEquals(emptySet<String>(), m.keys())
    }

    @Test
    fun togglePersistsAcrossInstances() {
        val ch = Channel(id = "ch1", name = "Первый", logoUrl = "", streamUrl = "https://stream/ch1")
        val p = program(now + 3_600_000, now + 7_200_000)
        marks().toggle(ch, p, "ch1|" + (now + 3_600_000) + "|" + (now + 7_200_000))

        val second = marks()
        second.restore()

        // restore переоkeyивает по MarkedProgram.key (тот же формат, что EpgRowAdapter.keyOf).
        assertEquals(setOf("ch1|" + (now + 3_600_000) + "|" + (now + 7_200_000)), second.keys())
    }

    @Test
    fun restoreDropsFinishedAndSavesPruning() {
        val prefs = AppPreferences(context)
        prefs.markedPrograms = listOf(mark(now - 10_000, now - 5_000), mark(now + 10_000, now + 20_000))

        val m = marks()
        m.restore()

        assertEquals(1, m.keys().size)
        assertEquals("ch1|" + (now + 10_000) + "|" + (now + 20_000), m.keys().first())
        assertEquals(1, AppPreferences(context).markedPrograms.size) // прунинг сохранён
    }

    @Test
    fun dueOnlyInsideNotifyWindowAndOnlyOnce() {
        val m = marks()
        m.toggle(
            Channel(id = "ch1", name = "Первый", logoUrl = "", streamUrl = "s"),
            program(now + 30_000, now + 3_600_000, "скоро"),
            "soon"
        )
        m.toggle(
            Channel(id = "ch1", name = "Первый", logoUrl = "", streamUrl = "s"),
            program(now + 600_000, now + 1_200_000, "позже"),
            "later"
        )

        val due = m.takeDue()
        assertEquals(listOf("скоро"), due.map { it.title })
        assertTrue("повторный промпт того же — нет", m.takeDue().isEmpty())
    }

    @Test
    fun dueSortedByStartTime() {
        val m = marks()
        val ch = Channel(id = "ch1", name = "Первый", logoUrl = "", streamUrl = "s")
        m.toggle(ch, program(now + 50_000, now + 3_600_000, "второй"), "b")
        m.toggle(ch, program(now + 20_000, now + 3_600_000, "первый"), "a")

        assertEquals(listOf("первый", "второй"), m.takeDue().map { it.title })
    }

    @Test
    fun consumeRemovesMarksEverywhere() {
        val m = marks()
        val ch = Channel(id = "ch1", name = "Первый", logoUrl = "", streamUrl = "s")
        m.toggle(ch, program(now + 30_000, now + 3_600_000, "скоро"), "k")
        val due = m.takeDue()

        m.consume(due)

        assertTrue(m.keys().isEmpty())
        assertTrue(AppPreferences(context).markedPrograms.isEmpty())
    }

    @Test
    fun onChangedReportsCurrentKeys() {
        val m = marks()
        val seen = mutableListOf<Set<String>>()
        m.onChanged = { seen.add(it) }
        val ch = Channel(id = "ch1", name = "Первый", logoUrl = "", streamUrl = "s")

        m.toggle(ch, program(now + 3_600_000, now + 7_200_000), "k9")

        assertEquals(listOf(setOf("k9")), seen)
    }
}
