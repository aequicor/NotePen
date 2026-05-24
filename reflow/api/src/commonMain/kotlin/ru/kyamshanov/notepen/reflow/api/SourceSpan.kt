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
 */
public data class SourceSpan(
    public val pageIndex: Int,
    public val charStart: Int,
    public val charEnd: Int,
    public val bounds: ReflowRect,
)
