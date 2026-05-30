package ru.kyamshanov.notepen.pdfviewer

import androidx.compose.ui.geometry.Offset
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        val (panNew, zoomNew) =
            PdfViewerMath.zoomAroundFocus(
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
        val (_, z) =
            PdfViewerMath.zoomAroundFocus(
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
        val pan =
            PdfViewerMath.panForPageScroll(
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
    fun `clampPan keeps a fitting page within screen bounds`() {
        val layout = PdfPagesLayout.build(pages(1f), basePageWidthPx = 100f)
        // Контент 100×100 в 400×400 — обе оси помещаются. pan в пределах
        // [0, 300] по обеим осям, значит (40, 70) полностью внутри → не трогаем.
        val clamped =
            PdfViewerMath.clampPan(
                pan = Offset(40f, 70f),
                layout = layout,
                zoom = 1f,
                viewportSize = FloatSize(400f, 400f),
            )
        assertEquals(40f, clamped.x)
        assertEquals(70f, clamped.y)
    }

    @Test
    fun `clampPan pulls a fitting page back inside the viewport`() {
        val layout = PdfPagesLayout.build(pages(1f), basePageWidthPx = 100f)
        // Контент 100×100 в 400×400 → pan.x ∈ [0, 300]. 999 → 300 (страница
        // прижата к правому краю, но целиком в экране). -50 по Y → 0.
        val clamped =
            PdfViewerMath.clampPan(
                pan = Offset(999f, -50f),
                layout = layout,
                zoom = 1f,
                viewportSize = FloatSize(400f, 400f),
            )
        assertEquals(300f, clamped.x)
        assertEquals(0f, clamped.y)
    }

    @Test
    fun `clampPan grants vertical overscroll buffer when content overflows height`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f), basePageWidthPx = 100f) // total 300
        // Контент 100×300 при viewport 400×200 — X помещается → pan.x ∈ [0, 300],
        // 40 не трогаем; Y overflow → края [-100, 0]; буфер = 100*0.25 = 25 →
        // диапазон [-125, 25], 999 → 25.
        val clamped =
            PdfViewerMath.clampPan(
                pan = Offset(40f, 999f),
                layout = layout,
                zoom = 1f,
                viewportSize = FloatSize(400f, 200f),
            )
        assertEquals(40f, clamped.x)
        assertEquals(25f, clamped.y)
    }

    @Test
    fun `clampPan grants horizontal overscroll buffer when content overflows width`() {
        val layout = PdfPagesLayout.build(pages(1f), basePageWidthPx = 200f) // 200×200
        // Viewport 100×400: X overflow → края [100-200, 0] = [-100, 0];
        // буфер = 200 * 0.25 = 50 → допустимо [-150, 50]. Y помещается → [0, 200].
        val viewportSize = FloatSize(100f, 400f)
        val maxed =
            PdfViewerMath.clampPan(
                pan = Offset(999f, 0f),
                layout = layout,
                zoom = 1f,
                viewportSize = viewportSize,
            )
        assertEquals(50f, maxed.x) // hi + buffer
        assertEquals(0f, maxed.y)
        val minned =
            PdfViewerMath.clampPan(
                pan = Offset(-999f, 0f),
                layout = layout,
                zoom = 1f,
                viewportSize = viewportSize,
            )
        assertEquals(-150f, minned.x) // lo - buffer
        val within =
            PdfViewerMath.clampPan(
                pan = Offset(-50f, 0f),
                layout = layout,
                zoom = 1f,
                viewportSize = viewportSize,
            )
        assertEquals(-50f, within.x) // already inside the buffered range
    }

    @Test
    fun `clampPanFree lets a fitting page move almost off-screen without snapping back`() {
        val layout = PdfPagesLayout.build(pages(1f), basePageWidthPx = 100f)
        // Лист 100×100 в 400×400 помещается по обеим осям. keep = 100*0.25 = 25,
        // свободный диапазон по каждой оси = [25-100, 400-25] = [-75, 375] —
        // намного шире, чем у clampPan ([0, 300]): лист можно увести почти за край.
        val vp = FloatSize(400f, 400f)
        val far = PdfViewerMath.clampPanFree(Offset(999f, 999f), layout, zoom = 1f, viewportSize = vp)
        assertEquals(375f, far.x)
        assertEquals(375f, far.y)
        val near = PdfViewerMath.clampPanFree(Offset(-999f, -999f), layout, zoom = 1f, viewportSize = vp)
        assertEquals(-75f, near.x)
        assertEquals(-75f, near.y)
        // Значение внутри свободного хода не трогается (нет возврата к центру).
        val within = PdfViewerMath.clampPanFree(Offset(200f, -40f), layout, zoom = 1f, viewportSize = vp)
        assertEquals(200f, within.x)
        assertEquals(-40f, within.y)
    }

    @Test
    fun `clampPanFree keeps clampPan overscroll behavior on an overflowing axis`() {
        // Лист 100×300 в 400×200: X помещается → свободный ход; Y переполняет →
        // прежнее поведение clampPan (края [-100, 0] + буфер 25 → [-125, 25]).
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f), basePageWidthPx = 100f)
        val vp = FloatSize(400f, 200f)
        val free = PdfViewerMath.clampPanFree(Offset(999f, 999f), layout, zoom = 1f, viewportSize = vp)
        assertEquals(375f, free.x) // X — свободный: 400 - 25
        assertEquals(25f, free.y) // Y — как в clampPan: hi + буфер
    }

    @Test
    fun `clampPanFree forwards a custom overscroll buffer to the overflowing axis`() {
        // Android передаёт полэкранный буфер. Лист 100×300 в 400×200: Y переполняет,
        // края [-100, 0]; буфер 100 (вместо дефолтных 25) → диапазон [-200, 100].
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f), basePageWidthPx = 100f)
        val vp = FloatSize(400f, 200f)
        val clamped =
            PdfViewerMath.clampPanFree(
                pan = Offset(0f, 999f),
                layout = layout,
                zoom = 1f,
                viewportSize = vp,
                horizontalBuffer = 100f,
                verticalBuffer = 100f,
            )
        assertEquals(100f, clamped.y) // hi + кастомный буфер
    }

    @Test
    fun `clampPanFree ignores a right ink overflow when bounding horizontal travel`() {
        // У страницы ink выходит вправо за лист (right = 1.4): слот шире листа,
        // но свободный ход считается по КОЛОНКЕ листа (100), а не по слоту (140) —
        // иначе «вылет» extent'а перекосил бы перемещение.
        val layout =
            PdfPagesLayout.build(
                pages(1f),
                basePageWidthPx = 100f,
                extents = listOf(PageExtent(right = 1.4f)),
            )
        val vp = FloatSize(400f, 400f)
        val far = PdfViewerMath.clampPanFree(Offset(999f, 0f), layout, zoom = 1f, viewportSize = vp)
        assertEquals(375f, far.x) // 400 - 100*0.25 — по листу, не по слоту
    }

    @Test
    fun `centeringClamp centres the page sheet despite a right ink overflow`() {
        // Регрессия: скан-страница с правым «вылетом» extent (right = 1.4) должна
        // центрироваться по САМОМУ листу (colонка 100), а не по слоту (140).
        // Слот шире → slot-based clampPan стянул бы лист влево; centeringClamp —
        // нет. На этом инварианте держится центрирование при открытии (scrollToPage).
        val layout =
            PdfPagesLayout.build(
                pages(1f),
                basePageWidthPx = 100f,
                extents = listOf(PageExtent(right = 1.4f)),
            )
        val centered =
            PdfViewerMath.centeringClamp(
                pan = Offset(0f, 0f),
                layout = layout,
                zoom = 1f,
                viewportSize = FloatSize(400f, 400f),
            )
        assertEquals(150f, centered.x) // (400 - 100) / 2 — по листу
    }

    @Test
    fun `fitToWidth zoom equals viewportWidth divided by basePageWidth`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f), basePageWidthPx = 100f)
        assertEquals(3f, PdfViewerMath.fitToWidthZoom(layout, viewportWidth = 300f))
        // Кламп к MAX_ZOOM при огромном вьюпорте.
        assertEquals(PdfViewerMath.MAX_ZOOM, PdfViewerMath.fitToWidthZoom(layout, viewportWidth = 100_000f))
    }

    @Test
    fun `panForFitWidth places page beside rail and below counter`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f), basePageWidthPx = 100f) // 100×100 each
        // Viewport 200 wide, left rail inset 60, top counter inset 50, no right
        // inset → available width = 140, fit zoom = 1.4. PDF column (100×1.4=140)
        // exactly fills the free band → centeringX == insetStart == 60.
        val zoom = 1.4f
        val pan =
            PdfViewerMath.panForFitWidth(
                layout = layout,
                pageIndex = 1,
                zoom = zoom,
                viewportWidth = 200f,
                insetStartPx = 60f,
                insetTopPx = 50f,
                insetEndPx = 0f,
            )
        assertEquals(60f, pan.x)
        // Page 1 top (docTop = 100) lands at the counter inset: 50 - 100*1.4.
        assertEquals(50f - 100f * zoom, pan.y)
    }

    @Test
    fun `doubleTapTargetZoom zooms in from fit-width`() {
        // base 100, available 200 → fit = 2.0; зум на fit → приближаем ×FACTOR.
        val target =
            PdfViewerMath.doubleTapTargetZoom(
                currentZoom = 2f,
                basePageWidthPx = 100f,
                availableWidthPx = 200f,
            )
        assertEquals((2f * PdfViewerMath.DOUBLE_TAP_ZOOM_FACTOR), target)
    }

    @Test
    fun `doubleTapTargetZoom returns to fit-width when already zoomed in`() {
        // fit = 2.0, текущий 5.0 (> fit×epsilon) → возвращаем к fit-width.
        val target =
            PdfViewerMath.doubleTapTargetZoom(
                currentZoom = 5f,
                basePageWidthPx = 100f,
                availableWidthPx = 200f,
            )
        assertEquals(2f, target)
    }

    @Test
    fun `doubleTapTargetZoom clamps zoom-in to MAX_ZOOM`() {
        // fit = 4.0, ×2.5 = 10 → клампится к MAX_ZOOM.
        val target =
            PdfViewerMath.doubleTapTargetZoom(
                currentZoom = 4f,
                basePageWidthPx = 100f,
                availableWidthPx = 400f,
            )
        assertEquals(PdfViewerMath.MAX_ZOOM, target)
    }

    @Test
    fun `centeringClamp leaves pan free when content overflows axis`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f), basePageWidthPx = 100f) // 100×300
        // Контент 100×300, вьюпорт 50×100 — оба overflow → pan не трогаем.
        val p =
            PdfViewerMath.centeringClamp(
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
        val p =
            PdfViewerMath.centeringClamp(
                pan = Offset(999f, -50f),
                layout = layout,
                zoom = 1f,
                viewportSize = FloatSize(300f, 50f),
            )
        assertEquals(100f, p.x) // (300 - 100) / 2
        assertEquals(-50f, p.y) // не тронули
    }

    @Test
    fun `centeringClamp centres PDF sheet, not the extent-expanded slot`() {
        // Лист 100×100, надпись вылезла далеко вправо/вниз за границы PDF.
        val extent = PageExtent(left = 0f, top = 0f, right = 3f, bottom = 3f)
        val layout =
            PdfPagesLayout.build(
                pages(1f),
                basePageWidthPx = 100f,
                extents = listOf(extent),
            )
        // Вьюпорт 300×300: PDF-лист (100×100) помещается по обеим осям,
        // слот со штрихом (300×300) — нет. Центрируем сам лист.
        val p =
            PdfViewerMath.centeringClamp(
                pan = Offset(999f, 999f),
                layout = layout,
                zoom = 1f,
                viewportSize = FloatSize(300f, 300f),
            )
        assertEquals(100f, p.x) // (300 - 100) / 2 — лист по центру
        assertEquals(100f, p.y) // (300 - 100) / 2
    }

    @Test
    fun `fitToPageZoom uses min of width and height fit`() {
        val layout = PdfPagesLayout.build(pages(2f), basePageWidthPx = 100f) // page 100×50
        val z = PdfViewerMath.fitToPageZoom(layout, pageIndex = 0, FloatSize(300f, 200f))
        // byWidth = 3, byHeight = 4 → min = 3.
        assertEquals(3f, z)
    }

    @Test
    fun `layoutZoomCap caps so largest slot dim stays within MAX_LAYOUT_DIM_PX`() {
        // Базовая ширина 5000, страница квадратная → большая сторона слота 5000.
        // cap = 16000 / 5000 = 3.2 (ниже MAX_ZOOM=8).
        val layout = PdfPagesLayout.build(pages(1f), basePageWidthPx = 5000f)
        val cap = PdfViewerMath.layoutZoomCap(layout)
        assertTrue(abs(cap - (PdfViewerMath.MAX_LAYOUT_DIM_PX / 5000f)) < 1e-3f)
        // На cap большая сторона ровно = предел.
        assertTrue(layout.pageWidthsPx[0] * cap <= PdfViewerMath.MAX_LAYOUT_DIM_PX + 1f)
    }

    @Test
    fun `layoutZoomCap never exceeds MAX_ZOOM for small pages`() {
        val layout = PdfPagesLayout.build(pages(1f), basePageWidthPx = 500f)
        // 16000 / 500 = 32 → клампится к MAX_ZOOM.
        assertEquals(PdfViewerMath.MAX_ZOOM, PdfViewerMath.layoutZoomCap(layout))
    }

    @Test
    fun `residualScale is identity below cap and splits above`() {
        val cap = 4f
        // Ниже cap — растеризуем на полном зуме, residual == 1.
        assertEquals(2f, PdfViewerMath.layoutZoom(2f, cap))
        assertEquals(1f, PdfViewerMath.residualScale(2f, cap))
        // Выше cap — layoutZoom фиксируется на cap, остаток уходит в residual.
        assertEquals(cap, PdfViewerMath.layoutZoom(8f, cap))
        assertTrue(abs(PdfViewerMath.residualScale(8f, cap) - 2f) < 1e-4f)
    }

    @Test
    fun `layoutZoom times residualScale reconstructs visual zoom`() {
        val cap = 4f
        for (zoom in listOf(0.5f, 1f, 3.9f, 4f, 6f, 8f)) {
            val visual = PdfViewerMath.layoutZoom(zoom, cap) * PdfViewerMath.residualScale(zoom, cap)
            assertTrue(abs(visual - zoom) < 1e-4f, "zoom=$zoom reconstructed=$visual")
        }
    }

    // ── FEATURE #5: книжный разворот (SpreadMode.SPREAD) ────────────────────────

    @Test
    fun `spread pairs share a Y row and split into left-right columns`() {
        // 4 страницы, aspect 0.5 (узкие, высокие) при базовой ширине 100 → высоты 200.
        // Пары (0,1) и (2,3): каждая пара делит верх; следующий ряд на max высот пары.
        val layout =
            PdfPagesLayout.build(
                pages(0.5f, 0.5f, 0.5f, 0.5f),
                basePageWidthPx = 100f,
                spreadMode = SpreadMode.SPREAD,
            )
        // Левая и правая страницы пары делят один Y-ряд.
        assertEquals(0f, layout.pageTopsPx[0])
        assertEquals(0f, layout.pageTopsPx[1])
        assertEquals(200f, layout.pageTopsPx[2])
        assertEquals(200f, layout.pageTopsPx[3])
        // Левая колонка на X=0, правая — за колонкой + корешок.
        val rightX = 100f + PdfPagesLayout.SPREAD_GUTTER_PX
        assertEquals(0f, layout.pageLeftsPx[0])
        assertEquals(rightX, layout.pageLeftsPx[1])
        assertEquals(0f, layout.pageLeftsPx[2])
        assertEquals(rightX, layout.pageLeftsPx[3])
        // Общая высота — два ряда по 200.
        assertEquals(400f, layout.totalHeightPx)
    }

    @Test
    fun `spread trailing odd page occupies left half alone`() {
        // 3 страницы → пара (0,1) + висячая 2 слева одна.
        val layout =
            PdfPagesLayout.build(
                pages(1f, 1f, 1f),
                basePageWidthPx = 100f,
                spreadMode = SpreadMode.SPREAD,
            )
        assertEquals(0f, layout.pageTopsPx[0])
        assertEquals(0f, layout.pageTopsPx[1])
        assertEquals(100f, layout.pageTopsPx[2]) // следующий ряд
        assertEquals(0f, layout.pageLeftsPx[2]) // висячая — слева
        assertEquals(200f, layout.totalHeightPx) // два ряда по 100
    }

    @Test
    fun `spread row height is the max of the pair heights`() {
        // Левая страница ниже (aspect 1 → h=100), правая выше (aspect 0.5 → h=200).
        val layout =
            PdfPagesLayout.build(
                pages(1f, 0.5f, 1f, 1f),
                basePageWidthPx = 100f,
                spreadMode = SpreadMode.SPREAD,
            )
        // Ряд 0 высотой max(100, 200) = 200 → ряд 1 начинается на 200.
        assertEquals(0f, layout.pageTopsPx[0])
        assertEquals(0f, layout.pageTopsPx[1])
        assertEquals(200f, layout.pageTopsPx[2])
    }

    @Test
    fun `spreadLeftPageOf snaps right half to the pair's left page`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f, 1f), basePageWidthPx = 100f, spreadMode = SpreadMode.SPREAD)
        assertEquals(0, PdfViewerMath.spreadLeftPageOf(layout, 0))
        assertEquals(0, PdfViewerMath.spreadLeftPageOf(layout, 1)) // правая → левая пары
        assertEquals(2, PdfViewerMath.spreadLeftPageOf(layout, 2))
        assertEquals(2, PdfViewerMath.spreadLeftPageOf(layout, 3))
        // В одностраничном — тождественно.
        val single = PdfPagesLayout.build(pages(1f, 1f), basePageWidthPx = 100f)
        assertEquals(1, PdfViewerMath.spreadLeftPageOf(single, 1))
    }

    @Test
    fun `firstVisiblePageIndex returns the left page of the visible pair in spread`() {
        val layout = PdfPagesLayout.build(pages(1f, 1f, 1f, 1f), basePageWidthPx = 100f, spreadMode = SpreadMode.SPREAD)
        // Ряд 0 виден (pan.y = 0) → первая видимая = левая страница пары = 0.
        assertEquals(0, PdfViewerMath.firstVisiblePageIndex(layout, panY = 0f, zoom = 1f))
        // Прокрутили во второй ряд (pan.y = -100) → пара (2,3), левая = 2.
        assertEquals(2, PdfViewerMath.firstVisiblePageIndex(layout, panY = -100f, zoom = 1f))
    }

    @Test
    fun `rowWidthPx is one column single and two columns plus gutter in spread`() {
        val single = PdfPagesLayout.build(pages(1f, 1f), basePageWidthPx = 100f)
        assertEquals(100f, PdfViewerMath.rowWidthPx(single))
        val spread = PdfPagesLayout.build(pages(1f, 1f), basePageWidthPx = 100f, spreadMode = SpreadMode.SPREAD)
        assertEquals(200f + PdfPagesLayout.SPREAD_GUTTER_PX, PdfViewerMath.rowWidthPx(spread))
    }

    @Test
    fun `fitToWidth in spread fits the whole pair into the viewport width`() {
        // Пара двух колонок по 100 + корешок 16 = 216 → fit в 432 даёт зум 2.0
        // (каждая страница ~полэкрана), а не 4.32 как было бы для одной колонки.
        val layout = PdfPagesLayout.build(pages(1f, 1f), basePageWidthPx = 100f, spreadMode = SpreadMode.SPREAD)
        val row = 200f + PdfPagesLayout.SPREAD_GUTTER_PX
        assertEquals(432f / row, PdfViewerMath.fitToWidthZoom(layout, viewportWidth = 432f))
    }

    @Test
    fun `panForFitWidth centres the whole spread row in the free band`() {
        // Ряд = 216 при zoom 1; available = 300 → centeringX = inset + (300-216)/2.
        val layout = PdfPagesLayout.build(pages(1f, 1f), basePageWidthPx = 100f, spreadMode = SpreadMode.SPREAD)
        val row = 200f + PdfPagesLayout.SPREAD_GUTTER_PX
        val pan =
            PdfViewerMath.panForFitWidth(
                layout = layout,
                pageIndex = 0,
                zoom = 1f,
                viewportWidth = 360f,
                insetStartPx = 60f,
                insetTopPx = 0f,
                insetEndPx = 0f,
            )
        assertTrue(abs(pan.x - (60f + (300f - row) / 2f)) < 1e-3f, "centeringX=${pan.x}")
    }
}
