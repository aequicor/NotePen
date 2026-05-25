package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Юнит-тесты zoom/pan target-rect лупы из panel-координат (используется
 * Android pinch/two-finger pan жестом в [MagnifierInputPanel]).
 */
class MagnifierZoomTest {
    @Test
    fun `zoom around panel center halves target width and keeps center`() {
        val state = enabledState(left = 0.4f, top = 0.4f, width = 0.2f, height = 0.2f)
        val panel = Size(500f, 500f)
        val centerBefore =
            Offset(
                x = (state.targetRect.left + state.targetRect.right) / 2f,
                y = (state.targetRect.top + state.targetRect.bottom) / 2f,
            )
        state.zoomTargetAroundPanelFocus(
            scaleFactor = 2f,
            focusPanelLocal = Offset(panel.width / 2f, panel.height / 2f),
            panelSize = panel,
        )
        // Ширина уменьшилась вдвое (pinch out ⇒ больше зум ⇒ меньше rect).
        assertNear(0.1f, state.targetRect.right - state.targetRect.left)
        val centerAfter =
            Offset(
                x = (state.targetRect.left + state.targetRect.right) / 2f,
                y = (state.targetRect.top + state.targetRect.bottom) / 2f,
            )
        // Центр сохранён.
        assertNear(centerBefore.x, centerAfter.x)
        assertNear(centerBefore.y, centerAfter.y)
    }

    @Test
    fun `zoom out is clamped to 1`() {
        val state = enabledState(left = 0.3f, top = 0.3f, width = 0.4f, height = 0.4f)
        val panel = Size(500f, 500f)
        // scaleFactor < 1 ⇒ target должен расти; но не больше 1.
        state.zoomTargetAroundPanelFocus(
            scaleFactor = 0.1f,
            focusPanelLocal = Offset(panel.width / 2f, panel.height / 2f),
            panelSize = panel,
        )
        val w = state.targetRect.right - state.targetRect.left
        val h = state.targetRect.bottom - state.targetRect.top
        assertTrue(w <= 1f + 1e-4f, "width=$w out of bounds")
        assertTrue(h <= 1f + 1e-4f, "height=$h out of bounds")
    }

    @Test
    fun `pan by panel pixels converts to normalized delta`() {
        val state = enabledState(left = 0.4f, top = 0.4f, width = 0.2f, height = 0.2f)
        val panel = Size(500f, 500f)
        val leftBefore = state.targetRect.left
        state.panTargetByPanelPx(Offset(50f, 0f), panel)
        // 50px / 500px × 0.2 (targetW) = 0.02.
        assertNear(leftBefore + 0.02f, state.targetRect.left)
    }

    private fun enabledState(
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ): MagnifierState =
        MagnifierState().also { s ->
            s.enable(onPage = 0, viewportSize = Size(1000f, 800f))
            s.updatePageCanvasPx(widthPx = 800f, heightPx = 800f)
            s.moveTarget(
                Offset(
                    x = left - s.targetRect.left,
                    y = top - s.targetRect.top,
                ),
            )
            s.resizeTarget(newWidth = width, newHeight = height)
        }

    private fun assertNear(
        expected: Float,
        actual: Float,
        eps: Float = 1e-3f,
    ) {
        assertTrue(
            abs(expected - actual) <= eps,
            "Expected $expected, got $actual (diff > $eps)",
        )
    }
}
