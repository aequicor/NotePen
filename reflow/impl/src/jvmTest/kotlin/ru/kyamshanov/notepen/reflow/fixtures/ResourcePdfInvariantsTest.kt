package ru.kyamshanov.notepen.reflow.fixtures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * Тесты-инварианты на in-repo PDF-фикстуре `thesis-mixed-content.pdf` — выпускная
 * квалификационная работа с разнообразным контентом (заголовки, нумерованные
 * списки, картинки, таблицы, библиография). В отличие от [RealPdfInvariantsTest]
 * (захардкоженные локальные пути), эта фикстура лежит в `src/jvmTest/resources/`
 * и доступна везде, включая CI.
 *
 * Цели:
 *  - **Probe correctness**: TEXT_BASED документ с native text-layer.
 *  - **Perf budgets**: extract быстрый на ~1 MB документе.
 *  - **Structural invariants**: block counts/types в наблюдаемых диапазонах.
 *  - **Known issues lock**: текущие баги (отсутствие heading-detection,
 *    false-positive list items из citation patterns) зафиксированы — будущее
 *    исправление сделает тесты падающими, что заставит ranges обновить.
 *
 * Discovery (2026-05-29) на thesis-mixed-content.pdf:
 *  - 455 blocks: 245 Paragraph, 170 ListItem, 22 Table, 18 Figure, 0 Heading.
 *  - extract 567ms, probe 284ms.
 *  - All Tables conf 0.58–0.86 (zero fallback), all Figures real images.
 *  - Bug: 0 headings — academic doc must have chapter titles. См. дальше.
 *  - Bug: ListItem `2024. С. 5.` — citation поймана как numbered list pattern.
 *  - Bug: ListItem с перепутанным контентом из multi-col comparison tables.
 */
class ResourcePdfInvariantsTest {
    @Test
    fun `thesis-mixed-content classifies as TEXT_BASED`() {
        val pdf = thesisFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val kind = runBlocking { extractor.probe(pdf.absolutePath) }
        assertEquals(PdfContentKind.TEXT_BASED, kind, "PDF with native text layer")
    }

    @Test
    fun `thesis extract under 2s on ~1MB document`() {
        val pdf = thesisFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val mark = TimeSource.Monotonic.markNow()
        runBlocking { extractor.extract(pdf.absolutePath) }
        val ms = mark.elapsedNow().inWholeMilliseconds
        assertTrue(ms < 2000L, "extract budget 2s, was ${ms}ms")
    }

    @Test
    fun `thesis block-kind distribution matches observed ranges`() {
        val pdf = thesisFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val counts = doc.blocks.groupingBy { it::class.simpleName ?: "?" }.eachCount()
        val paragraphs = counts["Paragraph"] ?: 0
        val tables = counts["Table"] ?: 0
        val headings = counts["Heading"] ?: 0
        val figures = counts["Figure"] ?: 0
        val listItems = counts["ListItem"] ?: 0

        // Discovery (2026-05-29): 245 / 170 / 22 / 18 / 0. Allowing ±25% drift
        // для будущих улучшений алгоритмов (кроме heading — он locked at 0
        // отдельным тестом ниже).
        assertInRange(paragraphs, 180..320, "paragraphs")
        assertInRange(listItems, 130..220, "list items")
        assertInRange(tables, 15..35, "tables")
        assertInRange(figures, 12..30, "figures")
    }

    @Test
    fun `thesis tables are confident — zero fall back to Figure`() {
        val pdf = thesisFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
        val figures = doc.blocks.filterIsInstance<ReflowBlock.Figure>()
        val fallback = figures.count { it.wasTableFallback }
        // На дипломной работе с чистыми native-таблицами Stream-детектор отрабатывает
        // безошибочно — все таблицы с conf ≥ 0.55, ни одного fallback'а в Figure.
        // Регрессия (>0 fallback) сигнализирует что Stream conf пороги поплыли.
        assertEquals(0, fallback, "expected no Stream-table fallback Figures on native-PDF thesis")
        assertTrue(tables.all { it.confidence >= 0.55f }, "all thesis tables should have conf ≥ 0.55")
    }

    @Test
    fun `thesis tables width within reason — no false positives with extreme cols`() {
        val pdf = thesisFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
        // TABLE_COLS_HARD_LIMIT=30 фильтрует wide false positives. На дипломе все
        // legitimate tables должны быть много́ уже — проверка sanity.
        tables.forEach { t ->
            val cols = t.rows.first().cells.size
            assertTrue(cols < 30, "table with $cols cols should have been filtered by colsPenalty")
        }
    }

    @Test
    fun `thesis text extraction yields substantial searchable content`() {
        val pdf = thesisFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val chars = doc.blocks.sumOf { textChars(it) }
        // Discovery: 136K chars. Floor 100K ловит regression text-extraction'а.
        assertTrue(chars > 100_000, "searchable text shrank, got $chars chars (expected ≥100K)")
    }

    @Test
    fun `thesis list hierarchy includes nested level 1`() {
        val pdf = thesisFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val listItems = doc.blocks.filterIsInstance<ReflowBlock.ListItem>()
        val nestedCount = listItems.count { it.level > 0 }
        // Discovery: 9 items на level=1. Lock at ≥1 — регрессия indent-based detection
        // (P8 Slice 2) ушла бы в 0.
        assertTrue(nestedCount > 0, "expected at least some nested list items, got 0")
    }

    /**
     * **KNOWN ISSUE**: на дипломной работе с явными главами/разделами ensemble
     * не детектит ни одного заголовка. Вероятные причины:
     *  - Font ratio для chapter titles слишком близок к [HEADING_RATIO]=1.15.
     *  - Текстовые заголовки оканчиваются терминальной пунктуацией («Введение.»).
     *
     * Тест locks current behavior — если кто-то улучшит heading ensemble, тест
     * упадёт, что заставит обновить и invariants, и алгоритм. См. open-tasks
     * в `project_notepen_reflow_progress.md`.
     */
    @Test
    fun `thesis headings NOT detected — known limitation`() {
        val pdf = thesisFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val headings = doc.blocks.filterIsInstance<ReflowBlock.Heading>()
        assertEquals(
            0,
            headings.size,
            "Heading detection on thesis is currently broken (font signal too tight). " +
                "When fixed, this test will start failing — update invariant.",
        )
    }

    // ── Article: org-structure risks (343 KB, 58 blocks) ────────────────────────

    @Test
    fun `article classifies as TEXT_BASED and extracts fast`() {
        val pdf = articleFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val mark = TimeSource.Monotonic.markNow()
        val kind = runBlocking { extractor.probe(pdf.absolutePath) }
        assertEquals(PdfContentKind.TEXT_BASED, kind)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val ms = mark.elapsedNow().inWholeMilliseconds
        assertEquals(PdfContentKind.TEXT_BASED, doc.kind)
        // Discovery: probe+extract вместе ~130ms. Бюджет 1s — щедрый.
        assertTrue(ms < 1000L, "article probe+extract budget 1s, was ${ms}ms")
    }

    @Test
    fun `article block-kind distribution matches observed ranges`() {
        val pdf = articleFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val counts = doc.blocks.groupingBy { it::class.simpleName ?: "?" }.eachCount()
        val paragraphs = counts["Paragraph"] ?: 0
        val listItems = counts["ListItem"] ?: 0
        // Discovery (2026-05-29): paragraphs=28, listItems=30. Allowing ±25% drift.
        assertInRange(paragraphs, 20..40, "article paragraphs")
        assertInRange(listItems, 22..40, "article list items")
    }

    @Test
    fun `article text content reaches floor`() {
        val pdf = articleFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val chars = doc.blocks.sumOf { textChars(it) }
        // Discovery: 14584. Floor 10K ловит регрессию text-extraction'а.
        assertTrue(chars > 10_000, "article searchable text shrank, got $chars chars (expected ≥10K)")
    }

    /**
     * Same heading-detection bug как в thesis (см. известный issue ниже). Locked
     * для регистрации текущего состояния — академические статьи с «Введение»,
     * «Заключение» и подобными секциями должны порождать Heading-блоки.
     */
    @Test
    fun `article headings NOT detected — known limitation`() {
        val pdf = articleFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val headings = doc.blocks.filterIsInstance<ReflowBlock.Heading>()
        assertEquals(
            0,
            headings.size,
            "Article «Введение» should be Heading but ensemble misses it. " +
                "Same root cause as thesis heading bug — see open tasks.",
        )
    }

    /**
     * Article упоминает «Методы приведены в таблице 1» в тексте, но Stream
     * детектор не нашёл table-структуру (видимо native-табличный layout не
     * матчит column-alignment эвристики). Locked для отслеживания.
     */
    @Test
    fun `article tables NOT detected — known under-detection`() {
        val pdf = articleFixture() ?: return
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(pdf.absolutePath) }
        val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
        assertEquals(0, tables.size, "article references table but Stream missed it; см. open tasks")
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun thesisFixture(): File? {
        val pdf = extractFixtureToTemp(ResourcePdfDiscoveryTest.FIXTURE_RESOURCE)
        if (pdf == null) {
            println("[fixture] thesis PDF not found in classpath — skipping invariants")
        }
        return pdf
    }

    private fun articleFixture(): File? {
        val pdf = extractFixtureToTemp(ResourcePdfDiscoveryTest.ARTICLE_ORG_RISKS_RESOURCE)
        if (pdf == null) {
            println("[fixture] article PDF not found in classpath — skipping invariants")
        }
        return pdf
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

    private fun assertInRange(
        actual: Int,
        range: IntRange,
        label: String,
    ) {
        assertTrue(actual in range, "$label=$actual outside expected range $range")
    }
}
