package ru.kyamshanov.notepen.book.fixtures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.book.EbookAwarePdfReflowExtractor
import ru.kyamshanov.notepen.book.JvmEbookToPdfConverter
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import java.io.File
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * Discovery-тесты на in-repo FB2-фикстурах. FB2 = FictionBook 2.0 XML — формат
 * проходит через [EbookAwarePdfReflowExtractor] decorator chain:
 * `converter.canConvert(path)` → `true` → `converter.ensurePdf(path)` конвертирует
 * FB2 в PDF (рендерится через `JvmBookPdfRenderer`), затем стандартный
 * [JvmPdfReflowExtractor] извлекает текст. Sidecar `BookReflowProvider.reflowFor`
 * может вернуть готовый sidecar reflow без re-extract'a, но для свежих фикстур
 * sidecar отсутствует — идём через path-конвертацию.
 *
 * Fixtures лежат в `src/jvmTest/resources/fixtures/` и грузятся через classloader,
 * поэтому тесты запускаются на любом окружении (включая CI).
 */
class Fb2DiscoveryTest {
    @Test
    fun `discover article-small fb2`() {
        runDiscovery(label = "article-small", resource = ARTICLE_SMALL_RESOURCE)
    }

    @Test
    fun `discover article-yamshanov fb2`() {
        runDiscovery(label = "article-yamshanov", resource = ARTICLE_YAMSHANOV_RESOURCE)
    }

    private fun runDiscovery(
        label: String,
        resource: String,
    ) {
        val fb2File = extractResourceToTemp(resource, suffix = ".fb2")
        if (fb2File == null) {
            println("[discover] $resource not found in classpath — skipping")
            return
        }
        val report = buildReport(label, fb2File)
        println(report)
        val outDir = File("build/reports/notepen-discovery").apply { mkdirs() }
        File(outDir, "$label.txt").writeText(report)
    }

    private fun buildReport(
        label: String,
        file: File,
    ): String =
        buildString {
            appendLine("=== $label ===")
            appendLine("size: ${file.length() / 1024} KB")
            appendLine()
            val converter = JvmEbookToPdfConverter(Dispatchers.IO)
            val extractor =
                EbookAwarePdfReflowExtractor(
                    JvmPdfReflowExtractor(Dispatchers.IO),
                    converter,
                    converter,
                )
            val probeMark = TimeSource.Monotonic.markNow()
            val kind = runBlocking { extractor.probe(file.absolutePath) }
            appendLine("probe: ${probeMark.elapsedNow().inWholeMilliseconds}ms → $kind")
            val extractMark = TimeSource.Monotonic.markNow()
            val doc = runBlocking { extractor.extract(file.absolutePath) }
            appendLine("extract: ${extractMark.elapsedNow().inWholeMilliseconds}ms")
            appendLine()
            appendLine("doc kind: ${doc.kind}")
            appendLine("total blocks: ${doc.blocks.size}")

            appendLine()
            appendLine("Blocks by kind:")
            doc.blocks.groupingBy { it::class.simpleName ?: "?" }.eachCount().toSortedMap().forEach { (k, n) ->
                appendLine("  $k: $n")
            }

            val headings = doc.blocks.filterIsInstance<ReflowBlock.Heading>()
            appendLine()
            appendLine("Headings: ${headings.size}; by level: ${headings.groupingBy { it.level }.eachCount().toSortedMap()}")
            appendLine("First 30 headings:")
            headings.take(30).forEach { h ->
                appendLine("  L${h.level}: ${h.text.take(140)}")
            }

            val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
            appendLine()
            appendLine("Tables: ${tables.size}")
            tables.take(15).forEachIndexed { i, t ->
                val rows = t.rows.size
                val cols = t.rows.firstOrNull()?.cells?.size ?: 0
                appendLine("  #$i: $rows×$cols, conf=${"%.2f".format(t.confidence)}")
            }

            val figures = doc.blocks.filterIsInstance<ReflowBlock.Figure>()
            val fallback = figures.count { it.wasTableFallback }
            appendLine()
            appendLine("Figures: ${figures.size} total ($fallback from low-conf fallback)")

            val listItems = doc.blocks.filterIsInstance<ReflowBlock.ListItem>()
            val levelHistogram = listItems.groupingBy { it.level }.eachCount().toSortedMap()
            appendLine()
            appendLine("List items: ${listItems.size}; by level: $levelHistogram")

            val totalChars = doc.blocks.sumOf { blockChars(it) }
            appendLine()
            appendLine("Total text chars: $totalChars")

            appendLine()
            appendLine("First 3 paragraphs (preview):")
            doc.blocks.filterIsInstance<ReflowBlock.Paragraph>().take(3).forEachIndexed { i, p ->
                appendLine("  [$i] ${p.text.take(220)}")
            }

            appendLine()
            appendLine("=== end $label ===")
        }

    private fun blockChars(b: ReflowBlock): Int =
        when (b) {
            is ReflowBlock.Paragraph -> b.text.length
            is ReflowBlock.Heading -> b.text.length
            is ReflowBlock.ListItem -> b.text.length
            is ReflowBlock.Blockquote -> b.text.length
            is ReflowBlock.Table -> b.rows.sumOf { r -> r.cells.sumOf { c -> c.text.length } }
            else -> 0
        }

    companion object {
        const val ARTICLE_SMALL_RESOURCE: String = "fixtures/article-small.fb2"
        const val ARTICLE_YAMSHANOV_RESOURCE: String = "fixtures/article-yamshanov.fb2"
    }
}

/**
 * Распаковывает classpath-ресурс [resourcePath] во временный файл с указанным
 * [suffix] (например, `.fb2` для FictionBook). Возвращает `null`, если ресурс
 * не найден.
 */
internal fun extractResourceToTemp(
    resourcePath: String,
    suffix: String,
): File? {
    val url =
        Fb2DiscoveryTest::class.java.classLoader
            .getResource(resourcePath) ?: return null
    val temp = File.createTempFile("notepen-fixture-", suffix)
    temp.deleteOnExit()
    url.openStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
    return temp
}
