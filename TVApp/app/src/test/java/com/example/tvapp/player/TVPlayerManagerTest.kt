package com.example.tvapp.player

import android.content.Context
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.LimeHdClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Логика выбора потока в TVPlayerManager без живого ExoPlayer: состав и дедупликация
 * URL-кандидатов, индекс текущего источника, сохранение последнего канала,
 * безопасные no-op'ы до initializePlayer/после releasePlayer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TVPlayerManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: AppPreferences
    private lateinit var manager: TVPlayerManager
    private var previousBaseUrl = ""

    private val channel = Channel(
        id = "limehd_10",
        name = "Первый",
        logoUrl = "",
        streamUrl = "https://primary/1.m3u8",
        fallbackStreamUrls = listOf("https://alt/1.m3u8", "https://cdn/1.m3u8")
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        previousBaseUrl = LimeHdClient.baseUrl
        LimeHdClient.baseUrl = "http://127.0.0.1:9/"
        prefs = AppPreferences(context)
        manager = TVPlayerManager(context)
    }

    @After
    fun tearDown() {
        LimeHdClient.baseUrl = previousBaseUrl
    }

    @Test
    fun noCurrentUrlsBeforeFirstPlay() {
        assertEquals(emptyList<String>(), manager.getCurrentStreamUrls())
        assertEquals(0, manager.getCurrentUrlIndex())
    }

    @Test
    fun playChannelBuildsCandidateListAndPersistsChannel() {
        manager.playChannel(channel)

        assertEquals(
            listOf("https://primary/1.m3u8", "https://alt/1.m3u8", "https://cdn/1.m3u8"),
            manager.getCurrentStreamUrls()
        )
        assertEquals(0, manager.getCurrentUrlIndex())
        assertEquals("limehd_10", prefs.lastChannelId)
    }

    @Test
    fun duplicateUrlsAreRemovedFromCandidates() {
        val duplicated = channel.copy(fallbackStreamUrls = listOf("https://primary/1.m3u8", "https://alt/1.m3u8"))

        manager.playChannel(duplicated)

        assertEquals(listOf("https://primary/1.m3u8", "https://alt/1.m3u8"), manager.getCurrentStreamUrls())
    }

    @Test
    fun playingExplicitFallbackSetsItsIndex() {
        manager.playChannel(channel, "https://cdn/1.m3u8")

        assertEquals(2, manager.getCurrentUrlIndex())
    }

    @Test
    fun switchToUrlRemembersIndexAndIgnoresUnknownUrl() {
        manager.playChannel(channel)
        manager.switchToUrl("https://alt/1.m3u8")
        assertEquals(1, manager.getCurrentUrlIndex())

        // До выбора канала переключение безопасно no-op.
        val fresh = TVPlayerManager(context)
        fresh.switchToUrl("https://any")
        assertEquals(0, fresh.getCurrentUrlIndex())
    }

    @Test
    fun playerAccessorsAreSafeWithoutInitialize() {
        manager.playChannel(channel)
        manager.pause()
        manager.resume()
        manager.seekTo(1000L)

        assertEquals(0L, manager.getCurrentPosition())
        assertEquals(0L, manager.getDuration())
        assertFalse(manager.isPlaying())
    }

    @Test
    fun releaseWithoutInitializeIsNoOp() {
        manager.releasePlayer()

        manager.playChannel(channel)
        assertEquals(3, manager.getCurrentStreamUrls().size)
        assertEquals("limehd_10", prefs.lastChannelId)
    }

    @Test
    fun setQualityModePersistsPreference() {
        manager.setQualityMode(AppPreferences.QualityMode.MINIMUM)

        assertEquals(AppPreferences.QualityMode.MINIMUM, prefs.qualityMode)
    }
}
