package ru.kyamshanov.notepen.reflow.fixtures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import java.io.File
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * Discovery-тест на in-repo PDF-фикстуре. В отличие от
 * [RealPdfDiscoveryTest] (захардкоженные локальные пути → CI no-op), эта
 * фикстура лежит в `src/jvmTest/resources/fixtures/` и грузится через
 * classloader, поэтому тест запускается ВЕЗДЕ — включая CI.
 *
 * Фикстура: `thesis-mixed-content.pdf` (~973 KB) — выпускная квалификационная работа,
 * содержащая разнообразный контент: заголовки разных уровней, нумерованные
 * перечисления, картинки, таблицы, библиография. Хороший «комбинированный»
 * test case для большинства алгоритмов P0–P8.
 */
class ResourcePdfDiscoveryTest {
    @Test
    fun `discover thesis-mixed-content`() {
        runDiscovery(label = "thesis-mixed-content", resource = FIXTURE_RESOURCE)
    }

    @Test
    fun `discover article-org-risks`() {
        runDiscovery(label = "article-org-risks", resource = ARTICLE_ORG_RISKS_RESOURCE)
    }

    private fun runDiscovery(
        label: String,
        resource: String,
    ) {
        val pdfFile = extractFixtureToTemp(resource)
        if (pdfFile == null) {
            println("[discover] $resource not found in classpath — skipping")
            return
        }
        val report = buildReport(label, pdfFile)
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
            val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
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
            tables.take(20).forEachIndexed { i, t ->
                val rows = t.rows.size
                val cols = t.rows.firstOrNull()?.cells?.size ?: 0
                appendLine("  #$i: $rows×$cols, conf=${"%.2f".format(t.confidence)}")
            }

            val figures = doc.blocks.filterIsInstance<ReflowBlock.Figure>()
            val fallback = figures.count { it.wasTableFallback }
            appendLine()
            appendLine("Figures: ${figures.size} total ($fallback from low-conf Stream-table fallback)")

            val listItems = doc.blocks.filterIsInstance<ReflowBlock.ListItem>()
            val levelHistogram = listItems.groupingBy { it.level }.eachCount().toSortedMap()
            appendLine()
            appendLine("List items: ${listItems.size}; by level: $levelHistogram")
            appendLine("First 20 list items:")
            listItems.take(20).forEach { li ->
                appendLine("  L${li.level}: ${li.text.take(120)}")
            }

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
        const val FIXTURE_RESOURCE: String = "fixtures/thesis-mixed-content.pdf"
        const val ARTICLE_ORG_RISKS_RESOURCE: String = "fixtures/article-org-risks.pdf"
    }
}

/**
 * Извлекает classpath-ресурс [resourcePath] во временный PDF и возвращает [File].
 * `null`, если ресурс не найден (CI без фикстуры).
 *
 * JVM PDFBox принимает только `File`-путь (не `InputStream`), поэтому copy-to-temp
 * — стандартный паттерн. Файл помечается `deleteOnExit()` чтобы не оставлять мусор.
 */
internal fun extractFixtureToTemp(resourcePath: String): File? {
    val url =
        ResourcePdfDiscoveryTest::class.java.classLoader
            .getResource(resourcePath) ?: return null
    val temp = File.createTempFile("notepen-fixture-", ".pdf")
    temp.deleteOnExit()
    url.openStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
    return temp
}
