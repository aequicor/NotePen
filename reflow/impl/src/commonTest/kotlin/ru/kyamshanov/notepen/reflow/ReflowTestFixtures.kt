package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.math.abs
import kotlin.test.assertTrue

/** Ширина тестовой страницы в пунктах. */
internal const val PAGE_WIDTH_PT = 600f

/** Высота тестовой страницы в пунктах. */
internal const val PAGE_HEIGHT_PT = 800f

/** Тестовая [RawPage] (по умолчанию 600×800, страница 0). */
internal fun page(
    glyphs: List<RawGlyph>,
    images: List<ReflowRect> = emptyList(),
    pageIndex: Int = 0,
    widthPt: Float = PAGE_WIDTH_PT,
    heightPt: Float = PAGE_HEIGHT_PT,
): RawPage = RawPage(pageIndex = pageIndex, widthPt = widthPt, heightPt = heightPt, glyphs = glyphs, images = images)

/**
 * Раскладывает строку в глифы по символам (координаты в пунктах): пробел
 * сдвигает курсор без глифа (создавая межсловный зазор), остальные символы
 * получают прямоугольник шириной [charWidth].
 *
 * Высота бокса — [fontSize] * [boxHeightFrac]. По умолчанию `1.0` (бокс ростом в
 * кегль). Меньшие значения моделируют реальный `heightDir` PDFBox (высота
 * глиф-бокса < кегля): так из бокса нельзя надёжно вывести межстрочный шаг —
 * см. [ReflowAssemblerTest] про разрыв абзаца по baseline-to-baseline.
 *
 * [bold]/[monospace] помечают все глифы строки соответствующим начертанием —
 * чтобы проверять перенос стиля шрифта в провенанс.
 */
internal fun line(
    text: String,
    top: Float,
    fontSize: Float = 10f,
    startX: Float = 50f,
    charWidth: Float = 6f,
    bold: Boolean = false,
    monospace: Boolean = false,
    boxHeightFrac: Float = 1f,
): List<RawGlyph> {
    var x = startX
    val glyphs = mutableListOf<RawGlyph>()
    for (ch in text) {
        if (ch == ' ') {
            x += charWidth
            continue
        }
        glyphs +=
            RawGlyph(
                text = ch.toString(),
                rect = ReflowRect(left = x, top = top, right = x + charWidth, bottom = top + fontSize * boxHeightFrac),
                fontSizePt = fontSize,
                bold = bold,
                monospace = monospace,
            )
        x += charWidth
    }
    return glyphs
}

/** Сравнение Float с допуском (нормализация координат не должна быть бит-в-бит). */
internal fun assertAlmostEquals(
    expected: Float,
    actual: Float,
    tolerance: Float = 1e-4f,
) {
    assertTrue(abs(expected - actual) <= tolerance, "expected ~$expected but was $actual")
}
