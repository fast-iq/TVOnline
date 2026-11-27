package com.example.tvapp.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import androidx.appcompat.content.res.AppCompatResources
import com.example.tvapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Пины палитр: каждая тема обязана держать читаемый контраст текст/фон и
 * различимый акцент, а «строка с каналом» (bg_pill) — краситься поверхностью
 * темы, а не захардкоженным hex'ом. Ловит молчаливый переезд палитр.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePaletteContrastTest {

    private fun themed(theme: UiTheme, black: Boolean): Context =
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), ThemeCatalog.styleFor(theme, black))

    private fun contrast(fg: Int, bg: Int): Double {
        val l1 = luminance(fg)
        val l2 = luminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(color: Int): Double {
        val r = channel(color, 16)
        val g = channel(color, 8)
        val b = channel(color, 0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun channel(color: Int, shift: Int): Double {
        val s = (color shr shift and 0xFF) / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    @Test
    fun everyThemeKeepsTextAndAccentContrast() {
        for (theme in UiTheme.ordered) {
            val ctx = themed(theme, black = false)
            val surface = ctx.themeColor(R.attr.tvSurface)
            val primary = ctx.themeColor(R.attr.tvTextPrimary)
            val secondary = ctx.themeColor(R.attr.tvTextSecondary)
            val accent = ctx.themeColor(R.attr.tvAccent)

            assertTrue("${theme.id}: текст на поверхности", contrast(primary, surface) >= 4.5)
            assertTrue("${theme.id}: вторичный текст", contrast(secondary, surface) >= 3.0)
            assertTrue("${theme.id}: акцент на поверхности", contrast(accent, surface) >= 3.0)
        }
    }

    @Test
    fun blackStageKeepsLightTextOnBlack() {
        for (theme in UiTheme.ordered) {
            val ctx = themed(theme, black = true)
            val primary = ctx.themeColor(R.attr.tvTextPrimary)
            assertTrue("${theme.id}: светлый текст на чёрной сцене", contrast(primary, Color.BLACK) >= 7.0)
        }
    }

    @Test
    fun darkAndLightThemesAreActuallyDistinct() {
        val lightSurface = luminance(themed(UiTheme.LIGHT, false).themeColor(R.attr.tvSurface))
        val darkSurface = luminance(themed(UiTheme.MIDNIGHT, false).themeColor(R.attr.tvSurface))
        assertTrue("светлая тема должна быть светлой", lightSurface > 0.7)
        assertTrue("полночь должна быть тёмной", darkSurface < 0.15)

        val accents = UiTheme.ordered.map { themed(it, false).themeColor(R.attr.tvAccent) }
        assertEquals("акценты тем должны различаться", accents.size, accents.distinct().size)
    }

    @Test
    fun channelPillPaintsSurfaceOfCurrentTheme() {
        for (theme in UiTheme.ordered) {
            val ctx = themed(theme, black = false)
            val pill = AppCompatResources.getDrawable(ctx, R.drawable.bg_pill) as GradientDrawable
            // Kotlin в GradientDrawable bind'ит `color` к getColorStateList() — берём default-состояние.
            assertEquals("${theme.id}: пилюля строки канала красится поверхностью темы", ctx.themeColor(R.attr.tvSurface), pill.color!!.defaultColor)
        }
    }
}
