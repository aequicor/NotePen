package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Покрывает детект [ReflowBlock.TableRow.isHeader] по доле bold-ячеек в первой
 * строке таблицы.
 */
class TableHeaderDetectionTest {
    private fun cellGlyphs(
        text: String,
        topY: Float,
        startX: Float,
        bold: Boolean,
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

    @Test
    fun bold_first_row_is_marked_as_header() {
        // Двухколоночная таблица: первая строка bold (Name | Type), вторая нет.
        val page =
            RawPage(
                pageIndex = 0,
                widthPt = 600f,
                heightPt = 800f,
                glyphs =
                    cellGlyphs("Name", topY = 100f, startX = 50f, bold = true) +
                        cellGlyphs("Type", topY = 100f, startX = 300f, bold = true) +
                        cellGlyphs("user", topY = 130f, startX = 50f, bold = false) +
                        cellGlyphs("admin", topY = 130f, startX = 300f, bold = false) +
                        cellGlyphs("guest", topY = 160f, startX = 50f, bold = false) +
                        cellGlyphs("user", topY = 160f, startX = 300f, bold = false),
                images = emptyList(),
            )
        val doc = ReflowAssembler.assemble(listOf(page))
        val table = doc.blocks.filterIsInstance<ReflowBlock.Table>().firstOrNull()
        assertTrue(table != null, "expected Table; got ${doc.blocks.map { it::class.simpleName }}")
        assertTrue(table.rows.first().isHeader, "первая строка bold должна быть header")
        assertFalse(table.rows[1].isHeader, "вторая строка не должна быть header")
    }

    @Test
    fun all_bold_rows_keep_only_first_marked() {
        // Если все строки bold, всё равно только первая получает isHeader=true.
        val page =
            RawPage(
                pageIndex = 0,
                widthPt = 600f,
                heightPt = 800f,
                glyphs =
                    cellGlyphs("A", topY = 100f, startX = 50f, bold = true) +
                        cellGlyphs("B", topY = 100f, startX = 300f, bold = true) +
                        cellGlyphs("C", topY = 130f, startX = 50f, bold = true) +
                        cellGlyphs("D", topY = 130f, startX = 300f, bold = true),
                images = emptyList(),
            )
        val doc = ReflowAssembler.assemble(listOf(page))
        val table = doc.blocks.filterIsInstance<ReflowBlock.Table>().firstOrNull() ?: return
        assertTrue(table.rows.first().isHeader)
        assertFalse(table.rows[1].isHeader)
    }

    @Test
    fun non_bold_first_row_is_not_header() {
        // Обычная data-таблица без bold header — isHeader должно быть false.
        val page =
            RawPage(
                pageIndex = 0,
                widthPt = 600f,
                heightPt = 800f,
                glyphs =
                    cellGlyphs("x", topY = 100f, startX = 50f, bold = false) +
                        cellGlyphs("y", topY = 100f, startX = 300f, bold = false) +
                        cellGlyphs("z", topY = 130f, startX = 50f, bold = false) +
                        cellGlyphs("w", topY = 130f, startX = 300f, bold = false),
                images = emptyList(),
            )
        val doc = ReflowAssembler.assemble(listOf(page))
        val table = doc.blocks.filterIsInstance<ReflowBlock.Table>().firstOrNull() ?: return
        assertFalse(table.rows.first().isHeader)
    }
}
