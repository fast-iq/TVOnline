package com.example.tvapp.ui

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.LimeHdClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * Офлайн-статус главного экрана: баннер «нет сети» и подсказка в пустом состоянии.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityNetworkBannerTest {

    private lateinit var context: Context
    private var previousBaseUrl = ""

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        previousBaseUrl = LimeHdClient.baseUrl
        LimeHdClient.baseUrl = "http://127.0.0.1:9/"
        AppPreferences(context).lastUpdateCheckMs = System.currentTimeMillis()
        File(context.filesDir, "channels_cache.json").delete()
        ChannelList.updateChannels(emptyList())
    }

    @After
    fun cleanUp() {
        LimeHdClient.baseUrl = previousBaseUrl
        ChannelList.updateChannels(emptyList())
    }

    private fun launch(): MainActivity {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        return activity
    }

    @Test
    fun offlineShowsBannerAndEmptyHint() {
        val activity = launch()

        activity.setNetworkOnline(false)

        val banner = activity.findViewById<TextView>(R.id.networkBanner)
        assertEquals(View.VISIBLE, banner.visibility)
        // Язык экрана задаёт BaseActivity — ожидаемые строки берём из активности.
        assertEquals(activity.getString(R.string.network_offline_banner), banner.text.toString())

        val empty = activity.findViewById<TextView>(R.id.emptyText)
        assertEquals(View.VISIBLE, empty.visibility)
        assertEquals(activity.getString(R.string.network_offline_empty), empty.text.toString())
    }

    @Test
    fun comingBackOnlineHidesBanner() {
        val activity = launch()
        activity.setNetworkOnline(false)

        activity.setNetworkOnline(true)

        val banner = activity.findViewById<TextView>(R.id.networkBanner)
        assertEquals(View.GONE, banner.visibility)
        // Список всё ещё пустой и мы «онлайн» — обычная подсказка загрузки, не офлайн-текст.
        val empty = activity.findViewById<TextView>(R.id.emptyText)
        assertTrue(empty.text.toString() != activity.getString(R.string.network_offline_empty))
    }
}
