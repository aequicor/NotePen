package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Юнит-тесты авто-прокрутки рамки magnifier'а (Scribble-like). */
class MagnifierStateAutoScrollTest {

    @Test
    fun `shift right moves target by ~85 percent of width with overlap`() {
        val state = enabledState(initialLeft = 0.1f, width = 0.2f)
        val shifted = state.shiftTargetForAutoscroll(AutoScrollDir.RIGHT)
        assertTrue(shifted)
        // 0.1 + 0.2*0.85 = 0.27
        assertNear(0.27f, state.targetRect.left)
        // Ширина сохранена.
        assertNear(0.2f, state.targetRect.right - state.targetRect.left)
    }

    @Test
    fun `shift right at page end wraps to next line`() {
        // Рамка у правого края — следующий шаг должен сделать line-feed.
        val state = enabledState(initialLeft = 0.85f, width = 0.2f, top = 0.4f, height = 0.1f)
        val shifted = state.shiftTargetForAutoscroll(AutoScrollDir.RIGHT)
        assertTrue(shifted)
        // Левый край = LINE_LEFT_MARGIN (0.05).
        assertNear(0.05f, state.targetRect.left)
        // Top увеличился на ~height*0.85.
        assertNear(0.485f, state.targetRect.top)
    }

    @Test
    fun `shift right at bottom-right corner stops`() {
        // Уже у правого края + почти у нижнего — line-feed выйдет за нижний край.
        val state = enabledState(initialLeft = 0.85f, width = 0.2f, top = 0.95f, height = 0.05f)
        val before = state.targetRect
        val shifted = state.shiftTargetForAutoscroll(AutoScrollDir.RIGHT)
        assertFalse(shifted)
        // Рамка не изменилась.
        assertEquals(before, state.targetRect)
    }

    @Test
    fun `enable centers panel near bottom of viewport`() {
        val state = MagnifierState()
        state.enable(onPage = 3, viewportSize = Size(1000f, 800f))
        assertTrue(state.enabled)
        assertEquals(3, state.pageIndex)
        // Панель не выходит за вьюпорт.
        assertTrue(state.panelTopLeft.x >= 0f)
        assertTrue(state.panelTopLeft.y >= 0f)
        assertTrue(state.panelTopLeft.x + state.panelSize.width <= 1000f + 0.01f)
        assertTrue(state.panelTopLeft.y + state.panelSize.height <= 800f + 0.01f)
    }

    @Test
    fun `disable resets enabled flag`() {
        val state = MagnifierState()
        state.enable(onPage = 0, viewportSize = Size(1000f, 800f))
        state.disable()
        assertFalse(state.enabled)
    }

    private fun enabledState(
        initialLeft: Float,
        width: Float,
        top: Float = 0.4f,
        height: Float = 0.05f,
    ): MagnifierState = MagnifierState().also { s ->
        s.enable(onPage = 0, viewportSize = Size(1000f, 800f))
        // Resize before moving: moveTarget clamps top against the *current*
        // frame height, so a tall default frame would cap top well above the
        // requested value. Shrink first, then position.
        s.resizeTarget(newWidth = width, newHeight = height)
        s.moveTarget(
            androidx.compose.ui.geometry.Offset(
                x = initialLeft - s.targetRect.left,
                y = top - s.targetRect.top,
            ),
        )
    }

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-3f) {
        assertTrue(
            abs(expected - actual) <= eps,
            "Expected $expected, got $actual (diff > $eps)",
        )
    }
}
