package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
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
     * @return документ + выделения для [ru.kyamshanov.notepen.reflow.ui.ReflowReader]
     */
    public suspend operator fun invoke(
        path: String,
        strokesByPage: Map<Int, List<DrawingPath>>,
    ): ReflowReading {
        val document = extractor.extract(path)
        val highlights =
            strokesByPage.entries.flatMap { (pageIndex, strokes) ->
                strokes.flatMap { stroke -> StrokeTextMapper.anchorsFor(document, pageIndex, stroke) }
            }
        return ReflowReading(document = document, highlights = highlights)
    }
}
