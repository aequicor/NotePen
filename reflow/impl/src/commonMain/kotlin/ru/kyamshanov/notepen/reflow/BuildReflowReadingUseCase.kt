package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.TextAnchor

/**
 * Готовые данные для reflow-ридера: переверстанный документ и диапазоны-
 * выделения, полученные ре-анкорингом рукописных штрихов на текст.
 *
 * @property document переверстанный документ
 * @property highlights выделения по блокам (для подсветки в потоке текста)
 */
public data class ReflowReading(
    public val document: ReflowDocument,
    public val highlights: List<TextAnchor>,
)

/**
 * Собирает [ReflowReading] для документа: извлекает текст через
 * [PdfReflowExtractor] и переносит постранично-привязанные штрихи в текстовые
 * диапазоны через [StrokeTextMapper].
 *
 * @param extractor извлекатель reflow-содержимого (платформенный)
 */
public class BuildReflowReadingUseCase(
    private val extractor: PdfReflowExtractor,
) {
    /**
     * @param path путь/URI к PDF
     * @param strokesByPage рукописные штрихи по нулевому индексу страницы
     * @param highlightsByPage липкие выделения по нулевому индексу страницы; их
     *   диапазоны символов пересчитываются из геометрии на лету, поэтому всегда
     *   согласованы с текущей экстракцией
     * @param document уже извлечённый документ для переиспользования (например, из
     *   кэша редактора); если `null` — извлекается заново
     * @return документ + выделения для [ru.kyamshanov.notepen.reflow.ui.ReflowReader]
     */
    public suspend operator fun invoke(
        path: String,
        strokesByPage: Map<Int, List<DrawingPath>>,
        highlightsByPage: Map<Int, List<StickyHighlight>> = emptyMap(),
        document: ReflowDocument? = null,
    ): ReflowReading {
        val doc = document ?: extractor.extract(path)
        val strokeAnchors =
            strokesByPage.entries.flatMap { (pageIndex, strokes) ->
                strokes.flatMap { stroke -> StrokeTextMapper.anchorsFor(doc, pageIndex, stroke) }
            }
        val highlightAnchors =
            highlightsByPage.entries.flatMap { (pageIndex, highlights) ->
                highlights.flatMap { highlight -> StrokeTextMapper.anchorsForRects(doc, pageIndex, highlight.rects) }
            }
        return ReflowReading(document = doc, highlights = strokeAnchors + highlightAnchors)
    }
}
