package com.example.tvapp.ui

import android.content.Intent
import android.os.Looper
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.R
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Смоук-тесты поиска: запуск активности, фильтрация, пустое состояние, переход на канал. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchActivitySmokeTest {

    private val channels = listOf(
        Channel(id = "c1", name = "Первый канал", logoUrl = "", streamUrl = "https://example/1.m3u8", number = 1),
        Channel(id = "c2", name = "Матч ТВ", logoUrl = "", streamUrl = "https://example/2.m3u8", number = 2),
        Channel(id = "c3", name = "Пятница", logoUrl = "", streamUrl = "https://example/3.m3u8", number = 3)
    )

    @Before
    fun seedChannels() {
        ChannelList.updateChannels(channels)
    }

    @After
    fun clearChannels() {
        ChannelList.updateChannels(emptyList())
    }

    private fun launch(): Pair<android.app.Activity, EditText> {
        val activity = Robolectric.buildActivity(SearchActivity::class.java).setup().get()
        val input = activity.findViewById<EditText>(R.id.searchInput)
        shadowOf(Looper.getMainLooper()).idle()
        return activity to input
    }

    @Test
    fun activityLaunchesWithAllChannels() {
        val (activity, input) = launch()
        assertNotNull(input)
        val list = activity.findViewById<RecyclerView>(R.id.searchResultsList)
        assertEquals(channels.size, list.adapter?.itemCount)
    }

    @Test
    fun typingFiltersResults() {
        val (activity, input) = launch()
        input.setText("пят")
        shadowOf(Looper.getMainLooper()).idle()
        val list = activity.findViewById<RecyclerView>(R.id.searchResultsList)
        assertEquals(1, list.adapter?.itemCount)
    }

    @Test
    fun emptyQueryWithNoMatchesKeepsAllResults() {
        val (activity, input) = launch()
        input.setText("нет такого канала")
        shadowOf(Looper.getMainLooper()).idle()
        val list = activity.findViewById<RecyclerView>(R.id.searchResultsList)
        assertEquals(0, list.adapter?.itemCount)
        val emptyText = activity.findViewById<android.widget.TextView>(R.id.searchEmptyText)
        assertEquals(android.view.View.VISIBLE, emptyText.visibility)
    }

    @Test
    fun itemClickOpensPlayerAndRecordsHistory() {
        val (activity, input) = launch()
        input.setText("матч")
        shadowOf(Looper.getMainLooper()).idle()
        val list = activity.findViewById<RecyclerView>(R.id.searchResultsList)
        val holder = list.findViewHolderForAdapterPosition(0)
        assertNotNull(holder)
        holder!!.itemView.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(PlayerActivity::class.java.name, started.component?.className)
        assertEquals("c2", started.getStringExtra("channel_id"))
        val prefs = com.example.tvapp.data.AppPreferences(activity.applicationContext)
        assertTrue(prefs.watchHistory.contains("c2"))
    }

    @Test
    fun backClearsQueryInsteadOfFinishing() {
        val (activity, input) = launch()
        input.setText("тел")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            activity.onKeyDown(
                android.view.KeyEvent.KEYCODE_BACK,
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK)
            )
        )
        assertEquals("", input.text.toString())
    }

    @Test
    fun playerIntentCarriesStreamExtras() {
        val (activity, _) = launch()
        val intent = Intent(activity, PlayerActivity::class.java).apply {
            putExtra("channel_id", "c1")
            putExtra("stream_url", "https://example/1.m3u8")
        }
        activity.startActivity(intent)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals("https://example/1.m3u8", started?.getStringExtra("stream_url"))
    }
}
