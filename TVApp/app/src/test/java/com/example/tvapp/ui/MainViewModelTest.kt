package com.example.tvapp.ui

import android.os.Looper
import com.example.tvapp.data.Channel
import com.example.tvapp.data.ChannelCacheCodec
import com.example.tvapp.data.ChannelList
import com.example.tvapp.data.LimeHdClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * ViewModel-загрузка каналов: сеть заглушена мёртвым портом (LimeHdClient при этом
 * возвращает пустой список, не бросая), поэтому диск-снимок — основной проверяемый
 * путь, ровно тот, что даёт мгновенный старт сетки на реальном устройстве.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelTest {

    private lateinit var cacheFile: File
    private var previousBaseUrl: String = ""

    private fun channels(): List<Channel> = (1..4).map {
        Channel(id = "c$it", name = "Канал $it", logoUrl = "", streamUrl = "https://example.invalid/$it.m3u8")
    }

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        cacheFile = File(app.filesDir, "channels_cache.json")
        previousBaseUrl = LimeHdClient.baseUrl
        LimeHdClient.baseUrl = "http://127.0.0.1:9/"
        ChannelList.updateChannels(emptyList())
    }

    @After
    fun tearDown() {
        LimeHdClient.baseUrl = previousBaseUrl
        cacheFile.delete()
        ChannelList.updateChannels(emptyList())
    }

    private fun createViewModel(): MainViewModel = MainViewModel(RuntimeEnvironment.getApplication())

    /** Крутим main-петлю (IO-резюме приходят через main), пока предикат не сработает. */
    private fun awaitState(vm: MainViewModel, timeoutMs: Long = 3_000, predicate: (MainUiState) -> Boolean): MainUiState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (predicate(vm.state.value)) return vm.state.value
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return vm.state.value
    }

    @Test
    fun diskSnapshotPublishesAndSyncsChannelList() {
        cacheFile.writeText(ChannelCacheCodec.serialize(channels()))

        val vm = createViewModel()
        val state = awaitState(vm) { it.channels.isNotEmpty() }

        assertEquals(4, state.channels.size)
        assertEquals("ChannelList синхронизирован", state.channels, ChannelList.channels)
    }

    @Test
    fun deadNetworkKeepsDiskList() {
        cacheFile.writeText(ChannelCacheCodec.serialize(channels()))

        val vm = createViewModel()
        awaitState(vm) { it.channels.isNotEmpty() }
        // Сетевой фаз нужно завершиться (пустой ответ не должен трогать список).
        val state = awaitState(vm, timeoutMs = 4_000) { it.channels.isNotEmpty() }

        assertEquals(4, state.channels.size)
        assertEquals(ChannelList.channels, state.channels)
    }

    @Test
    fun emptyDiskAndDeadNetworkYieldEmptyState() {
        val vm = createViewModel()

        // Нечему публиковаться: состояние остаётся пустым даже после фаз сети.
        val state = awaitState(vm, timeoutMs = 1_500) { false }

        assertTrue(state.channels.isEmpty())
    }

    @Test
    fun refreshKeepsCurrentList() {
        cacheFile.writeText(ChannelCacheCodec.serialize(channels()))
        val vm = createViewModel()
        awaitState(vm) { it.channels.isNotEmpty() }

        vm.refresh()
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(4, vm.state.value.channels.size)
    }
}
