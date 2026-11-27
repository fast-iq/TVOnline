package com.example.tvapp.ui

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelCacheCodec
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.LimeHdClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * «Продолжить просмотр»: на холодном старте фокус сетки сам становится
 * на последний смотревший канал (из prefs.lastChannelId + дискового кэша).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class MainActivityResumeTest {

    private val channels: List<Channel> = (1..6).map { i ->
        Channel(
            id = "fed_$i",
            name = "Федеральный $i",
            logoUrl = "",
            streamUrl = "https://example.invalid/fed$i.m3u8",
            category = "federal",
            number = i
        )
    } + (7..12).map { i ->
        Channel(
            id = "gen_$i",
            name = "Местный $i",
            logoUrl = "",
            streamUrl = "https://example.invalid/gen$i.m3u8",
            category = "general",
            number = i
        )
    }

    private lateinit var context: Context
    private var previousBaseUrl = ""

    @Before
    fun seedDiskCache() {
        context = RuntimeEnvironment.getApplication()
        previousBaseUrl = LimeHdClient.baseUrl
        LimeHdClient.baseUrl = "http://127.0.0.1:9/"
        AppPreferences(context).lastUpdateCheckMs = System.currentTimeMillis()
        File(context.filesDir, "channels_cache.json")
            .writeText(ChannelCacheCodec.serialize(channels))
    }

    @After
    fun cleanUp() {
        LimeHdClient.baseUrl = previousBaseUrl
        File(context.filesDir, "channels_cache.json").delete()
        ChannelList.updateChannels(emptyList())
    }

    private fun launch(): Pair<MainActivity, RecyclerView> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val grid = activity.findViewById<RecyclerView>(R.id.channelsRecyclerView)
        repeat(40) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        val width = activity.resources.displayMetrics.widthPixels
        val height = activity.resources.displayMetrics.heightPixels
        grid.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        grid.layout(0, 0, width, height)
        shadowOf(Looper.getMainLooper()).idle()
        return activity to grid
    }

    @Test
    fun coldStartFocusesLastWatchedChannel() {
        AppPreferences(context).lastChannelId = "gen_9"

        val (_, grid) = launch()

        // ALL-режим: header(0), fed_1..6 (1..6), header(7), gen_7(8), gen_8(9), gen_9(10).
        val expected = 10
        val focused = (0 until grid.adapter!!.itemCount).firstOrNull {
            grid.findViewHolderForAdapterPosition(it)?.itemView?.hasFocus() == true
        }
        assertNotNull("после старта ни одна карточка не в фокусе", focused)
        assertEquals("фокус не на последнем смотревшем канале", expected, focused)
    }
}
