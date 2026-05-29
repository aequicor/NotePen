package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.SourceSpan

/**
 * Результат обмера одного блока для построчной раскладки страниц.
 *
 * @property heightPx высота блока в пикселях
 * @property lineBottoms Y нижних границ строк относительно верха блока (только для
 *   делимых блоков — Paragraph/ListItem/Blockquote); пуст для неделимых
 */
internal data class MeasuredBlock(
    val heightPx: Int,
    val lineBottoms: List<Float> = emptyList(),
)

/**
 * Чистый, детерминированный обмер блоков ридера для пагинации. Заменяет
 * предыдущий «невидимый Compose-проход» (`Box(alpha=0)` + `onSizeChanged` × 7000
 * блоков), который синхронно блокировал main-поток на 13-17 секунд и был
 * нестабилен относительно повторных композиций — теперь высоты считаются на
 * фоновом диспетчере через [TextMeasurer], а результат идемпотентен относительно
 * `(text, style, constraints)`.
 *
 * Идемпотентность критична для defect (b) «съезжающего» содержимого при возврате
 * на читанную страницу: один и тот же документ при одинаковых типографических
 * настройках и фиксированной [contentWidthPx] всегда даёт одни и те же
 * [MeasuredBlock] (а значит, и одну и ту же раскладку страниц).
 *
 * Стиль для Heading/Paragraph/ListItem/Blockquote берётся из
 * [paragraphStyle]/[headingStyle] — с явным `LineHeightStyle` (см.
 * `DeterministicLineHeight` в `ReflowReader.kt`), поэтому `TextMeasurer` и
 * рендеринговый `BasicText` сходятся по высоте.
 *
 * Figure-блоки [BlockHeightCalculator] не меряет — их высота известна
 * аналитически (`round(contentWidthPx / aspectRatio)`) и передаётся снаружи в
 * [figureHeights]; здесь только возвращается готовое значение.
 *
 * Divider — `2*blockSpacing + 1.dp`.
 *
 * Table — упрощённый обмер: для каждой строки `max(cell heights)` при ширине
 * ячейки = `(contentWidth - borders) / cellCount`, плюс рамки и padding.
 * Это приблизительная оценка (Table-структура в нашем pipeline всё равно часто
 * восстановлена эвристически и подлежит редизайну в P7).
 */
internal object BlockHeightCalculator {
    fun measure(
        block: ReflowBlock,
        index: Int,
        contentWidthPx: Int,
        settings: ReflowReaderSettings,
        textMeasurer: TextMeasurer,
        density: Density,
        figureHeights: Map<Int, Int>,
    ): MeasuredBlock {
        val w = contentWidthPx.coerceAtLeast(1)
        return when (block) {
            is ReflowBlock.Figure -> MeasuredBlock(figureHeights[index] ?: 0)
            ReflowBlock.Divider -> measureDivider(settings, density)
            is ReflowBlock.Heading ->
                measureNonSplittable(
                    block.text,
                    block.source,
                    settings.headingStyle(block.level),
                    w,
                    settings,
                    textMeasurer,
                )
            is ReflowBlock.Paragraph -> measureSplittable(block.text, block.source, settings.paragraphStyle(), 0, w, settings, textMeasurer)
            is ReflowBlock.ListItem -> {
                // Согласовано с UI: ListItemView рендерит padding-start = contentPadding × (level + 1),
                // обмер должен использовать ту же ширину, иначе lineBottoms/heights разъедутся и
                // даст drift на границе страниц (defect (b) — см. P0-P5).
                val indent = with(density) { (settings.contentPadding * (block.level + 1)).roundToPx() }
                measureSplittable(block.text, block.source, settings.paragraphStyle(), indent, w, settings, textMeasurer)
            }
            is ReflowBlock.Blockquote -> {
                val indent = with(density) { (BLOCKQUOTE_BAR_WIDTH + settings.contentPadding).roundToPx() }
                val style = settings.paragraphStyle().copy(fontStyle = FontStyle.Italic)
                measureSplittable(block.text, block.source, style, indent, w, settings, textMeasurer)
            }
            is ReflowBlock.Table -> measureTable(block, w, settings, textMeasurer, density)
            is ReflowBlock.Code -> measureSplittable(block.text, block.source, settings.codeStyle(), 0, w, settings, textMeasurer)
            is ReflowBlock.Footnote -> measureSplittable(block.text, block.source, settings.footnoteStyle(), 0, w, settings, textMeasurer)
        }
    }

    private fun measureDivider(
        settings: ReflowReaderSettings,
        density: Density,
    ): MeasuredBlock {
        // DividerView: Modifier.padding(vertical = blockSpacing) вокруг 1.dp линии.
        val totalDp = settings.blockSpacing * 2 + 1.dp
        return MeasuredBlock(with(density) { totalDp.roundToPx() })
    }

    private fun measureNonSplittable(
        text: String,
        source: List<SourceSpan>,
        style: TextStyle,
        contentWidthPx: Int,
        settings: ReflowReaderSettings,
        textMeasurer: TextMeasurer,
    ): MeasuredBlock {
        val annotated = styledText(text, source, emptyList(), settings)
        val result = textMeasurer.measure(annotated, style, constraints = Constraints(maxWidth = contentWidthPx))
        return MeasuredBlock(result.size.height)
    }

    private fun measureSplittable(
        text: String,
        source: List<SourceSpan>,
        style: TextStyle,
        indentPx: Int,
        contentWidthPx: Int,
        settings: ReflowReaderSettings,
        textMeasurer: TextMeasurer,
    ): MeasuredBlock {
        val width = (contentWidthPx - indentPx).coerceAtLeast(1)
        val annotated = styledText(text, source, emptyList(), settings)
        val result = textMeasurer.measure(annotated, style, constraints = Constraints(maxWidth = width))
        val lineBottoms = List(result.lineCount) { result.getLineBottom(it) }
        return MeasuredBlock(result.size.height, lineBottoms)
    }

    /**
     * Phase B precision: charStart, соответствующий вершине окна, попадающей внутрь
     * делимого блока на y-координате [offsetPx] относительно верха блока. Возвращает
     * 0 для не-делимых блоков и `offsetPx ≤ 0` (страница начинается с верха блока).
     *
     * Не использует кэш [lineBottoms]: считаем линии заново через [TextMeasurer]
     * (один measure на вызов), потому что нужен ещё и `getLineStart(line)` — а это
     * уже не покрывается lineBottoms. Стоимость — один measure одного блока на
     * каждое перелистывание, что незначительно (firstBlock обычно небольшой).
     */
    fun charStartAtOffsetPx(
        block: ReflowBlock,
        contentWidthPx: Int,
        settings: ReflowReaderSettings,
        textMeasurer: TextMeasurer,
        density: Density,
        offsetPx: Float,
    ): Int {
        val spec = textSpecFor(block, contentWidthPx, settings, density)
        if (offsetPx <= 0f || spec == null) return 0
        val result =
            textMeasurer.measure(
                text = styledText(spec.text, spec.source, emptyList(), settings),
                style = spec.style,
                constraints = Constraints(maxWidth = spec.widthPx),
            )
        // Первая строка, чей низ строго больше offsetPx — она «содержит» offsetPx
        // (низ предыдущей ≤ offsetPx < низ этой).
        var line = 0
        while (line < result.lineCount && result.getLineBottom(line) <= offsetPx) line++
        val safeLine = line.coerceAtMost((result.lineCount - 1).coerceAtLeast(0))
        return if (result.lineCount == 0) 0 else result.getLineStart(safeLine)
    }

    /**
     * Phase B precision (read-side): индекс строки в [block], содержащей символ
     * [charStart]. Возвращает 0 для не-делимых блоков (там charStart всегда 0) или
     * `charStart <= 0`. Отрицательное значение никогда не возвращает — клампится.
     */
    fun lineForCharStart(
        block: ReflowBlock,
        contentWidthPx: Int,
        settings: ReflowReaderSettings,
        textMeasurer: TextMeasurer,
        density: Density,
        charStart: Int,
    ): Int {
        val spec = textSpecFor(block, contentWidthPx, settings, density)
        if (charStart <= 0 || spec == null) return 0
        val result =
            textMeasurer.measure(
                text = styledText(spec.text, spec.source, emptyList(), settings),
                style = spec.style,
                constraints = Constraints(maxWidth = spec.widthPx),
            )
        val lc = result.lineCount
        val safeChar = charStart.coerceAtMost(spec.text.length)
        return if (lc == 0) 0 else result.getLineForOffset(safeChar).coerceIn(0, lc - 1)
    }

    /**
     * Спецификация текста для measure-операций над одним блоком: текст, провенанс,
     * стиль и эффективная ширина с учётом indent'а блока. `null` для блоков без
     * текстового содержимого (Figure/Table/Divider) и не-делимых (Heading): к ним
     * Phase B мапинг charStart↔line не применяется.
     */
    private data class TextSpec(
        val text: String,
        val source: List<SourceSpan>,
        val style: TextStyle,
        val widthPx: Int,
    )

    private fun textSpecFor(
        block: ReflowBlock,
        contentWidthPx: Int,
        settings: ReflowReaderSettings,
        density: Density,
    ): TextSpec? {
        val w = contentWidthPx.coerceAtLeast(1)
        return when (block) {
            is ReflowBlock.Paragraph ->
                TextSpec(block.text, block.source, settings.paragraphStyle(), w)
            is ReflowBlock.ListItem -> {
                // Согласованно с [measure]: учитываем уровень вложенности в indent'е.
                val indent = with(density) { (settings.contentPadding * (block.level + 1)).roundToPx() }
                TextSpec(block.text, block.source, settings.paragraphStyle(), (w - indent).coerceAtLeast(1))
            }
            is ReflowBlock.Blockquote -> {
                val indent = with(density) { (BLOCKQUOTE_BAR_WIDTH + settings.contentPadding).roundToPx() }
                val style = settings.paragraphStyle().copy(fontStyle = FontStyle.Italic)
                TextSpec(block.text, block.source, style, (w - indent).coerceAtLeast(1))
            }
            is ReflowBlock.Code -> TextSpec(block.text, block.source, settings.codeStyle(), w)
            is ReflowBlock.Footnote -> TextSpec(block.text, block.source, settings.footnoteStyle(), w)
            is ReflowBlock.Heading,
            is ReflowBlock.Figure,
            is ReflowBlock.Table,
            ReflowBlock.Divider,
            -> null
        }
    }

    private fun measureTable(
        block: ReflowBlock.Table,
        contentWidthPx: Int,
        settings: ReflowReaderSettings,
        textMeasurer: TextMeasurer,
        density: Density,
    ): MeasuredBlock {
        if (block.rows.isEmpty()) return MeasuredBlock(0)
        val maxCellsPerRow = block.rows.maxOf { it.cells.size }.coerceAtLeast(1)
        val borderPx = with(density) { TABLE_BORDER_WIDTH.roundToPx() }
        val paddingPx = with(density) { TABLE_CELL_PADDING.roundToPx() }
        val cellInnerWidth =
            ((contentWidthPx - borderPx * (maxCellsPerRow + 1)) / maxCellsPerRow - paddingPx * 2)
                .coerceAtLeast(1)
        val rowStyle = settings.paragraphStyle()
        var total = 0
        for (row in block.rows) {
            var maxCellHeight = 0
            for (cell in row.cells) {
                val annotated = styledText(cell.text, cell.source, emptyList(), settings)
                val result =
                    textMeasurer.measure(
                        annotated,
                        rowStyle,
                        constraints = Constraints(maxWidth = cellInnerWidth),
                    )
                if (result.size.height > maxCellHeight) maxCellHeight = result.size.height
            }
            total += maxCellHeight + paddingPx * 2 + borderPx * 2
        }
        return MeasuredBlock(total)
    }
}
