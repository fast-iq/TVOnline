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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * Регрессия D-pad-навигации главного экрана (баг v1.0.15: «вверх» не уходил из сетки).
 *
 * Робоlectric не поднимает ViewRootImpl, поэтому activity.currentFocus/разыгрывание
 * клавиш здесь невозможны — вместо этого проверяются «концы» цепочки:
 * 1) чистое решение [DpadFocusRouter] покрыто [DpadFocusRouterTest];
 * 2) здесь — реальные входы решения на разложенной сетке (верхний ряд, половины экрана)
 *    и разводка nextFocusDownId кнопок шапки обратно в сетку.
 * Сеть заглушена несуществующим портом ([LimeHdClient.baseUrl]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class MainActivityDpadTest {

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
    private var previousBaseUrl: String = ""

    @Before
    fun seedDiskCache() {
        context = RuntimeEnvironment.getApplication()
        previousBaseUrl = LimeHdClient.baseUrl
        LimeHdClient.baseUrl = "http://127.0.0.1:9/"
        // Пропускаем проверку обновлений — она показала бы диалог поверх теста.
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

    private fun launchLaidOutGrid(): Pair<MainActivity, RecyclerView> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val grid = activity.findViewById<RecyclerView>(R.id.channelsRecyclerView)
        // Дисковая загрузка и фоновое обновление идут на потоках Dispatchers.IO — ждём их конца.
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
    fun gridPopulatedFromDiskWithCategoryHeaders() {
        val (activity, grid) = launchLaidOutGrid()
        assertNotNull(grid.adapter)
        // 12 каналов + 2 заголовка категорий (режим ALL).
        assertEquals(
            "actual=${grid.adapter?.itemCount}",
            channels.size + 2,
            grid.adapter?.itemCount ?: -1
        )
    }

    @Test
    fun firstCardIsTopmostAndLeftHalf() {
        val (activity, grid) = launchLaidOutGrid()
        // Позиция 0 — заголовок категории, позиция 1 — первая карточка (левый край верхнего ряда).
        val firstCard = grid.findViewHolderForAdapterPosition(1)?.itemView
        assertNotNull("карточка не разложена", firstCard)
        assertTrue("первая карточка не считается верхней", activity.isTopmostCard(firstCard!!))
        val width = activity.resources.displayMetrics.widthPixels
        assertTrue(
            "левая карточка не в левой половине: center=${activity.screenCenterX(firstCard)} width=$width",
            activity.screenCenterX(firstCard) * 2 < width
        )
    }

    @Test
    fun secondRowCardIsNotTopmost() {
        val (activity, grid) = launchLaidOutGrid()
        // Позиция 6 — шестой канал, вторая строка (ряд по 5 карточек).
        val secondRow = grid.findViewHolderForAdapterPosition(6)?.itemView
        assertNotNull(secondRow)
        assertFalse("карточка второй строки не должна считаться верхней", activity.isTopmostCard(secondRow!!))
    }

    @Test
    fun lastColumnCardIsRightHalf() {
        val (activity, grid) = launchLaidOutGrid()
        // Пятая колонка верхнего ряда (позиция 5) должна попадать в правую половину экрана,
        // откуда UP ведёт к кнопке настроек.
        val rightCard = grid.findViewHolderForAdapterPosition(5)?.itemView
        assertNotNull(rightCard)
        val width = activity.resources.displayMetrics.widthPixels
        assertTrue(
            "правая карточка не в правой половине: center=${activity.screenCenterX(rightCard!!)} width=$width",
            activity.screenCenterX(rightCard) * 2 >= width
        )
    }

    @Test
    fun headerButtonsRouteDownBackToGrid() {
        val (activity, grid) = launchLaidOutGrid()
        val ids = listOf(
            R.id.settingsButton,
            R.id.epgButton,
            R.id.searchButton,
            R.id.favoritesButton,
            R.id.historyButton
        )
        for (id in ids) {
            val button = activity.findViewById<View>(id)
            assertTrue("кнопка не focusable: $id", button.isFocusable)
            assertEquals(
                "nextFocusDown не ведёт в сетку: $id",
                grid.id,
                button.nextFocusDownId
            )
        }
    }
}
