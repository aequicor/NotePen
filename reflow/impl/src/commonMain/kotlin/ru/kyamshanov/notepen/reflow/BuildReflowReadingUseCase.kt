package ru.kyamshanov.notepen.reflow

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import ru.kyamshanov.notepen.reflow.api.PageBitmapProvider
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.TextAnchor
import kotlin.time.TimeSource

private val buildLogger = KotlinLogging.logger {}

/**
 * Готовые данные для reflow-ридера: переверстанный документ и диапазоны-
 * выделения, полученные ре-анкорингом рукописных штрихов на текст.
 *
 * @property document переверстанный документ
 * @property highlights выделения по блокам (для подсветки в потоке текста)
 * @property notes текстовые заметки, ре-анкоренные на текст (диапазон + сама заметка)
 */
public data class ReflowReading(
    public val document: ReflowDocument,
    public val highlights: List<TextAnchor>,
    public val notes: List<NoteAnchor> = emptyList(),
)

/**
 * Заметка ([PageNote]), привязанная к диапазону текста [anchor] в текущей экстракции —
 * для отрисовки маркера/подсветки заметки в reflow-ридере.
 *
 * @property anchor диапазон текста, к которому привязана заметка
 * @property note сама заметка (текст, цвет, геометрия-источник истины)
 */
public data class NoteAnchor(
    public val anchor: TextAnchor,
    public val note: PageNote,
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
     * @param notesByPage текстовые заметки по нулевому индексу страницы; ре-анкорятся
     *   геометрически (как выделения), с откатом к поиску цитаты (см.
     *   [StrokeTextMapper.anchorsForNote])
     * @param document уже извлечённый документ для переиспользования (например, из
     *   кэша редактора); если `null` — извлекается заново
     * @param pageBitmaps опциональный колбэк растеризации страницы под Lattice-
     *   уточнение таблиц. `null` — Lattice пропускается, в документе остаются
     *   Figure-крoпы для low-confidence Stream-таблиц. При наличии колбэка
     *   рендер дёргается лениво — только для страниц с low-conf кандидатами.
     * @return документ + выделения для [ru.kyamshanov.notepen.reflow.ui.ReflowReader]
     */
    public suspend operator fun invoke(
        path: String,
        strokesByPage: Map<Int, List<DrawingPath>>,
        highlightsByPage: Map<Int, List<StickyHighlight>> = emptyMap(),
        notesByPage: Map<Int, List<PageNote>> = emptyMap(),
        document: ReflowDocument? = null,
        pageBitmaps: PageBitmapProvider? = null,
    ): ReflowReading {
        val totalMark = TimeSource.Monotonic.markNow()
        val extractMark = TimeSource.Monotonic.markNow()
        val doc =
            document
                ?: if (pageBitmaps != null) extractor.extractWithLattice(path, pageBitmaps) else extractor.extract(path)
        val extractMs = extractMark.elapsedNow().inWholeMilliseconds
        val anchorMark = TimeSource.Monotonic.markNow()
        val strokeAnchors =
            strokesByPage.entries.flatMap { (pageIndex, strokes) ->
                strokes.flatMap { stroke -> StrokeTextMapper.anchorsFor(doc, pageIndex, stroke) }
            }
        val highlightAnchors =
            highlightsByPage.entries.flatMap { (pageIndex, highlights) ->
                highlights.flatMap { highlight -> StrokeTextMapper.anchorsForRects(doc, pageIndex, highlight.rects) }
            }
        val noteAnchors =
            notesByPage.values.flatMap { notes ->
                notes.flatMap { note ->
                    StrokeTextMapper.anchorsForNote(doc, note).map { NoteAnchor(it, note) }
                }
            }
        val anchorMs = anchorMark.elapsedNow().inWholeMilliseconds
        buildLogger.info {
            "PdfReflow: reading-build extract=${extractMs}ms anchors=${anchorMs}ms " +
                "blocks=${doc.blocks.size} strokes=${strokesByPage.values.sumOf { it.size }} " +
                "highlights=${highlightsByPage.values.sumOf { it.size }} " +
                "notes=${notesByPage.values.sumOf { it.size }} cached=${document != null} " +
                "total=${totalMark.elapsedNow().inWholeMilliseconds}ms"
        }
        return ReflowReading(
            document = doc,
            highlights = strokeAnchors + highlightAnchors,
            notes = noteAnchors,
        )
    }
}
