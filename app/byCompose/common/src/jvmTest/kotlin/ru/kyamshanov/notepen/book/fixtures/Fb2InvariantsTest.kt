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
 * Тесты-инварианты на FB2-фикстурах в `src/jvmTest/resources/`. Покрывают
 * EbookAware decorator chain: FB2 → PDF (через [JvmEbookToPdfConverter]) →
 * стандартный [JvmPdfReflowExtractor].
 *
 * Discovery (2026-05-29):
 *  - **article-small.fb2** (28 KB): 302 blocks (301 Paragraph + 1 Heading),
 *    14411 chars, 0 lists, 0 tables. Heading text — хэш metadata (баг конвертера).
 *  - **article-yamshanov.fb2** (334 KB): 28 blocks (27 Paragraph + 1 Heading),
 *    7809 chars. Heading text — «Unknown» (тот же metadata-баг).
 *
 * Known issues (lock'aются тестами):
 *  - FB2-Heading с garbage text — конвертер эмитит metadata-поля как Heading.
 *  - 0 list items в FB2-paths против 30 в PDF той же статьи — FB2-paragraph
 *    boundary detection другое (каждый `<p>` → отдельный paragraph).
 */
class Fb2InvariantsTest {
    // ── article-small.fb2 ───────────────────────────────────────────────────────

    @Test
    fun `article-small fb2 classifies as TEXT_BASED with extractable text`() {
        val fb2 = articleSmallFixture() ?: return
        val extractor = buildExtractor()
        val kind = runBlocking { extractor.probe(fb2.absolutePath) }
        assertEquals(PdfContentKind.TEXT_BASED, kind, "FB2 conversion produces text PDF")
        val doc = runBlocking { extractor.extract(fb2.absolutePath) }
        val chars = doc.blocks.sumOf { textChars(it) }
        // Discovery: 14411. Floor 10K — content одна статья.
        assertTrue(chars > 10_000, "article-small text shrank, got $chars (expected ≥10K)")
    }

    @Test
    fun `article-small fb2 block distribution`() {
        val fb2 = articleSmallFixture() ?: return
        val doc = runBlocking { buildExtractor().extract(fb2.absolutePath) }
        val counts = doc.blocks.groupingBy { it::class.simpleName ?: "?" }.eachCount()
        // Discovery: 301 paragraphs + 1 heading. FB2-конвертер делает много мелких
        // параграфов (каждый <p> отдельно), unlike PDF где group'ятся.
        assertInRange(counts["Paragraph"] ?: 0, 250..360, "article-small paragraphs")
        assertEquals(1, counts["Heading"] ?: 0, "FB2 metadata emits exactly 1 Heading")
        assertEquals(0, counts["ListItem"] ?: 0, "FB2 path doesn't detect list items currently")
        assertEquals(0, counts["Table"] ?: 0, "no tables in article")
    }

    @Test
    fun `article-small fb2 extract under 10s on first cold run`() {
        val fb2 = articleSmallFixture() ?: return
        val extractor = buildExtractor()
        val mark = TimeSource.Monotonic.markNow()
        runBlocking { extractor.extract(fb2.absolutePath) }
        val ms = mark.elapsedNow().inWholeMilliseconds
        // Cold run includes FB2→PDF render. Cache hit — single-digit ms. 10s — щедро.
        assertTrue(ms < 10_000L, "FB2 extract budget 10s, was ${ms}ms")
    }

    // ── article-yamshanov.fb2 ───────────────────────────────────────────────────

    @Test
    fun `article-yamshanov fb2 classifies as TEXT_BASED with paragraphs`() {
        val fb2 = articleYamshanovFixture() ?: return
        val extractor = buildExtractor()
        val kind = runBlocking { extractor.probe(fb2.absolutePath) }
        assertEquals(PdfContentKind.TEXT_BASED, kind)
        val doc = runBlocking { extractor.extract(fb2.absolutePath) }
        // Discovery: 7809 chars.
        val chars = doc.blocks.sumOf { textChars(it) }
        assertTrue(chars > 5_000, "yamshanov text shrank, got $chars (expected ≥5K)")
    }

    @Test
    fun `article-yamshanov fb2 block distribution`() {
        val fb2 = articleYamshanovFixture() ?: return
        val doc = runBlocking { buildExtractor().extract(fb2.absolutePath) }
        val counts = doc.blocks.groupingBy { it::class.simpleName ?: "?" }.eachCount()
        // Discovery: 27 paragraphs + 1 heading.
        assertInRange(counts["Paragraph"] ?: 0, 20..40, "yamshanov paragraphs")
        assertEquals(1, counts["Heading"] ?: 0, "FB2 emits exactly 1 metadata Heading")
        assertEquals(0, counts["ListItem"] ?: 0, "no lists detected via FB2 path")
        assertEquals(0, counts["Table"] ?: 0, "no tables")
    }

    // ── Cross-cutting known issues ──────────────────────────────────────────────

    /**
     * **KNOWN ISSUE**: FB2 conversion path эмитит metadata-поля (id-хэш, заголовок
     * "Unknown") как [ReflowBlock.Heading]. Это баг `JvmBookPdfRenderer`, который
     * рендерит metadata так, что font/layout signals heading-ensemble на них
     * срабатывают. Locked для отслеживания будущего фикса.
     */
    @Test
    fun `fb2 single Heading contains metadata garbage — known issue`() {
        val fb2 = articleSmallFixture() ?: return
        val doc = runBlocking { buildExtractor().extract(fb2.absolutePath) }
        val heading = doc.blocks.filterIsInstance<ReflowBlock.Heading>().single()
        // Heading text — это metadata, NOT смысловой заголовок. Locked.
        // Real fix: либо renderer не делает metadata так чтобы оно ловилось ensemble'м,
        // либо post-pass фильтрует Heading с очень короткой alphanumeric не-кириллицей.
        val isMd5Hash = heading.text.matches(Regex("[a-f0-9]{32}"))
        val isGarbage =
            isMd5Hash ||
                heading.text == "Unknown" ||
                heading.text.length < 4
        assertTrue(isGarbage, "expected metadata garbage in FB2 heading, got: '${heading.text}'")
    }

    /**
     * Same heading-detection limitation как в PDF path: реальные секции
     * («Введение», заголовок статьи) НЕ детектятся как Heading'и. В FB2-paths
     * это особенно заметно потому что эта структура есть в исходном FB2 XML
     * (`<title>`, `<section>` теги), но при конвертации в PDF теряется.
     */
    @Test
    fun `fb2 article structural section titles NOT detected — known limitation`() {
        val fb2 = articleSmallFixture() ?: return
        val doc = runBlocking { buildExtractor().extract(fb2.absolutePath) }
        val paragraphs = doc.blocks.filterIsInstance<ReflowBlock.Paragraph>().map { it.text.trim() }
        // «Введение», «Заключение» — должны быть Heading'ами, но остаются Paragraph'ами.
        assertTrue(
            paragraphs.any { it == "Введение" || it.startsWith("Введение") },
            "«Введение» должно присутствовать в тексте",
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun buildExtractor(): PdfReflowExtractor {
        val converter = JvmEbookToPdfConverter(Dispatchers.IO)
        return EbookAwarePdfReflowExtractor(
            JvmPdfReflowExtractor(Dispatchers.IO),
            converter,
            converter,
        )
    }

    private fun articleSmallFixture(): File? {
        val f = extractResourceToTemp(Fb2DiscoveryTest.ARTICLE_SMALL_RESOURCE, suffix = ".fb2")
        if (f == null) println("[fixture] article-small.fb2 not in classpath — skipping")
        return f
    }

    private fun articleYamshanovFixture(): File? {
        val f = extractResourceToTemp(Fb2DiscoveryTest.ARTICLE_YAMSHANOV_RESOURCE, suffix = ".fb2")
        if (f == null) println("[fixture] article-yamshanov.fb2 not in classpath — skipping")
        return f
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
