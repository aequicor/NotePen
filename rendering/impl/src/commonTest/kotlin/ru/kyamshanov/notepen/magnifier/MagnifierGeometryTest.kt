package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdfviewer.PdfPagesLayout
import ru.kyamshanov.notepen.pdfviewer.SpreadMode
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

    @Test
    fun `resolvePageForDocSpace in SINGLE ignores docX`() {
        val layout =
            PdfPagesLayout.build(
                listOf(page(0), page(1)),
                basePageWidthPx = 100f,
            ) // SINGLE, tops 0 / 100
        // X не влияет — колонка одна.
        assertEquals(0, resolvePageForDocSpace(layout, docX = 9999f, docY = 50f))
        assertEquals(1, resolvePageForDocSpace(layout, docX = -9999f, docY = 150f))
    }

    @Test
    fun `resolvePageForDocSpace picks left or right page of spread pair by docX`() {
        // Пара (0,1) в одном Y-ряду: левая на X=0, правая на X=116 (100 + gutter 16).
        val layout =
            PdfPagesLayout.build(
                listOf(page(0), page(1)),
                basePageWidthPx = 100f,
                spreadMode = SpreadMode.SPREAD,
            )
        // Левая половина листа → страница 0.
        assertEquals(0, resolvePageForDocSpace(layout, docX = 50f, docY = 50f))
        // Правая половина (за корешком) → страница 1, хотя docY тот же ряд.
        assertEquals(1, resolvePageForDocSpace(layout, docX = 150f, docY = 50f))
        // Граница колонок — середина корешка (108): левее → 0, правее → 1.
        assertEquals(0, resolvePageForDocSpace(layout, docX = 100f, docY = 50f))
        assertEquals(1, resolvePageForDocSpace(layout, docX = 110f, docY = 50f))
    }

    @Test
    fun `resolvePageForDocSpace hanging last left page has no right column`() {
        // 3 страницы в spread: пара (0,1) + висячая левая 2. Для ряда страницы 2
        // правой колонки нет → docX справа всё равно даёт страницу 2.
        val layout =
            PdfPagesLayout.build(
                listOf(page(0), page(1), page(2)),
                basePageWidthPx = 100f,
                spreadMode = SpreadMode.SPREAD,
            )
        val row2Top = layout.pageTopsPx[2]
        assertEquals(2, resolvePageForDocSpace(layout, docX = 50f, docY = row2Top + 10f))
        assertEquals(2, resolvePageForDocSpace(layout, docX = 150f, docY = row2Top + 10f))
    }

    @Test
    fun `loupe selection on right spread page uses right page local x`() {
        val layout =
            PdfPagesLayout.build(
                listOf(page(0), page(1)),
                basePageWidthPx = 100f,
                spreadMode = SpreadMode.SPREAD,
            )

        val segments =
            buildLoupeSegmentsForDocRect(
                layout = layout,
                // Right column starts at 116, so this selects x=0.20..0.40 on page 1.
                docRect = Rect(136f, 10f, 156f, 40f),
            )

        assertEquals(1, segments?.size)
        val segment = segments!!.single()
        assertEquals(1, segment.pageIndex)
        assertNear(0.2f, segment.targetOnPage.left)
        assertNear(0.4f, segment.targetOnPage.right)
        assertNear(0.1f, segment.targetOnPage.top)
        assertNear(0.4f, segment.targetOnPage.bottom)
    }

    @Test
    fun `loupe selection spanning spread pair assigns separate panel x ranges`() {
        val layout =
            PdfPagesLayout.build(
                listOf(page(0), page(1)),
                basePageWidthPx = 100f,
                spreadMode = SpreadMode.SPREAD,
            )

        val segments =
            buildLoupeSegmentsForDocRect(
                layout = layout,
                // 80..100 is the left page tail, 100..116 is gutter,
                // 116..136 is the right page head.
                docRect = Rect(80f, 10f, 136f, 40f),
            )!!

        assertEquals(2, segments.size)
        val left = segments[0]
        val right = segments[1]
        assertEquals(0, left.pageIndex)
        assertEquals(1, right.pageIndex)
        assertNear(0f, left.panelLeftFrac)
        assertNear(20f / 56f, left.panelRightFrac)
        assertNear(36f / 56f, right.panelLeftFrac)
        assertNear(1f, right.panelRightFrac)
        assertNear(0.8f, left.targetOnPage.left)
        assertNear(1f, left.targetOnPage.right)
        assertNear(0f, right.targetOnPage.left)
        assertNear(0.2f, right.targetOnPage.right)
    }

    private fun page(index: Int): PdfPageInfo = PdfPageInfo(pageIndex = index, widthPt = 100f, heightPt = 100f)

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
