package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import ru.kyamshanov.notepen.reflow.api.ReaderAlign
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.TextAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Word-parity рендер переносов на десктопе (Skiko): промоушен дефисов (`ReaderHyphenPromotion`)
 * превращает мягкие переносы на концах строк в НАСТОЯЩИЕ дефисы — их меряет и рисует сам движок,
 * а выключка по ширине учитывает их ширину. Пиксельно проверяет реальный путь
 * (resolveHyphenation → styledText → drawText(layout), без какой-либо дорисовки):
 *  - строка, разорванная по слогу, оканчивается видимым дефисом-глифом (чернила в дефис-ячейке);
 *  - при JUSTIFY дефис стоит заподлицо с правым краем колонки;
 *  - ПРАВЕЕ края колонки чернил нет — прежняя дорисовка поверх раскладки тут бы провалилась;
 *  - инвариант fixed-point: ни одна строка не кончается «голым» мягким переносом.
 *
 * Канарейка апгрейда Skiko: если движок сам начнёт рисовать дефис на U+00AD, эти пиксельные
 * прогоны заметят сдвиг — пересмотреть ReaderHyphenPromotion.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalTestApi::class)
class WordParityHyphenRenderTest {
    @Test
    fun justifiedBrokenLineEndsWithEngineHyphenFlushAtEdge() = wordParityCheck(ReaderAlign.JUSTIFY, expectFlush = true)

    @Test
    fun startAlignedBrokenLineEndsWithEngineHyphen() = wordParityCheck(ReaderAlign.START, expectFlush = false)

    /** Обмер пагинации и рендер-путь (включая фоновую подсветку выделения) дают одну раскладку. */
    @Test
    fun measureMatchesRenderPathWithLiveAnchors() =
        runDesktopComposeUiTest {
            val settings = ReflowReaderSettings(hyphenation = true, align = ReaderAlign.JUSTIFY)
            val style = settings.paragraphStyle()

            var measured: MeasuredBlock? = null
            var renderHeight = -1
            var renderBottoms: List<Float> = emptyList()
            setContent {
                val tm = rememberTextMeasurer()
                val density = LocalDensity.current
                measured =
                    BlockHeightCalculator.measure(
                        block = ReflowBlock.Paragraph(PROSE),
                        index = 0,
                        contentWidthPx = WIDTH_PX,
                        settings = settings,
                        textMeasurer = tm,
                        density = density,
                        figureHeights = emptyMap(),
                    )
                // Рендер-путь: тот же resolve + styledText с ЖИВОЙ подсветкой выделения — фоновый
                // спан не должен менять метрики (иначе активное выделение двигало бы пагинацию).
                val resolved = tm.resolveHyphenation(readerHyphenation(PROSE, true), emptyList(), settings, style, WIDTH_PX)
                val anchors = listOf(TextAnchor(blockIndex = 0, charStart = 5, charEnd = 40))
                val layout =
                    tm.measure(
                        styledText(resolved.hyphenation, emptyList(), anchors, settings),
                        style,
                        constraints = Constraints(maxWidth = WIDTH_PX),
                    )
                renderHeight = layout.size.height
                renderBottoms = List(layout.lineCount) { layout.getLineBottom(it) }
                Box(Modifier) {}
            }
            waitForIdle()

            assertEquals(measured!!.heightPx, renderHeight, "обмер и рендер разошлись по высоте блока")
            assertEquals(measured!!.lineBottoms, renderBottoms, "обмер и рендер разошлись по строкам")
        }

    private fun wordParityCheck(
        align: ReaderAlign,
        expectFlush: Boolean,
    ) = runDesktopComposeUiTest {
        val settings = ReflowReaderSettings(hyphenation = true, align = align)
        val style = settings.paragraphStyle()

        var captured: ImageBitmap? = null
        var dashLine = -1
        var dashLineSpaced = false
        var bareShyEnds = -1
        var lineTop = 0
        var lineBottom = 0
        var lineRight = 0f
        var dashAdvancePx = 0f
        setContent {
            val tm = rememberTextMeasurer()
            val density = LocalDensity.current
            val resolved = tm.resolveHyphenation(readerHyphenation(PROSE, true), emptyList(), settings, style, WIDTH_PX)
            val layout = resolved.layout
            val t = resolved.hyphenation.hyphenated
            bareShyEnds = 0
            for (line in 0 until layout.lineCount - 1) {
                val end = layout.getLineEnd(line, visibleEnd = false)
                if (end !in 1..t.length) continue
                when (t[end - 1]) {
                    SOFT_HYPHEN -> bareShyEnds++
                    PROMOTED_HYPHEN -> {
                        // Для flush-проверки нужна строка С ПРОБЕЛОМ: строку из одного куска
                        // длинного слова межсловной выключке нечем растягивать (как и в Word).
                        val spaced = t.substring(layout.getLineStart(line), end).contains(' ')
                        if (dashLine < 0 || (expectFlush && spaced && !dashLineSpaced)) {
                            dashLine = line
                            dashLineSpaced = spaced
                        }
                    }
                }
            }
            if (dashLine >= 0) {
                lineTop = layout.getLineTop(dashLine).toInt()
                lineBottom = layout.getLineBottom(dashLine).toInt()
                lineRight = layout.getLineRight(dashLine)
                dashAdvancePx = tm.measure(PROMOTED_HYPHEN.toString(), style).size.width.toFloat()
            }
            val w = WIDTH_PX + MARGIN_PX
            val h = layout.size.height.coerceAtLeast(1)
            val bmp = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
                drawRect(Color.White)
                // ТОЛЬКО движок: дефис обязан быть частью раскладки, не дорисовкой.
                drawText(layout)
            }
            captured = bmp
            Box(Modifier) {}
        }
        waitForIdle()

        assertTrue(dashLine >= 0, "ни одна строка не разорвана по слогу с промоутнутым дефисом")
        assertEquals(0, bareShyEnds, "fixed point оставил строку с «голым» мягким переносом на конце")
        if (expectFlush) {
            assertTrue(dashLineSpaced, "не нашлось промоутнутой строки с пробелом — нечем проверять выключку")
            assertTrue(lineRight >= WIDTH_PX - FLUSH_EPSILON_PX, "дефис не заподлицо с краем: lineRight=$lineRight")
        }
        val pm = captured!!.toPixelMap()
        val dashCellFrom = (lineRight - dashAdvancePx).toInt().coerceAtLeast(0)
        val dashCellTo = lineRight.toInt().coerceAtMost(pm.width)
        val inkInDashCell = countInk(pm, dashCellFrom, dashCellTo, lineTop, lineBottom.coerceAtMost(pm.height))
        assertTrue(inkInDashCell > 0, "нет чернил дефиса у правого края строки $dashLine")
        // Поле правее края колонки (с допуском на свес/сглаживание крайнего глифа) чистое.
        val inkInMargin = countInk(pm, WIDTH_PX + OVERHANG_TOLERANCE_PX, pm.width, 0, pm.height)
        assertEquals(0, inkInMargin, "чернила в поле правее края колонки — дефис вылез за край?")
    }

    private fun countInk(
        pm: androidx.compose.ui.graphics.PixelMap,
        xFrom: Int,
        xTo: Int,
        yFrom: Int,
        yTo: Int,
    ): Int {
        var ink = 0
        for (y in yFrom until yTo) {
            for (x in xFrom until xTo) {
                val px = pm[x, y]
                if (px.red < INK_THRESHOLD && px.green < INK_THRESHOLD && px.blue < INK_THRESHOLD) ink++
            }
        }
        return ink
    }

    private companion object {
        /** Узкая колонка — форсирует разрывы по слогам. */
        const val WIDTH_PX = 220

        /** Запас справа от колонки — там НЕ должно быть чернил. */
        const val MARGIN_PX = 40

        /** Допуск на округление выключенного края. */
        const val FLUSH_EPSILON_PX = 2f

        /** Свес/сглаживание крайнего глифа не считаем «вылезанием за край». */
        const val OVERHANG_TOLERANCE_PX = 3

        const val INK_THRESHOLD = 0.5f

        /** Длинные русские слова — гарантированные переносы в узкой колонке. */
        const val PROSE =
            "Электроэнцефалографического исследования распределение потенциалов " +
                "перепроверяется многократно, потому что мультиплатформенность " +
                "предусматривает воспроизводимость измерений."
    }
}
