package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import ru.kyamshanov.notepen.reflow.api.ReaderAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Диагностика поведения Skia при выключке по ширине (JUSTIFY) с мягкими переносами U+00AD.
 *
 * Эти факты — фундамент `ReaderHyphenPromotion` (промоушен дефисов до неподвижной точки):
 * - D1: разбивка строк НЕ зависит от выравнивания — JUSTIFY рвёт по тем же точкам, что START,
 *   включая разрывы по мягкому переносу. Значит, итерации fixed-point можно вести реальным стилем.
 * - D2: выключенные строки реально растянуты до ширины колонки (`getLineRight` ≈ width) — в том
 *   числе разорванные по слогу. Отсюда: у дорисованного поверх дефиса при JUSTIFY нет места.
 *   Исключение (как и в Word): строка без единого пробела — межсловной выключке нечего тянуть.
 * - D3 (информационно, stdout): величина слэка по строкам — чистая проза vs проза с длинным
 *   неразрывным code-токеном. Квантифицирует «дыры», неустранимые выключкой в принципе.
 *
 * Регрессионный пин против апгрейдов Skiko: если D1/D2 сломались — пересмотреть ReaderHyphenPromotion.
 */
@OptIn(ExperimentalTestApi::class)
class JustifyShyBreakDiagnosticTest {
    /** D1: JUSTIFY использует те же точки разрыва, что START, и рвёт по мягким переносам. */
    @Test
    fun justifyBreaksAtSoftHyphensLikeStart() =
        runDesktopComposeUiTest {
            val hyph = readerHyphenation(PROSE, true)
            assertTrue(hyph.hasSoftHyphens, "слогодел не вставил мягких переносов на JVM")

            var startEnds: List<Int> = emptyList()
            var justifyEnds: List<Int> = emptyList()
            setContent {
                val tm = rememberTextMeasurer()
                startEnds = lineEnds(layoutOf(tm, hyph, ReaderAlign.START, NARROW_WIDTH_PX))
                justifyEnds = lineEnds(layoutOf(tm, hyph, ReaderAlign.JUSTIFY, NARROW_WIDTH_PX))
                Box(Modifier) {}
            }
            waitForIdle()

            val shyEnded =
                justifyEnds.dropLast(1).count { end ->
                    end in 1..hyph.hyphenated.length && hyph.hyphenated[end - 1] == SOFT_HYPHEN
                }
            assertTrue(
                shyEnded > 0,
                "JUSTIFY не разорвал ни одной строки по мягкому переносу: ends=$justifyEnds (START=$startEnds)",
            )
            assertEquals(
                startEnds,
                justifyEnds,
                "разбивка строк зависит от выравнивания — fixed-point нельзя итерировать реальным стилем",
            )
        }

    /**
     * D2: при JUSTIFY строки с пробелами (кроме последней) растянуты до ширины колонки.
     * Строка без пробелов (кусок одного длинного слова) остаётся нерастянутой — это норма.
     */
    @Test
    fun justifyStretchesLinesToFullWidth() =
        runDesktopComposeUiTest {
            // Шрифто-зависимо: ширину выключенной строки задают метрики СИСТЕМНОГО шрифта (skiko
            // резолвит его из ОС), поэтому на Linux CI результат недетерминирован — как и снапшоты
            // ридера, тест локально-advisory и в CI-гейт не входит (ср. BaranovskayaSnapshotTest).
            // D1 (точки разрыва) и D3 (stdout) шрифто-устойчивы и в CI остаются.
            if (System.getenv("CI") != null) {
                println("[justify] CI — пропуск шрифто-зависимого теста ширины выключки")
                return@runDesktopComposeUiTest
            }
            val hyph = readerHyphenation(PROSE, true)

            // (right, есть ли пробел в строке) по строкам; счётчик слоговых строк полной ширины.
            var lines: List<Pair<Float, Boolean>> = emptyList()
            var shyEndedFull = 0
            setContent {
                val tm = rememberTextMeasurer()
                val layout = layoutOf(tm, hyph, ReaderAlign.JUSTIFY, NARROW_WIDTH_PX)
                val t = hyph.hyphenated
                lines =
                    List(layout.lineCount - 1) { line ->
                        val start = layout.getLineStart(line)
                        val end = layout.getLineEnd(line, visibleEnd = false)
                        layout.getLineRight(line) to t.substring(start, end).contains(' ')
                    }
                shyEndedFull =
                    (0 until layout.lineCount - 1).count { line ->
                        val end = layout.getLineEnd(line, visibleEnd = false)
                        end in 1..t.length &&
                            t[end - 1] == SOFT_HYPHEN &&
                            layout.getLineRight(line) >= NARROW_WIDTH_PX - JUSTIFY_EPSILON_PX
                    }
                Box(Modifier) {}
            }
            waitForIdle()

            lines.forEachIndexed { line, (right, hasSpace) ->
                if (hasSpace) {
                    assertTrue(
                        right >= NARROW_WIDTH_PX - JUSTIFY_EPSILON_PX,
                        "строка $line не растянута выключкой: right=$right при ширине $NARROW_WIDTH_PX (lines=$lines)",
                    )
                }
            }
            assertTrue(
                shyEndedFull > 0,
                "ни одна разорванная по слогу строка не растянута до полной ширины",
            )
        }

    /** D3: информационная сводка слэка — проза vs проза с неразрывным code-токеном (stdout). */
    @Test
    fun slackDiagnostics() =
        runDesktopComposeUiTest {
            var report = ""
            setContent {
                val tm = rememberTextMeasurer()
                report =
                    listOf(
                        "prose" to PROSE,
                        "withCode" to PROSE_WITH_CODE,
                    ).joinToString("\n") { (label, text) ->
                        val hyph = readerHyphenation(text, true)
                        val layout = layoutOf(tm, hyph, ReaderAlign.START, WIDE_WIDTH_PX)
                        val slacks = List(layout.lineCount - 1) { WIDE_WIDTH_PX - layout.getLineRight(it) }
                        val max = slacks.maxOrNull() ?: 0f
                        val avg = if (slacks.isEmpty()) 0f else slacks.sum() / slacks.size
                        "$label: lines=${layout.lineCount} maxSlack=${max.toInt()}px " +
                            "avgSlack=${avg.toInt()}px slacks=${slacks.map { it.toInt() }}"
                    }
                Box(Modifier) {}
            }
            waitForIdle()
            println("JustifyShyBreakDiagnostic D3 (width=${WIDE_WIDTH_PX}px):\n$report")
        }

    private fun layoutOf(
        tm: TextMeasurer,
        hyph: ReaderHyphenation,
        align: ReaderAlign,
        widthPx: Int,
    ): TextLayoutResult {
        val settings = ReflowReaderSettings(hyphenation = true, align = align)
        return tm.measure(
            styledText(hyph, emptyList(), emptyList(), settings),
            settings.paragraphStyle(),
            constraints = Constraints(maxWidth = widthPx),
        )
    }

    private fun lineEnds(layout: TextLayoutResult): List<Int> = List(layout.lineCount) { layout.getLineEnd(it, visibleEnd = false) }

    private companion object {
        /** Узкая колонка — максимизирует разрывы по слогам (как в DesktopHyphenRenderTest). */
        const val NARROW_WIDTH_PX = 220

        /** Колонка пошире для D3 — длинный code-токен должен влезать целиком. */
        const val WIDE_WIDTH_PX = 420

        /** Допуск на округление правого края выключенной строки. */
        const val JUSTIFY_EPSILON_PX = 2f

        val PROSE =
            "Электроэнцефалографического исследования распределение потенциалов " +
                "перепроверяется многократно, потому что мультиплатформенность " +
                "предусматривает воспроизводимость измерений на разнообразных устройствах."

        val PROSE_WITH_CODE =
            "Электроэнцефалографического исследования распределение потенциалов " +
                "перепроверяется через Modifier.fillMaxSize() многократно, потому что " +
                "мультиплатформенность предусматривает воспроизводимость измерений."
    }
}
