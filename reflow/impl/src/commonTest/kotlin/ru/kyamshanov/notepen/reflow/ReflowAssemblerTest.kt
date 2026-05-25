package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReflowAssemblerTest {
    @Test
    fun `classify empty document as image only`() {
        assertEquals(PdfContentKind.IMAGE_ONLY, ReflowAssembler.classify(emptyList()))
    }

    @Test
    fun `classify pages with text as text based`() {
        val pages = listOf(page(line("hello world", top = 100f)), page(line("second page", top = 100f)))
        assertEquals(PdfContentKind.TEXT_BASED, ReflowAssembler.classify(pages))
    }

    @Test
    fun `classify mixed pages as hybrid`() {
        val pages = listOf(page(line("has text", top = 100f)), page(emptyList()))
        assertEquals(PdfContentKind.HYBRID, ReflowAssembler.classify(pages))
    }

    @Test
    fun `classify pages without text as image only`() {
        assertEquals(PdfContentKind.IMAGE_ONLY, ReflowAssembler.classify(listOf(page(emptyList()))))
    }

    @Test
    fun `merge tightly spaced lines into one paragraph`() {
        val glyphs = line("first line", top = 100f) + line("second line", top = 112f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        assertEquals(1, blocks.size)
        val paragraph = assertIs<ReflowBlock.Paragraph>(blocks.single())
        assertEquals("first line second line", paragraph.text)
    }

    @Test
    fun `generated-ebook line spacing does not over-split a paragraph`() {
        // Регресс: один абзац сгенерированного PDF (шаг строк 1.25 кегля, низкий
        // глиф-бокс ~0.5 кегля, как heightDir у PdfBox) дробился на отдельные
        // абзацы — прежний порог считал зазор по нижнему краю бокса. Шаг строк
        // тут стабилен, поэтому всё остаётся одним абзацем с пробелами.
        val fontSize = 13f
        val pitch = fontSize * 1.25f
        val frac = 0.5f
        val glyphs =
            line("first line of one paragraph", top = 100f, fontSize = fontSize, boxHeightFrac = frac) +
                line("second line same paragraph", top = 100f + pitch, fontSize = fontSize, boxHeightFrac = frac) +
                line("third line same paragraph", top = 100f + 2 * pitch, fontSize = fontSize, boxHeightFrac = frac)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        val paragraph = assertIs<ReflowBlock.Paragraph>(blocks.single())
        assertEquals("first line of one paragraph second line same paragraph third line same paragraph", paragraph.text)
    }

    @Test
    fun `real paragraph break with extra leading still splits at generated line spacing`() {
        // Двойная защита: при том же низком боксе настоящий разрыв абзаца (лишняя
        // выноска поверх обычного шага) обязан разделить текст на два абзаца.
        val fontSize = 13f
        val pitch = fontSize * 1.25f
        val frac = 0.5f
        val glyphs = mutableListOf<RawGlyph>()
        var top = 100f
        repeat(4) {
            glyphs += line("body line of paragraph one", top = top, fontSize = fontSize, boxHeightFrac = frac)
            top += pitch
        }
        top += fontSize * 0.9f // межабзацная выноска поверх обычного шага
        repeat(4) {
            glyphs += line("body line of paragraph two", top = top, fontSize = fontSize, boxHeightFrac = frac)
            top += pitch
        }
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        val paragraphs = blocks.filterIsInstance<ReflowBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals(
            "body line of paragraph one body line of paragraph one " +
                "body line of paragraph one body line of paragraph one",
            paragraphs[0].text,
        )
    }

    @Test
    fun `split paragraphs separated by a large gap`() {
        val glyphs = line("paragraph one", top = 100f) + line("paragraph two", top = 140f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        assertEquals(2, blocks.size)
        assertEquals("paragraph one", (blocks[0] as ReflowBlock.Paragraph).text)
        assertEquals("paragraph two", (blocks[1] as ReflowBlock.Paragraph).text)
    }

    @Test
    fun `detect heading by larger font size`() {
        val glyphs =
            line("Title", top = 50f, fontSize = 20f) +
                line("body text follows the heading", top = 100f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        assertEquals(2, blocks.size)
        val heading = assertIs<ReflowBlock.Heading>(blocks[0])
        assertEquals("Title", heading.text)
        assertEquals(1, heading.level)
        assertIs<ReflowBlock.Paragraph>(blocks[1])
    }

    @Test
    fun `join hyphenated word across line break`() {
        val glyphs = line("exam-", top = 100f) + line("ple sentence", top = 112f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        val paragraph = assertIs<ReflowBlock.Paragraph>(blocks.single())
        assertEquals("example sentence", paragraph.text)
    }

    @Test
    fun `place a figure between paragraphs in reading order`() {
        val glyphs = line("before image", top = 100f) + line("after image", top = 200f)
        val image = ReflowRect(left = 50f, top = 130f, right = 300f, bottom = 180f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs, images = listOf(image)))).blocks
        assertEquals(3, blocks.size)
        assertEquals("before image", (blocks[0] as ReflowBlock.Paragraph).text)
        val figure = assertIs<ReflowBlock.Figure>(blocks[1])
        // bounds нормализованы в [0..1] относительно страницы 600×800
        assertAlmostEquals(50f / 600f, figure.bounds.left)
        assertAlmostEquals(130f / 800f, figure.bounds.top)
        assertAlmostEquals(300f / 600f, figure.bounds.right)
        assertAlmostEquals(180f / 800f, figure.bounds.bottom)
        assertEquals("after image", (blocks[2] as ReflowBlock.Paragraph).text)
    }

    @Test
    fun `no space inserted before trailing punctuation`() {
        // глиф-запятая стоит с зазором, как часто бывает в PDF, но примыкает к слову
        val blocks = ReflowAssembler.assemble(listOf(page(line("word , next", top = 100f)))).blocks
        assertEquals("word, next", (blocks.single() as ReflowBlock.Paragraph).text)
    }

    @Test
    fun `bold and monospace styles propagate to source spans`() {
        val glyphs =
            line("bold", top = 100f, bold = true) +
                line("code", top = 100f, startX = 120f, monospace = true)
        val paragraph = assertIs<ReflowBlock.Paragraph>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals("bold code", paragraph.text)
        assertTrue(paragraph.source.filter { it.charEnd <= 4 }.all { it.bold && !it.monospace })
        assertTrue(paragraph.source.filter { it.charStart >= 5 }.all { it.monospace && !it.bold })
    }

    @Test
    fun `tightly spaced bullet lines become separate list items`() {
        val glyphs = line("- first item", top = 100f) + line("- second item", top = 112f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        assertEquals(2, blocks.size)
        assertEquals("- first item", assertIs<ReflowBlock.ListItem>(blocks[0]).text)
        assertEquals("- second item", assertIs<ReflowBlock.ListItem>(blocks[1]).text)
    }

    @Test
    fun `numbered lines become list items`() {
        val glyphs = line("1. first", top = 100f) + line("2. second", top = 112f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        assertEquals(2, blocks.size)
        assertIs<ReflowBlock.ListItem>(blocks[0])
        assertIs<ReflowBlock.ListItem>(blocks[1])
    }

    @Test
    fun `wrapped continuation line merges into the list item`() {
        val glyphs = line("- long item", top = 100f) + line("continued here", top = 112f)
        val item = assertIs<ReflowBlock.ListItem>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals("- long item continued here", item.text)
    }

    @Test
    fun `decimal number does not start a list item`() {
        val blocks = ReflowAssembler.assemble(listOf(page(line("3.14 is pi", top = 100f)))).blocks
        assertIs<ReflowBlock.Paragraph>(blocks.single())
    }

    @Test
    fun `em-dash continuation line stays in the paragraph, not a list item`() {
        // тире в начале перенесённой строки — знак предложения, а не маркер списка
        val glyphs = line("request looks like code", top = 100f) + line("— returns a response", top = 112f)
        val block = assertIs<ReflowBlock.Paragraph>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals("request looks like code — returns a response", block.text)
    }

    @Test
    fun `space before a monospace method call is preserved`() {
        // ведущая «.» в коде начинает токен (.method) — пробел перед ним не снимаем
        val glyphs = line("call", top = 100f) + line(".method", top = 100f, startX = 120f, monospace = true)
        val block = assertIs<ReflowBlock.Paragraph>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals("call .method", block.text)
    }

    @Test
    fun `compound hyphen across line break keeps the hyphen without a space`() {
        val glyphs = line("Plugin-", top = 100f) + line("Name here", top = 112f)
        val block = assertIs<ReflowBlock.Paragraph>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals("Plugin-Name here", block.text)
    }

    @Test
    fun `aligned columns across rows form a table`() {
        // три строки по три колонки (большие горизонтальные зазоры, ряды по вертикали)
        val glyphs =
            line("Name", top = 100f, startX = 50f) +
                line("Type", top = 100f, startX = 250f) +
                line("Default", top = 100f, startX = 450f) +
                line("Age", top = 130f, startX = 50f) +
                line("Int", top = 130f, startX = 250f) +
                line("Zero", top = 130f, startX = 450f) +
                line("City", top = 160f, startX = 50f) +
                line("Text", top = 160f, startX = 250f) +
                line("None", top = 160f, startX = 450f)
        val table = assertIs<ReflowBlock.Table>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals(3, table.rows.size)
        assertEquals(listOf("Name", "Type", "Default"), table.rows[0].cells.map { it.text })
        assertEquals(listOf("Age", "Int", "Zero"), table.rows[1].cells.map { it.text })
        assertEquals(listOf("City", "Text", "None"), table.rows[2].cells.map { it.text })
    }

    @Test
    fun `wrapped cell merges into one row of the table`() {
        val glyphs =
            line("Key", top = 100f, startX = 50f) +
                line("First part", top = 100f, startX = 250f) +
                line("wrapped", top = 112f, startX = 250f) + // перенос ячейки col1, тот же ряд
                line("Key2", top = 150f, startX = 50f) +
                line("Second", top = 150f, startX = 250f)
        val table = assertIs<ReflowBlock.Table>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Key", "First part wrapped"), table.rows[0].cells.map { it.text })
        assertEquals(listOf("Key2", "Second"), table.rows[1].cells.map { it.text })
    }

    @Test
    fun `table cell provenance indexes the cell text`() {
        val glyphs =
            line("AA", top = 100f, startX = 50f) + line("BB", top = 100f, startX = 250f) +
                line("CC", top = 130f, startX = 50f) + line("DD", top = 130f, startX = 250f)
        val table = assertIs<ReflowBlock.Table>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        val cell = table.rows[0].cells[0]
        assertEquals("AA", cell.text)
        assertTrue(cell.source.isNotEmpty())
        cell.source.forEach { assertTrue(it.charStart in 0..cell.text.length && it.charEnd in it.charStart..cell.text.length) }
    }

    @Test
    fun `wide first cell still splits at the column boundary learned from other rows`() {
        // ряд 1: col0 почти вплотную к col1 (зазор < порога) — но граница колонки
        // известна из соседних рядов, поэтому деление идёт по позиции глифа
        val wide = "A".repeat(32) // правый край ~242pt при старте 50 и ширине символа 6
        val glyphs =
            line("K", top = 100f, startX = 50f) + line("V", top = 100f, startX = 250f) +
                line(wide, top = 130f, startX = 50f) + line("Z", top = 130f, startX = 250f) +
                line("M", top = 160f, startX = 50f) + line("N", top = 160f, startX = 250f)
        val table = assertIs<ReflowBlock.Table>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals(3, table.rows.size)
        assertEquals(listOf(wide, "Z"), table.rows[1].cells.map { it.text })
    }

    @Test
    fun `single aligned line is not a table`() {
        // одна «колоночная» строка — это не таблица (нужно ≥2 строки)
        val glyphs = line("Key", top = 100f, startX = 50f) + line("Value", top = 100f, startX = 300f)
        assertIs<ReflowBlock.Paragraph>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
    }

    @Test
    fun `plain prose is not detected as a table`() {
        val glyphs = line("just some normal prose", top = 100f) + line("second line of the prose", top = 112f)
        assertIs<ReflowBlock.Paragraph>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
    }
}
