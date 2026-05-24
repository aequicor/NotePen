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
 */
internal data class RawGlyph(
    val text: String,
    val rect: ReflowRect,
    val fontSizePt: Float,
    val spaceWidthPt: Float = 0f,
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
)
