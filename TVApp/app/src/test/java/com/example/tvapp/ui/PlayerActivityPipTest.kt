package com.example.tvapp.ui

import android.content.pm.PackageManager
import android.view.KeyEvent
import com.example.tvapp.data.LimeHdClient
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Картинка-в-картинке плеера: какие клавиши подавляются в PiP-окне и что
 * происходит при уходе домой без запущенного воспроизведения.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerActivityPipTest {

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

    /** onCreate не вызываем: живая инициализация ExoPlayer здесь не нужна. */
    private fun activity(): PlayerActivity = Robolectric.buildActivity(PlayerActivity::class.java).get()

    @Test
    fun pipBlocksInvisibleUiKeysOnlyInPip() {
        val activity = activity()

        for (key in listOf(
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_CAPTIONS,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN
        )) {
            assertTrue("в PiP должна подавляться $key", activity.pipBlocksKey(key, inPip = true))
            assertFalse("вне PiP клавиша $key не блокируется", activity.pipBlocksKey(key, inPip = false))
        }
    }

    @Test
    fun playbackKeysStayLiveInPip() {
        val activity = activity()

        // Переключение каналов, плей/пауза и цифры работают и в плавающем окне.
        // BACK тоже жив: с пульта это «стоп» — закрывает PiP и останавливает показ.
        for (key in listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_5
        )) {
            assertFalse("в PiP не должна подавляться $key", activity.pipBlocksKey(key, inPip = true))
        }
    }

    @Test
    fun goingHomeWithoutPlaybackDoesNotEnterPip() {
        val activity = activity()
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)

        // Ни канала, ни onCreate — нажатие HOME не должно ни падать, ни звать в PiP.
        activity.userLeaveHint()

        assertFalse(activity.isInPictureInPictureMode)
    }
}
