package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo

/**
 * Чистые тесты математики viewer'а: layout страничной стопки,
 * cursor-anchored zoom, derived "first visible" и conversion'ы для sync.
 */
class PdfPagesLayoutTest {

    private fun pages(vararg aspectRatios: Float): List<PdfPageInfo> =
        aspectRatios.mapIndexed { i, ar ->
            PdfPageInfo(pageIndex = i, widthPt = 100f, heightPt = 100f / ar)
        }

    @Test
    fun `build accumulates page tops at base width`() {
        // 3 страницы 0.5 (узкие, высокие) при базовой ширине 100 → высоты 200
        val layout = PdfPagesLayout.build(pages(0.5f, 0.5f, 0.5f), basePageWidthPx = 100f)
        assertEquals(100f, layout.basePageWidthPx)
        assertEquals(listOf(200f, 200f, 200f), layout.pageHeightsPx.toList())
        assertEquals(listOf(0f, 200f, 400f), layout.pageTopsPx.toList())
        assertEquals(600f, layout.totalHeightPx)
    }

    @Test
    fun `cursor pixel stays under focus after zoom`() {
        val focus = Offset(640f, 480f)
        val pan = Offset(40f, -20f)
        val zoomOld = 1.3f
        val (panNew, zoomNew) = PdfViewerMath.zoomAroundFocus(
            focus = focus,
            panOld = pan,
            zoomOld = zoomOld,
            zoomTarget = zoomOld * 0.9f,
        )
        val docBefore = (focus - pan) / zoomOld
        val docAfter = (focus - panNew) / zoomNew
        assertTrue(abs(docBefore.x - docAfter.x) < 1e-3f)
        assertTrue(abs(docBefore.y - docAfter.y) < 1e-3f)
    }

    @Test
    fun `zoom clamps to bounds`() {
        val (_, z) = PdfViewerMath.zoomAroundFocus(
            focus = Offset(100f, 100f),
            panOld = Offset.Zero,
            zoomOld = 7f,
            zoomTarget = 100f, // > MAX_ZOOM
        )
        assertEquals(PdfViewerMath.MAX_ZOOM, z)
    }

    @Test
    fun `visiblePageRange returns only pages overlapping viewport`() {
        // 5 страниц по 100 высоты, zoom = 1, pan.y = -150 → видны страницы 1 и 2.
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f, 1f, 1f), basePageWidthPx = 100f)
        val r = PdfViewerMath.visiblePageRange(layout, panY = -150f, zoom = 1f, viewportHeight = 100f)
        assertEquals(1..2, r)
    }

    @Test
    fun `firstVisiblePageIndex matches LazyList semantics`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f), basePageWidthPx = 100f) // 3 × 100
        // pan.y = -50 — первая страница ушла на половину за верх; первая видимая — 0.
        assertEquals(0, PdfViewerMath.firstVisiblePageIndex(layout, panY = -50f, zoom = 1f))
        // pan.y = -100 — верх второй страницы у самого верха вьюпорта.
        assertEquals(1, PdfViewerMath.firstVisiblePageIndex(layout, panY = -100f, zoom = 1f))
        // pan.y = -250 — третья страница уже видна, она и first-visible.
        assertEquals(2, PdfViewerMath.firstVisiblePageIndex(layout, panY = -250f, zoom = 1f))
    }

    @Test
    fun `pageScrollOffsetPx is intra-page offset of first visible`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f), basePageWidthPx = 100f)
        // pan.y = -130 → first visible = 1, offset внутри страницы 1 = 30.
        val firstIdx = PdfViewerMath.firstVisiblePageIndex(layout, panY = -130f, zoom = 1f)
        assertEquals(1, firstIdx)
        assertEquals(
            30,
            PdfViewerMath.pageScrollOffsetPx(layout, firstIdx, panY = -130f, zoom = 1f),
        )
    }

    @Test
    fun `panForPageScroll places page top at -offsetPx`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f), basePageWidthPx = 100f)
        val pan = PdfViewerMath.panForPageScroll(
            layout = layout,
            pageIndex = 2,
            offsetPx = 50,
            zoom = 1f,
            currentPanX = 0f,
        )
        // Хотим: страница 2 (docTop = 200) → её визуальный top = -50.
        assertEquals(-50f - 200f, pan.y)
    }

    @Test
    fun `clampPan centres content shorter than viewport`() {
        val layout = PdfPagesLayout.build(pages(1f), basePageWidthPx = 100f)
        val clamped = PdfViewerMath.clampPan(
            pan = Offset(999f, 999f),
            layout = layout,
            zoom = 1f,
            viewportSize = FloatSize(400f, 400f),
        )
        // Контент 100×100 в 400×400 центрируется в (150, 150).
        assertEquals(150f, clamped.x)
        assertEquals(150f, clamped.y)
    }

    @Test
    fun `clampPan keeps tall content scrollable within bounds`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f), basePageWidthPx = 100f) // total 300
        val clamped = PdfViewerMath.clampPan(
            pan = Offset(999f, 999f),
            layout = layout,
            zoom = 1f,
            viewportSize = FloatSize(400f, 200f),
        )
        // Контент 100×300 при viewport 400×200 — X центрируется (150), Y клампится в [200-300, 0] = [-100, 0].
        // 999 в Y → клампится к 0.
        assertEquals(150f, clamped.x)
        assertEquals(0f, clamped.y)
    }

    @Test
    fun `fitToWidth zoom equals viewportWidth divided by basePageWidth`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f), basePageWidthPx = 100f)
        assertEquals(3f, PdfViewerMath.fitToWidthZoom(layout, viewportWidth = 300f))
        // Кламп к MAX_ZOOM при огромном вьюпорте.
        assertEquals(PdfViewerMath.MAX_ZOOM, PdfViewerMath.fitToWidthZoom(layout, viewportWidth = 100_000f))
    }

    @Test
    fun `centeringClamp leaves pan free when content overflows axis`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f), basePageWidthPx = 100f) // 100×300
        // Контент 100×300, вьюпорт 50×100 — оба overflow → pan не трогаем.
        val p = PdfViewerMath.centeringClamp(
            pan = Offset(-200f, -150f),
            layout = layout,
            zoom = 1f,
            viewportSize = FloatSize(50f, 100f),
        )
        assertEquals(Offset(-200f, -150f), p)
    }

    @Test
    fun `centeringClamp centres fitted axes only`() {
        val layout = PdfPagesLayout.build(pages(1f), basePageWidthPx = 100f) // 100×100
        // Вьюпорт 300×50 — X помещается (центрируем), Y overflow (не трогаем).
        val p = PdfViewerMath.centeringClamp(
            pan = Offset(999f, -50f),
            layout = layout,
            zoom = 1f,
            viewportSize = FloatSize(300f, 50f),
        )
        assertEquals(100f, p.x) // (300 - 100) / 2
        assertEquals(-50f, p.y) // не тронули
    }

    @Test
    fun `fitToPageZoom uses min of width and height fit`() {
        val layout = PdfPagesLayout.build(pages(2f), basePageWidthPx = 100f) // page 100×50
        val z = PdfViewerMath.fitToPageZoom(layout, pageIndex = 0, FloatSize(300f, 200f))
        // byWidth = 3, byHeight = 4 → min = 3.
        assertEquals(3f, z)
    }
}
