package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowRect

/**
 * Один извлечённый глиф (или короткий ран глифов с общим стилем) с его
 * положением на странице. Платформенно-нейтральное промежуточное
 * представление, которое наполняют экстракторы (PDFBox / PdfBox-Android) и
 * потребляет [ReflowAssembler].
 *
 * @property text юникод-текст глифа/рана
 * @property rect ограничивающий прямоугольник в координатах PDF-страницы (пункты)
 * @property fontSizePt кегль шрифта в пунктах
 * @property spaceWidthPt ширина пробела текущего шрифта в пунктах; 0 — неизвестна.
 *   Используется, чтобы отличить межсловный зазор от широкого трекинга букв.
 * @property bold глиф набран полужирным начертанием (по имени шрифта)
 * @property italic глиф набран курсивом (по имени шрифта)
 * @property monospace глиф набран моноширинным шрифтом (по имени шрифта)
 */
internal data class RawGlyph(
    val text: String,
    val rect: ReflowRect,
    val fontSizePt: Float,
    val spaceWidthPt: Float = 0f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val monospace: Boolean = false,
)

/**
 * Сырое содержимое одной страницы PDF: позиционированные глифы и области
 * растровых изображений. Координаты — пункты PDF, origin в верхнем левом углу.
 *
 * @property pageIndex нулевой индекс страницы
 * @property widthPt ширина страницы в пунктах
 * @property heightPt высота страницы в пунктах
 * @property glyphs позиционированные глифы (пусто, если текстового слоя нет)
 * @property images области встроенных растровых изображений
 */
internal data class RawPage(
    val pageIndex: Int,
    val widthPt: Float,
    val heightPt: Float,
    val glyphs: List<RawGlyph>,
    val images: List<ReflowRect>,
    /**
     * Векторные линии страницы (`moveTo`/`lineTo`/`strokePath` PDF-операторов),
     * отфильтрованные до близких к горизонтальным/вертикальным. Используются
     * Lattice-уточнителем для распознавания таблиц без растеризации PDF —
     * PDF с нарисованными рамками таблиц получают точную сетку из их структуры,
     * а не из пикселей.
     *
     * Координаты в PDF-пунктах (top-left origin, как `glyphs[i].rect`). Пусто,
     * если экстрактор не собирает векторы (старые тесты, синтетические RawPage)
     * — для них Lattice не применяется без явного `extractWithLattice`.
     */
    val vectorLines: List<VectorLine> = emptyList(),
)

/**
 * Отрезок прямой на странице (горизонтальный или вертикальный) — кандидат
 * в линию решётки таблицы. Подгоняется к одной из двух осей: glуы с
 * |Δy| < |Δx| · TOLERANCE классифицируются как [isHorizontal] и наоборот.
 *
 * Координаты в PDF-пунктах (top-left origin):
 * - для **горизонтальной** линии `start`/`end` — X-координаты концов,
 *   `perpPos` — Y;
 * - для **вертикальной** — наоборот.
 *
 * Эта форма зеркалит [ru.kyamshanov.notepen.reflow.lattice.Morphology.LineSegment]
 * (бакет run-length из растровой бинаризации), и позволяет конвертацию без
 * растеризации.
 */
internal data class VectorLine(
    val isHorizontal: Boolean,
    val start: Float,
    val end: Float,
    val perpPos: Float,
)
