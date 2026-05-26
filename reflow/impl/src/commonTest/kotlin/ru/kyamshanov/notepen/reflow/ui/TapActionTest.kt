package ru.kyamshanov.notepen.reflow.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class TapActionTest {
    @Test
    fun leftZone_isPrev() {
        // Левые 30% ширины — предыдущая страница (x строго меньше 0.3*width).
        assertEquals(TapAction.PREV, tapAction(x = 0f, width = WIDTH, tapToTurn = true))
        assertEquals(TapAction.PREV, tapAction(x = 299f, width = WIDTH, tapToTurn = true))
    }

    @Test
    fun rightZone_isNext() {
        // Правые 30% ширины — следующая страница (x строго больше 0.7*width).
        assertEquals(TapAction.NEXT, tapAction(x = 701f, width = WIDTH, tapToTurn = true))
        assertEquals(TapAction.NEXT, tapAction(x = WIDTH.toFloat(), width = WIDTH, tapToTurn = true))
    }

    @Test
    fun centerZone_togglesBar() {
        // Центр (и границы зон) — показать/скрыть панель, не листать.
        assertEquals(TapAction.TOGGLE_BAR, tapAction(x = 300f, width = WIDTH, tapToTurn = true))
        assertEquals(TapAction.TOGGLE_BAR, tapAction(x = 500f, width = WIDTH, tapToTurn = true))
        assertEquals(TapAction.TOGGLE_BAR, tapAction(x = 700f, width = WIDTH, tapToTurn = true))
    }

    @Test
    fun tapToTurnDisabled_alwaysTogglesBar() {
        // Выключенные тап-зоны: любой тап лишь тогглит панель (никакого листания).
        assertEquals(TapAction.TOGGLE_BAR, tapAction(x = 0f, width = WIDTH, tapToTurn = false))
        assertEquals(TapAction.TOGGLE_BAR, tapAction(x = WIDTH.toFloat(), width = WIDTH, tapToTurn = false))
    }

    @Test
    fun nonPositiveWidth_togglesBar() {
        // До замера ширины (0) зоны не считаем — тап тогглит панель.
        assertEquals(TapAction.TOGGLE_BAR, tapAction(x = 10f, width = 0, tapToTurn = true))
    }

    private companion object {
        const val WIDTH = 1000
    }
}
