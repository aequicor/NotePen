package ru.kyamshanov.notepen.book.fixtures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.book.EbookAwarePdfReflowExtractor
import ru.kyamshanov.notepen.book.JvmEbookToPdfConverter
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Тест-инварианты на реальном EPUB (`planeta-ru.epub`, ~2 МБ): прогон через
 * EbookAware decorator chain (EPUB → PDF → reflow). Закрывает EPUB-ногу
 * приёмки наравне с [IsiguroFb2InvariantsTest] (FB2): рендер у обоих общий
 * (ReflowReader), специфичен лишь конвертер на входе.
 *
 * Путь захардкожен под локальную машину (`~/Downloads/test-fixtures`); на CI и
 * без файла — graceful skip. Положен сюда в ходе авто-QA reflow-ридера.
 *
 * **Что тестируем (EPUB-специфичный путь):**
 *  - EPUB опознаётся и конвертируется в текстовый PDF (TEXT_BASED либо HYBRID —
 *    последнее для книг со встроенными иллюстрациями, как planeta-ru; главное —
 *    не IMAGE_ONLY, т.е. текст реально извлечён).
 *  - Книжный объём контента: paragraphs > 100, total chars > 20K (planeta-ru —
 *    реальная книга, но порог занижен на случай частичной конвертации).
 *  - Текст не «разорван» табличным детектором: доля Table-блоков мала
 *    (< 25 %), как и положено чистой цифровой вёрстке EPUB (анти-регрессия к
 *    F-1/F-8 на не-OCR источнике).
 *  - Cache hit на повторный extract — быстро (< 5s).
 */
class PlanetaEpubInvariantsTest {
    private val epubPath = "/Users/kruz18/Downloads/test-fixtures/planeta-ru.epub"

    @Test
    fun planeta_epub_converts_to_textual_pdf() {
        runIfPresent { extractor ->
            val kind = runBlocking { extractor.probe(epubPath) }
            // EPUB с иллюстрациями легитимно классифицируется как HYBRID; важно,
            // что текст извлечён (не IMAGE_ONLY) — иначе reflow был бы пустым.
            assertTrue(
                kind == PdfContentKind.TEXT_BASED || kind == PdfContentKind.HYBRID,
                "EPUB must yield a textual PDF (TEXT_BASED or HYBRID); got $kind",
            )
        }
    }

    @Test
    fun planeta_epub_has_book_scale_content() {
        runIfPresent { extractor ->
            val doc = runBlocking { extractor.extract(epubPath) }
            val paragraphs = doc.blocks.filterIsInstance<ReflowBlock.Paragraph>().size
            val chars = doc.blocks.sumOf { textChars(it) }
            assertTrue(paragraphs > 100, "expected book-scale paragraph count; got $paragraphs")
            assertTrue(chars > 20_000, "expected book-scale text; got $chars chars")
        }
    }

    @Test
    fun planeta_epub_clean_text_not_shredded_into_tables() {
        runIfPresent { extractor ->
            val doc = runBlocking { extractor.extract(epubPath) }
            val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>().size
            val total = doc.blocks.size.coerceAtLeast(1)
            // Чистый цифровой EPUB не должен крошиться в фантомные таблицы (как
            // OCR-PDF до F-8). Высокий letterAvg чистого текста никогда не триггерит
            // TableNoiseGuard, так что доля таблиц должна быть мала.
            assertTrue(
                tables.toDouble() / total < 0.25,
                "clean EPUB should not be shredded into tables; tables=$tables of $total blocks",
            )
        }
    }

    @Test
    fun planeta_epub_cache_warm_extract_under_5s() {
        runIfPresent { extractor ->
            // Cold prime — конверсия EPUB → PDF идёт сюда. После — read sidecar.
            runBlocking { extractor.extract(epubPath) }
            val mark = TimeSource.Monotonic.markNow()
            runBlocking { extractor.extract(epubPath) }
            val ms = mark.elapsedNow().inWholeMilliseconds
            assertTrue(ms < 5_000L, "warm extract must hit sidecar cache; was ${ms}ms")
        }
    }

    private fun runIfPresent(block: (PdfReflowExtractor) -> Unit) {
        val file = File(epubPath)
        if (!file.exists() || !file.canRead()) {
            println("[planeta-epub] fixture not found — skipping")
            return
        }
        block(buildExtractor())
    }

    private fun buildExtractor(): PdfReflowExtractor {
        val converter = JvmEbookToPdfConverter(Dispatchers.IO)
        return EbookAwarePdfReflowExtractor(
            JvmPdfReflowExtractor(Dispatchers.IO),
            converter,
            converter,
        )
    }

    private fun textChars(b: ReflowBlock): Int =
        when (b) {
            is ReflowBlock.Paragraph -> b.text.length
            is ReflowBlock.Heading -> b.text.length
            is ReflowBlock.ListItem -> b.text.length
            is ReflowBlock.Blockquote -> b.text.length
            is ReflowBlock.Table -> b.rows.sumOf { r -> r.cells.sumOf { c -> c.text.length } }
            else -> 0
        }
}
