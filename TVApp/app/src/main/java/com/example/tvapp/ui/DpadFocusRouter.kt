package com.example.tvapp.ui

import android.view.KeyEvent

/** Куда DPAD-UP из сетки каналов отправляет фокус. */
enum class HeaderFocusTarget { HISTORY, SETTINGS, NONE }

/**
 * Чистое решение о переходе фокуса DPAD-UP из сетки каналов к кнопкам шапки.
 * Переход нужен, потому что геометрический поиск фокуса на ТВ не выпускает UP из
 * RecyclerView (LayoutManager ест UP как прокрутку; от левых карточек до правых
 * кнопок слишком длинный диагональный прыжок).
 */
object DpadFocusRouter {

    /**
     * @param action KeyEvent.action
     * @param repeatCount KeyEvent.repeatCount — удержание кнопки не должно «выстреливать» несколько раз
     * @param gridScrolledUp сетка ещё может прокручиваться вверх (canScrollVertically(-1))
     * @param focusIsGridCard фокус на карточке канала внутри сетки
     * @param focusIsTopmostCard выше фокуса в разложенной сетке нет карточек
     * @param focusCenterX экранный X центра фокуса
     * @param screenWidth ширина экрана в пикселях
     */
    fun route(
        action: Int,
        repeatCount: Int,
        gridScrolledUp: Boolean,
        focusIsGridCard: Boolean,
        focusIsTopmostCard: Boolean,
        focusCenterX: Int,
        screenWidth: Int
    ): HeaderFocusTarget {
        if (action != KeyEvent.ACTION_DOWN) return HeaderFocusTarget.NONE
        if (repeatCount != 0) return HeaderFocusTarget.NONE
        if (gridScrolledUp || !focusIsGridCard || !focusIsTopmostCard) return HeaderFocusTarget.NONE
        return if (focusCenterX * 2 < screenWidth) HeaderFocusTarget.HISTORY else HeaderFocusTarget.SETTINGS
    }
}
