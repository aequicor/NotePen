package ru.kyamshanov.notepen.reflow.api

/**
 * Фрагмент текста блока ([ReflowBlock.Paragraph] / [ReflowBlock.Heading]) с
 * привязкой к его происхождению на исходной PDF-странице.
 *
 * Гранулярность — один спан на исходный ран глифов (обычно близко к слову).
 * Инвариант: `block.text.substring(charStart, charEnd)` равен тексту этого рана.
 *
 * Нужен для ре-анкоринга аннотаций между постраничным и reflow-видом: [bounds]
 * лежит в той же нормализованной системе `[0..1]`, что и точки штрихов, поэтому
 * штрих можно сопоставить с диапазоном текста и наоборот.
 *
 * @property pageIndex нулевой индекс исходной страницы
 * @property charStart индекс начала фрагмента в `.text` блока (включительно)
 * @property charEnd индекс конца фрагмента в `.text` блока (исключительно)
 * @property bounds область фрагмента на странице, нормализованная к `[0..1]`
 * @property bold фрагмент набран полужирным начертанием (определено по имени шрифта)
 * @property italic фрагмент набран курсивным начертанием (определено по имени шрифта)
 * @property monospace фрагмент набран моноширинным шрифтом (обычно inline-код)
 * @property superscript фрагмент — надстрочный (smaller font + baseline-offset вверх),
 *   как в `x²` или маркер сноски `¹`. UI рендерит уменьшенным шрифтом и сдвигом вверх
 * @property subscript фрагмент — подстрочный (`H₂O`, индексы)
 */
public data class SourceSpan(
    public val pageIndex: Int,
    public val charStart: Int,
    public val charEnd: Int,
    public val bounds: ReflowRect,
    public val bold: Boolean = false,
    public val monospace: Boolean = false,
    public val italic: Boolean = false,
    public val superscript: Boolean = false,
    public val subscript: Boolean = false,
)
