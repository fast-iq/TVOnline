package com.example.tvapp.ui

import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.data.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Сетка каналов обновляется через DiffUtil: одинаковый список не даёт уведомлений,
 * переименование — точечный change, перестановка — move, вставка/удаление канала —
 * вставку/удаление, а не перевыпуск всей сетки (иначе теряется фокус и мигают логотипы).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChannelAdapterDiffTest {

    private class Recorder : RecyclerView.AdapterDataObserver() {
        val events = mutableListOf<String>()

        override fun onChanged() {
            events += "changed-all"
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            events += "changed($positionStart,$itemCount)"
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            events += "inserted($positionStart,$itemCount)"
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            events += "removed($positionStart,$itemCount)"
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            events += "moved($fromPosition,$toPosition,$itemCount)"
        }
    }

    private fun channel(id: String, name: String = "Канал $id", number: Int = 1): Channel = Channel(
        id = id,
        name = name,
        logoUrl = "",
        streamUrl = "https://example.invalid/$id.m3u8",
        category = "federal",
        number = number
    )

    private fun cards(vararg ids: String, name: (String) -> String = { it }): List<GridItem> = ids.map { GridItem.Card(channel(it, name(it))) }

    private fun adapterWith(items: List<GridItem>): Pair<ChannelAdapter, Recorder> {
        val adapter = ChannelAdapter(items = items, onChannelSelected = {})
        val recorder = Recorder()
        adapter.registerAdapterDataObserver(recorder)
        return adapter to recorder
    }

    @Test
    fun identicalUpdateDispatchesNothing() {
        val items = cards("a", "b", "c")
        val (adapter, recorder) = adapterWith(items)

        adapter.updateItems(cards("a", "b", "c"))

        assertEquals(emptyList<String>(), recorder.events)
        assertEquals(3, adapter.itemCount)
    }

    @Test
    fun renamedChannelDispatchesSingleChangeNotFullInvalidation() {
        val (adapter, recorder) = adapterWith(cards("a", "b", "c"))

        adapter.updateItems(cards("a", "b", "c", name = { if (it == "b") "Переименован" else it }))

        assertTrue(
            "ожидался точечный change, получено: ${recorder.events}",
            recorder.events.any { it.startsWith("changed(1") } &&
                !recorder.events.contains("changed-all")
        )
    }

    @Test
    fun insertedAndRemovedChannelsDispatchRanges() {
        val (adapter, recorder) = adapterWith(cards("a", "b", "c"))

        adapter.updateItems(cards("a", "x", "c"))

        assertTrue(
            "ожидались вставка и удаление одного канала, получено: ${recorder.events}",
            recorder.events.any { it.startsWith("inserted") && it.endsWith(",1)") } &&
                recorder.events.any { it.startsWith("removed") && it.endsWith(",1)") } &&
                !recorder.events.contains("changed-all")
        )
        assertEquals(3, adapter.itemCount)
    }

    @Test
    fun movedChannelDispatchesMove() {
        val (adapter, recorder) = adapterWith(cards("a", "b", "c"))

        adapter.updateItems(cards("c", "a", "b"))

        assertTrue(
            "ожидался move без перевыпуска, получено: ${recorder.events}",
            recorder.events.any { it.startsWith("moved") } &&
                !recorder.events.contains("changed-all") &&
                !recorder.events.any { it.startsWith("inserted") || it.startsWith("removed") }
        )
    }

    @Test
    fun headerIdentityByCategoryContentByTitle() {
        val header = listOf(GridItem.Header("federal", "Федеральные"))
        val (adapter, recorder) = adapterWith(header)

        adapter.updateItems(listOf(GridItem.Header("federal", "Федеральные каналы")))

        assertTrue(
            "смена заголовка должна дать точечный change: ${recorder.events}",
            recorder.events.any { it.startsWith("changed(0") } && !recorder.events.contains("changed-all")
        )
    }

    @Test
    fun sameChannelInGridWithNewHeaderKeepsCardIdentity() {
        val before = listOf<GridItem>(GridItem.Header("federal", "Ф")) + cards("a", "b") +
            GridItem.Header("sport", "С") + cards("s1")
        val (adapter, recorder) = adapterWith(before)

        // Канал b переехал в другую категорию — карточка того же id должна быть перемещена,
        // а не пересоздана; добавившийся заголовок — вставка.
        val after = listOf<GridItem>(GridItem.Header("federal", "Ф")) + cards("a") +
            GridItem.Header("sport", "С") + cards("s1", "b")

        adapter.updateItems(after)

        assertTrue(
            "не должно быть полного перевыпуска: ${recorder.events}",
            !recorder.events.contains("changed-all")
        )
        assertEquals(5, adapter.itemCount)
    }
}
