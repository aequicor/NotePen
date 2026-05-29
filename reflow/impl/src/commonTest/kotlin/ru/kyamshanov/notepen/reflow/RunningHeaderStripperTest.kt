package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit-тесты [RunningHeaderStripper]: повторяющиеся колонтитулы должны
 * вырезаться, body-text — оставаться нетронутым.
 */
class RunningHeaderStripperTest {
    private val pageWidthPt = 600f
    private val pageHeightPt = 800f

    /** Глиф в указанной зоне: top=Y, высота = fontSize, X=0..text.length*charWidth. */
    private fun glyphsAt(
        text: String,
        topY: Float,
        startX: Float = 50f,
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
            )
        }
    }

    private fun pageOf(
        vararg lines: List<RawGlyph>,
        index: Int = 0,
    ): RawPage =
        RawPage(
            pageIndex = index,
            widthPt = pageWidthPt,
            heightPt = pageHeightPt,
            glyphs = lines.flatMap { it },
            images = emptyList(),
        )

    @Test
    fun strips_repeating_header_across_many_pages() {
        // 12 страниц, у каждой ввер — «Chapter 1», в середине — уникальный body.
        val pages =
            (0 until 12).map { i ->
                pageOf(
                    glyphsAt("Chapter 1", topY = 20f),
                    glyphsAt("body content $i", topY = 400f),
                    index = i,
                )
            }
        val cleaned = RunningHeaderStripper.strip(pages)
        cleaned.forEachIndexed { i, p ->
            val text = p.glyphs.joinToString("") { it.text }
            assertTrue("Chapter 1" !in text, "header still present on page $i: '$text'")
            assertTrue("body content" in text, "body was wrongly stripped on page $i: '$text'")
        }
    }

    @Test
    fun strips_page_numbers_with_digit_normalization() {
        // 15 страниц, у каждой внизу — «Page N» с разными N.
        val pages =
            (1..15).map { n ->
                pageOf(
                    glyphsAt("body $n", topY = 400f),
                    glyphsAt("Page $n", topY = 760f),
                    index = n - 1,
                )
            }
        val cleaned = RunningHeaderStripper.strip(pages)
        cleaned.forEachIndexed { i, p ->
            val text = p.glyphs.joinToString("") { it.text }
            assertTrue(!text.contains(Regex("Page \\d+")), "page-number footer remained at page $i: '$text'")
            assertTrue("body" in text, "body wrongly stripped at page $i")
        }
    }

    @Test
    fun keeps_unique_content_lines() {
        // 12 страниц с уникальными заголовками — НЕ должны стрипаться.
        val pages =
            (0 until 12).map { i ->
                pageOf(
                    glyphsAt("Unique title $i", topY = 20f),
                    glyphsAt("body $i", topY = 400f),
                    index = i,
                )
            }
        val cleaned = RunningHeaderStripper.strip(pages)
        cleaned.forEachIndexed { i, p ->
            val text = p.glyphs.joinToString("") { it.text }
            assertTrue("Unique title" in text, "unique top-zone title wrongly stripped at page $i: '$text'")
        }
    }

    @Test
    fun no_op_on_short_documents() {
        // < MIN_PAGES_FOR_DETECTION (10) → stripper не работает, даже если header
        // повторяется. Безопасный no-op на коротких документах.
        val pages =
            (0 until 5).map { i ->
                pageOf(
                    glyphsAt("Repeated", topY = 20f),
                    glyphsAt("body $i", topY = 400f),
                    index = i,
                )
            }
        val cleaned = RunningHeaderStripper.strip(pages)
        assertEquals(pages.flatMap { it.glyphs }.size, cleaned.flatMap { it.glyphs }.size)
    }

    @Test
    fun does_not_strip_body_text_in_top_zone() {
        // Текст у самой верхней границы страницы, но РАЗНЫЙ на каждой странице:
        // не stripper'у его трогать. Защищает от ложных срабатываний на верстке
        // без явного header'а.
        val pages =
            (0 until 12).map { i ->
                pageOf(
                    glyphsAt("page intro line ABCDE $i", topY = 20f),
                    glyphsAt("body $i", topY = 400f),
                    index = i,
                )
            }
        val cleaned = RunningHeaderStripper.strip(pages)
        cleaned.forEachIndexed { i, p ->
            val text = p.glyphs.joinToString("") { it.text }
            assertTrue("page intro line" in text, "unique top-line wrongly stripped at page $i")
        }
    }
}
