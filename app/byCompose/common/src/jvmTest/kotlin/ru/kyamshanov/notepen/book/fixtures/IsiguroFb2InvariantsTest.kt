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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Тест-инварианты на крупном FB2-файле (Исигуро, «Клара и Солнце», 1.5 МБ): прогон
 * через EbookAware decorator chain (FB2 → PDF → reflow). Дополняет [Fb2InvariantsTest],
 * который работает с маленькими test-resource'ами.
 *
 * Путь захардкожен под локальную машину; на CI и без файла — graceful skip.
 *
 * **Что тестируем:**
 *  - FB2 классифицируется как TEXT_BASED (PDF из конвертера всегда textual).
 *  - Длинный роман (~1.5 МБ FB2 ≈ много страниц): paragraphs > 1000; total chars
 *    > 200K (порядок книги).
 *  - Cache hit на повторный extract — отрабатывает быстро (< 5s).
 *  - Heading 1 — metadata-«мусор» (известная проблема, locked).
 */
class IsiguroFb2InvariantsTest {
    private val isiguroPath = "/Users/kruz18/Downloads/Telegram Desktop/Isiguro_Klara-i-Solnce.aQ1xpw.619657.fb2"

    @Test
    fun isiguro_fb2_classifies_as_TEXT_BASED() {
        runIfPresent { extractor ->
            val kind = runBlocking { extractor.probe(isiguroPath) }
            assertEquals(PdfContentKind.TEXT_BASED, kind)
        }
    }

    @Test
    fun isiguro_fb2_has_book_scale_content() {
        runIfPresent { extractor ->
            val doc = runBlocking { extractor.extract(isiguroPath) }
            val paragraphs = doc.blocks.filterIsInstance<ReflowBlock.Paragraph>().size
            val chars = doc.blocks.sumOf { textChars(it) }
            assertTrue(paragraphs > 1_000, "expected book-scale paragraph count; got $paragraphs")
            // 1.5 МБ FB2 (XML) — после конвертации даёт текст уровня романа,
            // полмиллиона символов точно есть. Floor 200K — на случай если
            // конвертер съест часть контента.
            assertTrue(chars > 200_000, "expected book-scale text; got $chars chars")
        }
    }

    @Test
    fun isiguro_fb2_cache_warm_extract_under_5s() {
        runIfPresent { extractor ->
            // Cold prime — конверсия FB2 → PDF идёт сюда. После — read sidecar.
            runBlocking { extractor.extract(isiguroPath) }
            val mark = TimeSource.Monotonic.markNow()
            runBlocking { extractor.extract(isiguroPath) }
            val ms = mark.elapsedNow().inWholeMilliseconds
            assertTrue(ms < 5_000L, "warm extract must hit sidecar cache; was ${ms}ms")
        }
    }

    @Test
    fun isiguro_fb2_first_heading_is_book_title_or_metadata() {
        runIfPresent { extractor ->
            val doc = runBlocking { extractor.extract(isiguroPath) }
            val headings = doc.blocks.filterIsInstance<ReflowBlock.Heading>()
            assertTrue(headings.isNotEmpty(), "FB2 emits at least one heading from metadata")
            val first = headings.first().text.trim()
            // На крупных FB2 metadata часто заполнена корректно (title книги),
            // на мелких — выбрасывает md5/«Unknown». Локим только что heading
            // непустой; точное содержимое — это metadata-driven и зависит от
            // источника, не от нашего pipeline'а.
            assertTrue(first.isNotEmpty(), "FB2 metadata heading должна быть непустой")
            assertTrue(
                first.any { it.isLetter() },
                "FB2 metadata heading должна содержать хотя бы одну букву; got: '$first'",
            )
        }
    }

    private fun runIfPresent(block: (PdfReflowExtractor) -> Unit) {
        val file = File(isiguroPath)
        if (!file.exists() || !file.canRead()) {
            println("[isiguro-fb2] fixture not found — skipping")
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
