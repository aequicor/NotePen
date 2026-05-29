package ru.kyamshanov.notepen.reflow.fixtures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import java.io.File
import kotlin.test.Test
import kotlin.time.TimeSource

/**
 * Discovery-тесты на реальных PDF-фикстурах: прогоняют JVM-экстрактор
 * (без декораторов кэша/дедупа — чтобы видеть «чистый» результат) и
 * выводят структурную сводку в stdout + дамп в `build/reports/notepen-discovery/`.
 *
 * Цель: понять, что фактически выдаёт пайплайн на сложных реальных документах,
 * чтобы написать прицельные assertions в следующем шаге. Пути захардкожены —
 * фикстуры локальные, не в репозитории. Тест gracefully skip'ает, если файл
 * отсутствует (зелёный «no-op»).
 */
class RealPdfDiscoveryTest {
    @Test
    fun `discover Grammarway 3`() {
        discover(
            path = "/Users/kruz18/Downloads/Grammarway_3 1 (2).pdf",
            label = "Grammarway_3",
        )
    }

    @Test
    fun `discover Baranovskaya English Grammar`() {
        discover(
            path = "/Users/kruz18/Documents/english/Барановская_Грамматика_англ_языка_202509160919_58958.pdf",
            label = "Baranovskaya",
        )
    }

    private fun discover(
        path: String,
        label: String,
    ) {
        val file = File(path)
        if (!file.exists() || !file.canRead()) {
            println("[$label] fixture not found at $path — skipping discovery")
            return
        }

        val report = buildReport(label, file, path)
        println(report)
        val outDir = File("build/reports/notepen-discovery").apply { mkdirs() }
        File(outDir, "$label.txt").writeText(report)
    }

    private fun buildReport(
        label: String,
        file: File,
        path: String,
    ): String =
        buildString {
            appendLine("=== $label ===")
            appendLine("path: $path")
            appendLine("size: ${file.length() / (1024 * 1024)} MB")
            appendLine()
            val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
            val probeMark = TimeSource.Monotonic.markNow()
            val kind = runBlocking { extractor.probe(path) }
            appendLine("probe: ${probeMark.elapsedNow().inWholeMilliseconds}ms → $kind")
            val extractMark = TimeSource.Monotonic.markNow()
            val doc = runBlocking { extractor.extract(path) }
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
            appendLine("First 40 headings:")
            headings.take(40).forEach { h ->
                appendLine("  L${h.level}: ${h.text.take(120)}")
            }

            val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
            appendLine()
            appendLine("Tables: ${tables.size}")
            tables.take(25).forEachIndexed { i, t ->
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

            val totalChars = doc.blocks.sumOf { blockChars(it) }
            appendLine()
            appendLine("Total text chars: $totalChars")

            appendLine()
            appendLine("First 3 paragraphs (preview):")
            doc.blocks.filterIsInstance<ReflowBlock.Paragraph>().take(3).forEachIndexed { i, p ->
                appendLine("  [$i] ${p.text.take(200)}")
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
}
