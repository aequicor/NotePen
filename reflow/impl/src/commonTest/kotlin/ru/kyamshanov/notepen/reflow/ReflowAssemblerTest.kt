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
    fun `modest paragraph gap splits without over-splitting wrapped lines`() {
        // Регресс: после поднятия порога (до ×1.5 медианы ≈ 1.8 кегля) реальные
        // абзацы с умеренным межабзацным зазором слипались в один блок. Числа
        // сняты с измеренного PDF-гайда (см. ScratchPitchProbe): типичный
        // внутриабзацный шаг ≈1.20 кегля, разрыв абзаца ≈1.60 кегля — заметно
        // меньше «двойной» выноски (+0.9 кегля) из соседнего теста. Порог должен
        // лечь между ними: перенос строк (1.20) остаётся одним абзацем, разрыв
        // (1.60) делит на два.
        val fontSize = 10f
        val intraPitch = fontSize * 1.2f // обычная межстрочная выноска
        val breakPitch = fontSize * 1.6f // умеренный межабзацный шаг
        val glyphs = mutableListOf<RawGlyph>()
        var top = 100f
        repeat(4) {
            glyphs += line("body line of paragraph one", top = top, fontSize = fontSize)
            top += intraPitch
        }
        top += breakPitch - intraPitch // последняя пара = разрыв абзаца
        repeat(4) {
            glyphs += line("body line of paragraph two", top = top, fontSize = fontSize)
            top += intraPitch
        }
        val paragraphs = ReflowAssembler.assemble(listOf(page(glyphs))).blocks.filterIsInstance<ReflowBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals(
            "body line of paragraph one body line of paragraph one " +
                "body line of paragraph one body line of paragraph one",
            paragraphs[0].text,
        )
        assertEquals(
            "body line of paragraph two body line of paragraph two " +
                "body line of paragraph two body line of paragraph two",
            paragraphs[1].text,
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
        // Реалистичные 3+ char ячейки: 1-char glyph cells триггерят F-1
        // noise-guard (avg len < TABLE_MIN_AVG_CELL_CHARS) и таблица отказывается.
        val glyphs =
            line("Key", top = 100f, startX = 50f) + line("Val", top = 100f, startX = 250f) +
                line("Foo", top = 130f, startX = 50f) + line("Bar", top = 130f, startX = 250f)
        val table = assertIs<ReflowBlock.Table>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        val cell = table.rows[0].cells[0]
        assertEquals("Key", cell.text)
        assertTrue(cell.source.isNotEmpty())
        cell.source.forEach { assertTrue(it.charStart in 0..cell.text.length && it.charEnd in it.charStart..cell.text.length) }
    }

    @Test
    fun `wide first cell still splits at the column boundary learned from other rows`() {
        // ряд 1: col0 почти вплотную к col1 (зазор < порога) — но граница колонки
        // известна из соседних рядов, поэтому деление идёт по позиции глифа.
        // 3+ char ячейки в опорных рядах: 1-char glyph cells триггерили F-1 guard.
        val wide = "A".repeat(32) // правый край ~242pt при старте 50 и ширине символа 6
        val glyphs =
            line("Key", top = 100f, startX = 50f) + line("Val", top = 100f, startX = 250f) +
                line(wide, top = 130f, startX = 50f) + line("Vlu", top = 130f, startX = 250f) +
                line("Foo", top = 160f, startX = 50f) + line("Bar", top = 160f, startX = 250f)
        val table = assertIs<ReflowBlock.Table>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals(3, table.rows.size)
        assertEquals(listOf(wide, "Vlu"), table.rows[1].cells.map { it.text })
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

    @Test
    fun `wrapped closing paren is not mistaken for a numbered list item`() {
        // A code expression wraps so the next line starts with "3)," — the closing
        // paren of mutableListOf(1, 2, 3), not a list marker (it is followed by a comma).
        val glyphs =
            line("val list = mutableListOf(1, 2,", top = 100f) +
                line("3), then list add four runs", top = 112f)
        val block = ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single()
        assertIs<ReflowBlock.Paragraph>(block)
        assertTrue("mutableListOf" in block.text && "3)" in block.text, "text=${block.text}")
    }

    @Test
    fun `numbered marker followed by a letter is still a list item`() {
        val block = ReflowAssembler.assemble(listOf(page(line("3) third list entry", top = 100f)))).blocks.single()
        assertIs<ReflowBlock.ListItem>(block)
    }

    @Test
    fun `unicode soft hyphen joins across line break regardless of next line casing`() {
        // U+00AD — типографская подсказка «здесь можно переносить», не часть слова.
        // Соединяем безусловно, даже если следующая строка начинается с большой буквы
        // (что нетипично для soft-hyphen-переноса, но возможно в собственных именах).
        val glyphs = line("inter­", top = 100f) + line("Net", top = 112f)
        val block = ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single()
        val paragraph = assertIs<ReflowBlock.Paragraph>(block)
        assertEquals("interNet", paragraph.text)
    }

    @Test
    fun `unicode soft hyphen joins before lowercase as before`() {
        val glyphs = line("inter­", top = 100f) + line("national", top = 112f)
        val paragraph = assertIs<ReflowBlock.Paragraph>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals("international", paragraph.text)
    }

    @Test
    fun `ascii hyphen still keeps compound word before uppercase next line`() {
        // Регресс: оставляем существующее поведение для ASCII-дефиса в собственных
        // словах (Plugin-Name, Anti-American) — там дефис семантический.
        val glyphs = line("Plugin-", top = 100f) + line("Name follows", top = 112f)
        val paragraph = assertIs<ReflowBlock.Paragraph>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals("Plugin-Name follows", paragraph.text)
    }

    @Test
    fun `heading ensemble accepts large bold line with no terminal punctuation`() {
        // Заголовок: крупнее body + bold + не кончается точкой. Все 3 сигнала +
        // mandatory font-ratio — точно heading.
        val glyphs =
            line("Section Title", top = 50f, fontSize = 14f, bold = true) +
                line("body paragraph follows.", top = 100f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        val heading = assertIs<ReflowBlock.Heading>(blocks[0])
        assertEquals("Section Title", heading.text)
        assertIs<ReflowBlock.Paragraph>(blocks[1])
    }

    @Test
    fun `xy-cut splits two columns on hybrid pages into reading order left then right`() {
        // HYBRID = первая страница с текстом + вторая без текста (заставляет classify
        // вернуть HYBRID, чтобы XY-cut применился — на TEXT_BASED PDF XY-cut пропускаем,
        // см. ReflowAssembler.assemble).
        // Глифы: левая колонка (X 50..200) две строки + правая (X 400..550) две строки,
        // обе на тех же Y. Без XY-cut groupLines склеил бы левую и правую в одну Line
        // (порядок чтения: «left right left right»). С XY-cut: сначала вся левая, потом
        // правая.
        val textPage =
            page(
                line("LeftOne", top = 100f, startX = 50f) +
                    line("LeftTwo", top = 120f, startX = 50f) +
                    line("RightOne", top = 100f, startX = 400f) +
                    line("RightTwo", top = 120f, startX = 400f),
            )
        val emptyPage = page(glyphs = emptyList(), pageIndex = 1)
        val blocks = ReflowAssembler.assemble(listOf(textPage, emptyPage)).blocks
        // Порядок blocks — это «валюта» reading order: сначала все блоки левой колонки,
        // потом правой. С Y-gap 20 pt и default font 10 pt разрывы абзацев попадают,
        // но проверяем именно ПОРЯДОК (а не количество блоков).
        val leftBlocks = blocks.indices.filter { idx -> blocks[idx].textOrEmpty().contains("Left") }
        val rightBlocks = blocks.indices.filter { idx -> blocks[idx].textOrEmpty().contains("Right") }
        assertTrue(leftBlocks.isNotEmpty(), "expected blocks containing 'Left' text")
        assertTrue(rightBlocks.isNotEmpty(), "expected blocks containing 'Right' text")
        assertTrue(
            leftBlocks.max() < rightBlocks.min(),
            "all Left blocks must precede Right blocks in reading order " +
                "(left indices=$leftBlocks, right indices=$rightBlocks)",
        )
    }

    /** Текст блока, если применимо; иначе пусто. Для проверки порядка XY-cut. */
    private fun ReflowBlock.textOrEmpty(): String =
        when (this) {
            is ReflowBlock.Paragraph -> text
            is ReflowBlock.Heading -> text
            is ReflowBlock.ListItem -> text
            is ReflowBlock.Blockquote -> text
            else -> ""
        }

    @Test
    fun `flat list items get level 0`() {
        // Все элементы на одном отступе — все level 0.
        val glyphs =
            line("- first item", top = 100f, startX = 50f) +
                line("- second item", top = 112f, startX = 50f) +
                line("- third item", top = 124f, startX = 50f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        val items = blocks.filterIsInstance<ReflowBlock.ListItem>()
        assertEquals(3, items.size)
        assertTrue(items.all { it.level == 0 }, "expected all level 0, got ${items.map { it.level }}")
    }

    @Test
    fun `nested list items get level 1 by deeper indent`() {
        // Маркер на startX=110 при ширине страницы 600 → indent ≈ 0.183.
        // Первый маркер на 50 → 0.083. Разница 0.10 >> LIST_INDENT_TOLERANCE_NORM 0.015.
        val glyphs =
            line("- outer one", top = 100f, startX = 50f) +
                line("- nested one", top = 112f, startX = 110f) +
                line("- nested two", top = 124f, startX = 110f) +
                line("- outer two", top = 136f, startX = 50f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        val items = blocks.filterIsInstance<ReflowBlock.ListItem>()
        assertEquals(4, items.size)
        assertEquals(listOf(0, 1, 1, 0), items.map { it.level }, "outer→nested→nested→outer")
    }

    @Test
    fun `list flow resets after paragraph interruption`() {
        // Список → абзац → новый список: счётчик уровней начинается с 0 заново,
        // даже если отступ нового маркера больше старого.
        val glyphs =
            line("- first list item", top = 100f, startX = 110f) +
                line("regular paragraph here", top = 200f, startX = 50f) +
                line("- second list", top = 300f, startX = 110f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        val items = blocks.filterIsInstance<ReflowBlock.ListItem>()
        assertEquals(2, items.size)
        assertEquals(0, items[0].level, "первый list-элемент потока = 0")
        assertEquals(0, items[1].level, "после абзаца счётчик обнуляется")
    }

    @Test
    fun `heading ensemble rejects slightly larger font with terminal period and no other signals`() {
        // Раньше: font 1.2× body → точно heading. Теперь: нужен secondary signal,
        // которого нет (не bold, период в конце), поэтому остаётся абзацем.
        // Защита от false positive «слегка крупнее, но не заголовок».
        val glyphs =
            line("Slightly bigger first sentence.", top = 50f, fontSize = 12f) +
                line("regular body text continues here", top = 80f)
        val blocks = ReflowAssembler.assemble(listOf(page(glyphs))).blocks
        // Обе строки оказываются в одном Paragraph (либо в двух — главное, что не Heading).
        assertTrue(blocks.all { it !is ReflowBlock.Heading }, "no heading expected, got $blocks")
    }

    @Test
    fun `real four-row three-column table stays as table with high confidence`() {
        // Чистая узкокелейная таблица: реальные данные — короткие cells, много строк,
        // плотно заполнено. Stream-детектор должен выдать высокий confidence (>0.5) и
        // оставить блок Table, а не подменить на Figure-фолбэк.
        val rows =
            listOf(
                listOf("Name", "Type", "Default"),
                listOf("Age", "Int", "Zero"),
                listOf("City", "Text", "None"),
                listOf("Tags", "List", "Empty"),
            )
        val starts = listOf(50f, 250f, 450f)
        val tops = listOf(100f, 130f, 160f, 190f)
        val glyphs =
            rows.flatMapIndexed { rowIdx, row ->
                row.flatMapIndexed { colIdx, text ->
                    line(text, top = tops[rowIdx], startX = starts[colIdx])
                }
            }
        val table = assertIs<ReflowBlock.Table>(ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single())
        assertEquals(4, table.rows.size)
        assertTrue(table.confidence > 0.5f, "expected high confidence (>0.5), got ${table.confidence}")
    }

    @Test
    fun `wide-cell prose false-positive falls back to figure crop`() {
        // Случай defect (a): 3 строки × 2 «колонки», каждая ячейка — длинный кусок
        // текста (~60 символов). Stream-детектор по выровненным левым краям ставит
        // таблицу, но lengthPenalty уводит confidence ниже порога — пост-пасс
        // подменяет на Figure-кроп исходной страницы. Лучше показать crop, чем
        // ломать вёрстку «таблицей», в которую втянулся соседний абзац.
        val cellText = "A".repeat(60)
        // charWidth=3pt → 60 chars = 180pt; стартX=50 → правый край 230;
        // вторая колонка startX=270 (gap=40pt > COLUMN_GAP_FACTOR×fontSize=15pt).
        val tops = listOf(100f, 130f, 160f)
        val glyphs =
            tops.flatMap { top ->
                line(cellText, top = top, startX = 50f, charWidth = 3f) +
                    line(cellText, top = top, startX = 270f, charWidth = 3f)
            }
        val block = ReflowAssembler.assemble(listOf(page(glyphs))).blocks.single()
        assertIs<ReflowBlock.Figure>(block)
    }
}
