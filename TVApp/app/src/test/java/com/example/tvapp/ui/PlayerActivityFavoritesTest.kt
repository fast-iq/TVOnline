package com.example.tvapp.ui

import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.Channel
import com.example.tvapp.data.LimeHdClient
import com.example.tvapp.player.TVPlayerManager
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

/**
 * Плеерные фичи без живого ExoPlayer: избранное по ★ прямо из просмотра и
 * интент тапа по системному плееру (media session).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerActivityFavoritesTest {

    private var previousBaseUrl = ""

    @Before
    fun setUp() {
        previousBaseUrl = LimeHdClient.baseUrl
        LimeHdClient.baseUrl = "http://127.0.0.1:9/"
    }

    @After
    fun cleanUp() {
        LimeHdClient.baseUrl = previousBaseUrl
    }

    /** onCreate не нужен: toggle работает только с preferences. */
    private fun activity(): PlayerActivity = Robolectric.buildActivity(PlayerActivity::class.java).get()

    private fun channel(id: String) = Channel(id = id, name = "Канал $id", logoUrl = "", streamUrl = "https://example.invalid/$id.m3u8")

    @Test
    fun starKeyTogglesFavoriteBothWays() {
        val activity = activity()
        val prefs = AppPreferences(RuntimeEnvironment.getApplication())
        prefs.favoriteChannelIds = emptySet()
        val ch = channel("fav_toggle_channel")

        assertTrue(activity.toggleFavoriteChannel(ch))
        assertEquals(setOf(ch.id), prefs.favoriteChannelIds)

        assertFalse(activity.toggleFavoriteChannel(ch))
        assertEquals(emptySet<String>(), prefs.favoriteChannelIds)
    }

    @Test
    fun togglingOneChannelKeepsOthers() {
        val activity = activity()
        val prefs = AppPreferences(RuntimeEnvironment.getApplication())
        val first = channel("fav_first")
        val second = channel("fav_second")
        prefs.favoriteChannelIds = setOf(first.id)

        assertTrue(activity.toggleFavoriteChannel(second))

        assertEquals(setOf(first.id, second.id), prefs.favoriteChannelIds)
    }

    @Test
    fun sessionActivityIntentTargetsMainActivity() {
        val activity = activity()

        val pending = TVPlayerManager(activity).sessionActivityIntent(activity)

        val saved = shadowOf(pending).savedIntent
        assertNotNull(saved)
        assertEquals("com.example.tvapp.ui.MainActivity", saved.component?.className)
    }
}
