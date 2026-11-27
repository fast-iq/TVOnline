package com.example.tvapp.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Таблица решений маршрутизации DPAD-UP из сетки в шапку (чистая логика DpadFocusRouter). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DpadFocusRouterTest {

    private fun route(
        action: Int = KeyEvent.ACTION_DOWN,
        repeatCount: Int = 0,
        scrolledUp: Boolean = false,
        gridCard: Boolean = true,
        topmost: Boolean = true,
        centerX: Int = 100,
        screen: Int = 1920
    ) = DpadFocusRouter.route(action, repeatCount, scrolledUp, gridCard, topmost, centerX, screen)

    @Test
    fun upFromTopmostCardLeftHalfGoesToHistory() {
        assertEquals(HeaderFocusTarget.HISTORY, route(centerX = 100))
    }

    @Test
    fun upFromTopmostCardRightHalfGoesToSettings() {
        assertEquals(HeaderFocusTarget.SETTINGS, route(centerX = 1500))
        assertEquals(HeaderFocusTarget.SETTINGS, route(centerX = 960, screen = 1920))
    }

    @Test
    fun upActionUpIgnored() {
        assertEquals(HeaderFocusTarget.NONE, route(action = KeyEvent.ACTION_UP))
    }

    @Test
    fun keyRepeatIgnored() {
        assertEquals(HeaderFocusTarget.NONE, route(repeatCount = 1))
    }

    @Test
    fun gridStillScrollableUpIgnored() {
        assertEquals(HeaderFocusTarget.NONE, route(scrolledUp = true))
    }

    @Test
    fun focusOutsideGridCardIgnored() {
        assertEquals(HeaderFocusTarget.NONE, route(gridCard = false))
    }

    @Test
    fun notTopmostCardIgnored() {
        assertEquals(HeaderFocusTarget.NONE, route(topmost = false))
    }
}
