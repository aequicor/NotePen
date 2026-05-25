package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Юнит-тесты чистой координатной математики magnifier'а. */
class MagnifierGeometryTest {
    private val panel = Size(400f, 200f)
    private val target = Rect(0.2f, 0.4f, 0.6f, 0.6f) // width = 0.4, height = 0.2

    @Test
    fun `panel center maps to target center in page-normalized space`() {
        val page = panelLocalToPageNormalized(Offset(200f, 100f), panel, target)
        assertNear(0.4f, page.x)
        assertNear(0.5f, page.y)
    }

    @Test
    fun `panel top-left maps to target top-left`() {
        val page = panelLocalToPageNormalized(Offset.Zero, panel, target)
        assertNear(target.left, page.x)
        assertNear(target.top, page.y)
    }

    @Test
    fun `panel bottom-right maps to target bottom-right`() {
        val page = panelLocalToPageNormalized(Offset(panel.width, panel.height), panel, target)
        assertNear(target.right, page.x)
        assertNear(target.bottom, page.y)
    }

    @Test
    fun `round-trip identity`() {
        val original = Offset(0.35f, 0.55f)
        val panelLocal = pageNormalizedToPanelLocal(original, panel, target)
        val back = panelLocalToPageNormalized(panelLocal, panel, target)
        assertNear(original.x, back.x)
        assertNear(original.y, back.y)
    }

    @Test
    fun `zoomFactor is panelWidth divided by visible target width`() {
        // panel.width = 400, target.width = 0.4 of pageCanvasWidthPx = 1000 → 400 / 400 = 1
        val z = zoomFactor(panel, target, pageCanvasWidthPx = 1000f)
        assertNear(1f, z)
        // If page is 200px wide, target width in page = 80, panel = 400 → 5×
        val z2 = zoomFactor(panel, target, pageCanvasWidthPx = 200f)
        assertNear(5f, z2)
    }

    @Test
    fun `clampTargetToPage keeps in-range rect intact`() {
        val r = Rect(0.1f, 0.2f, 0.3f, 0.4f)
        assertEquals(r, clampTargetToPage(r))
    }

    @Test
    fun `clampTargetToPage shifts overflowing rect inside page`() {
        val r = Rect(0.9f, 0.95f, 1.2f, 1.3f) // width 0.3, height 0.35; overflow
        val clamped = clampTargetToPage(r)
        assertTrue(clamped.left >= 0f && clamped.top >= 0f)
        assertTrue(clamped.right <= 1f && clamped.bottom <= 1f)
        // Размер сохраняется.
        assertNear(0.3f, clamped.right - clamped.left)
        assertNear(0.35f, clamped.bottom - clamped.top)
    }

    @Test
    fun `zero panel yields zero offset (defensive)`() {
        val out = panelLocalToPageNormalized(Offset(10f, 10f), Size.Zero, target)
        assertEquals(Offset.Zero, out)
    }

    private fun assertNear(
        expected: Float,
        actual: Float,
        eps: Float = 1e-4f,
    ) {
        assertTrue(
            abs(expected - actual) <= eps,
            "Expected $expected, got $actual (diff > $eps)",
        )
    }
}
