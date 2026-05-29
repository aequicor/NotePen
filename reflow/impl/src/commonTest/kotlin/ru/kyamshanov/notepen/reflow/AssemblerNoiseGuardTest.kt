package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers two noise-guard behaviours added during the 2026-05-29 wave-3-qa pass:
 *
 *  - **F-1**: `buildTable` returns null (range falls back to paragraphs) when the
 *    average non-empty cell text is under [TABLE_MIN_AVG_CELL_CHARS_TEST] —
 *    OCR/HYBRID column-gap detector tends to slice ordinary prose by glyph on
 *    scanned grammar books, producing pseudo-"tables" of 1-2 char fragments.
 *  - **F-3**: `isAcceptableHeading` rejects spaced-out OCR heading candidates
 *    ("В х о д н а я") where ≥3 "words" are emitted and over half of them are
 *    single letters — the row gets demoted to a Paragraph, no TOC entry.
 */
class AssemblerNoiseGuardTest {
    private fun cellGlyphs(
        text: String,
        topY: Float,
        startX: Float,
        bold: Boolean = false,
        fontSizePt: Float = 10f,
    ): List<RawGlyph> {
        val charW = 6f
        return text.mapIndexed { i, ch ->
            RawGlyph(
                text = ch.toString(),
                rect =
                    ReflowRect(
                        left = startX + i * charW,
                        top = topY,
                        right = startX + (i + 1) * charW,
                        bottom = topY + fontSizePt,
                    ),
                fontSizePt = fontSizePt,
                bold = bold,
            )
        }
    }

    @Test
    fun `ocr-noise rows of 1-2 char fragments do not survive as table`() {
        // Симулируем OCR-разрез прозы "Артикль (Основана в 1997 году)" в столбцы:
        // каждая «ячейка» 1-2 символа, ≥2 ряда подряд. Без F-1 guard'а assembler
        // считал это таблицей. После — должна стать Paragraph'ом (поток текста).
        val xs = listOf(50f, 130f, 210f, 290f, 370f, 450f)
        val tops = listOf(100f, 130f, 160f, 190f)
        val fragments = listOf("(О", "с", "нова", "на", "в", "1997")
        val glyphs =
            tops.flatMap { y ->
                xs.zip(fragments).flatMap { (x, frag) -> cellGlyphs(frag, topY = y, startX = x) }
            }
        val page = page(glyphs, widthPt = 600f, heightPt = 600f)

        val doc = ReflowAssembler.assemble(listOf(page))
        val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
        assertTrue(tables.isEmpty(), "OCR noise must not survive as Table; got ${tables.size}")
        // Текст всё ещё должен быть в потоке (как Paragraph) — не теряем содержимое.
        val paragraphs = doc.blocks.filterIsInstance<ReflowBlock.Paragraph>()
        assertTrue(paragraphs.isNotEmpty(), "noise rows must fall back to paragraphs, not be discarded")
    }

    @Test
    fun `real short-cell table with average above floor still becomes Table`() {
        // Контроль: легитимная таблица 2x3 со словами 3-5 chars (avg≈4) проходит.
        val page =
            page(
                cellGlyphs("Name", topY = 100f, startX = 50f, bold = true) +
                    cellGlyphs("Age", topY = 100f, startX = 300f, bold = true) +
                    cellGlyphs("Anna", topY = 130f, startX = 50f) +
                    cellGlyphs("30", topY = 130f, startX = 300f) +
                    cellGlyphs("Bob", topY = 160f, startX = 50f) +
                    cellGlyphs("25", topY = 160f, startX = 300f),
            )
        val doc = ReflowAssembler.assemble(listOf(page))
        val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
        assertTrue(tables.isNotEmpty(), "real table must not be killed by F-1 floor")
    }

    @Test
    fun `spaced-out OCR heading candidate is demoted to paragraph`() {
        // Один «крупный» (fontSizePt 18 vs body 10) ряд с расставленными буквами —
        // ensemble heuristic пытается промоутить, F-3 фильтр блокирует.
        val bodyFont = 10f
        val headingFont = 18f
        // body context — несколько обычных строк, чтобы median bodyFont сложился.
        val body =
            cellGlyphs("обычная строка тела", topY = 200f, startX = 50f, fontSizePt = bodyFont) +
                cellGlyphs("ещё одна строка тела", topY = 215f, startX = 50f, fontSizePt = bodyFont) +
                cellGlyphs("и ещё одна", topY = 230f, startX = 50f, fontSizePt = bodyFont)
        // "В х о д н а я" — 7 «слов» по 1 букве, расставлены.
        val xs = listOf(50f, 110f, 170f, 230f, 290f, 350f, 410f)
        val letters = listOf("В", "х", "о", "д", "н", "а", "я")
        val heading =
            xs.zip(letters).flatMap { (x, ch) ->
                cellGlyphs(ch, topY = 100f, startX = x, bold = true, fontSizePt = headingFont)
            }
        val page = page(heading + body)
        val doc = ReflowAssembler.assemble(listOf(page))

        val headings = doc.blocks.filterIsInstance<ReflowBlock.Heading>()
        assertFalse(
            headings.any { it.text.replace(Regex("\\s+"), "") == "Входная" },
            "spaced-out heading candidate must be demoted; got $headings",
        )
    }
}
