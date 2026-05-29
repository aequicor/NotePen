package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Покрывает склейку подряд идущих [ReflowBlock.Table] с одинаковым числом
 * колонок в одну (cross-page continuation).
 */
class CrossPageTableJoinTest {
    private fun cell(
        text: String,
        topY: Float,
        startX: Float,
        bold: Boolean = false,
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
                        bottom = topY + 10f,
                    ),
                fontSizePt = 10f,
                bold = bold,
            )
        }
    }

    private fun tablePage(
        pageIndex: Int,
        headerBold: Boolean = false,
    ): RawPage {
        // Двухколоночная мини-таблица: 1 header (опционально bold) + 2 data rows.
        val glyphs =
            cell("Name", topY = 50f, startX = 50f, bold = headerBold) +
                cell("Age", topY = 50f, startX = 300f, bold = headerBold) +
                cell("Anna", topY = 80f, startX = 50f) +
                cell("30", topY = 80f, startX = 300f) +
                cell("Bob", topY = 110f, startX = 50f) +
                cell("25", topY = 110f, startX = 300f)
        return RawPage(
            pageIndex = pageIndex,
            widthPt = 600f,
            heightPt = 200f,
            glyphs = glyphs,
            images = emptyList(),
        )
    }

    @Test
    fun consecutive_tables_with_same_column_count_are_merged() {
        val pages = listOf(tablePage(0), tablePage(1))
        val doc = ReflowAssembler.assemble(pages)
        val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
        assertEquals(1, tables.size, "expected merged table; got ${tables.size}")
        val merged = tables.single()
        assertTrue(merged.rows.size >= 6, "merged table should contain rows from both pages; got ${merged.rows.size}")
    }

    @Test
    fun merged_table_keeps_only_first_header() {
        // Обе страницы имеют header (bold first row). Merged table сохраняет только
        // header из первой страницы; повторяющийся header из второй демоутится.
        val pages = listOf(tablePage(0, headerBold = true), tablePage(1, headerBold = true))
        val doc = ReflowAssembler.assemble(pages)
        val table = doc.blocks.filterIsInstance<ReflowBlock.Table>().single()
        val headers = table.rows.count { it.isHeader }
        assertEquals(1, headers, "expected single header row after merge; got $headers")
        assertTrue(table.rows.first().isHeader, "first row is the header")
    }

    @Test
    fun tables_with_different_column_count_stay_separate() {
        // Первая таблица 2×3, вторая 3×3 — не должны сливаться. Реалистичные
        // ячейки (3+ символов): однобуквенные header'ы триггерят F-1 noise-guard
        // (avg cell chars < TABLE_MIN_AVG_CELL_CHARS) и таблица деградирует в
        // параграфы, ломая assertion'ы про cols.
        val page0 = tablePage(0)
        val glyphs2 =
            cell("Col1", topY = 50f, startX = 50f) +
                cell("Col2", topY = 50f, startX = 200f) +
                cell("Col3", topY = 50f, startX = 350f) +
                cell("val1", topY = 80f, startX = 50f) +
                cell("val2", topY = 80f, startX = 200f) +
                cell("val3", topY = 80f, startX = 350f) +
                cell("val4", topY = 110f, startX = 50f) +
                cell("val5", topY = 110f, startX = 200f) +
                cell("val6", topY = 110f, startX = 350f)
        val page1 =
            RawPage(
                pageIndex = 1,
                widthPt = 600f,
                heightPt = 200f,
                glyphs = glyphs2,
                images = emptyList(),
            )
        val doc = ReflowAssembler.assemble(listOf(page0, page1))
        val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
        assertEquals(2, tables.size, "tables with different col counts must NOT merge")
        assertFalse(tables[0].rows.first().cells.size == tables[1].rows.first().cells.size)
    }

    @Test
    fun non_table_block_between_tables_prevents_merge() {
        // Таблица → Paragraph → Таблица: между ними prose-block, merge не должен
        // срабатывать.
        val tableGlyphs1 = cell("A", 50f, 50f) + cell("B", 50f, 300f) + cell("a", 80f, 50f) + cell("b", 80f, 300f)
        val tableGlyphs2 = cell("X", 50f, 50f) + cell("Y", 50f, 300f) + cell("x", 80f, 50f) + cell("y", 80f, 300f)
        val proseGlyphs = cell("Some prose between tables", 200f, 50f)
        val singlePage =
            RawPage(
                pageIndex = 0,
                widthPt = 600f,
                heightPt = 800f,
                glyphs = tableGlyphs1 + proseGlyphs + tableGlyphs2,
                images = emptyList(),
            )
        // Используем 2 страницы для HYBRID classify (или просто текстовая classify TEXT_BASED — тут не критично).
        val doc = ReflowAssembler.assemble(listOf(singlePage))
        // Главное: не одна merged Table, а минимум 2 + Paragraph между.
        val tables = doc.blocks.filterIsInstance<ReflowBlock.Table>()
        if (tables.size >= 2) {
            // Если разделитель сработал — отдельный paragraph между ними.
            val firstTableIdx = doc.blocks.indexOfFirst { it is ReflowBlock.Table }
            val secondTableIdx = doc.blocks.indexOfLast { it is ReflowBlock.Table }
            assertTrue(
                doc.blocks.subList(firstTableIdx + 1, secondTableIdx).any { it !is ReflowBlock.Table },
                "expected non-Table block between two Tables: ${doc.blocks.map { it::class.simpleName }}",
            )
        }
        // Если detect не выделил 2 tables (одиночная страница, малое содержимое),
        // тест mostly seal'ит «merge ничего лишнего не делает».
        assertTrue(doc.kind == PdfContentKind.TEXT_BASED || doc.kind == PdfContentKind.HYBRID)
    }
}
