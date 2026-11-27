package com.example.tvapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Каталог тем: разбор сохранённого id и уникальность стилей —
 * ключ к тому, что выбор темы переживает перезапуск и не путает варианты.
 */
class ThemeCatalogTest {

    @Test
    fun unknownOrMissingIdFallsBackToMidnight() {
        assertEquals(UiTheme.MIDNIGHT, UiTheme.fromId(null))
        assertEquals(UiTheme.MIDNIGHT, UiTheme.fromId(""))
        assertEquals(UiTheme.MIDNIGHT, UiTheme.fromId("avangard"))
    }

    @Test
    fun idsAreStableForPersistence() {
        // Id хранятся в SharedPreferences — смена ломает сохранённый выбор пользователей.
        assertEquals(listOf("midnight", "emerald", "sunset", "light"), UiTheme.ordered.map { it.id })
        for (theme in UiTheme.ordered) {
            assertEquals(theme, UiTheme.fromId(theme.id))
        }
    }

    @Test
    fun everyThemeResolvesToDistinctStyles() {
        val styles = UiTheme.ordered.flatMap { listOf(ThemeCatalog.styleFor(it, black = false), ThemeCatalog.styleFor(it, black = true)) }
        assertEquals("обычный и чёрный вариант не должны совпадать", 8, styles.distinct().size)
    }
}
