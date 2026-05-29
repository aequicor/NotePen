package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Покрывает распределение Figure'ов по XY-cut регионам: картинка должна попасть в
 * тот sub-region, чей bbox содержит её midpoint, а не дампиться в первый.
 *
 * Регрессия — old behavior: все Figure уходили на регион [0], multi-column PDF
 * с рисунками в правой колонке имели «прыгающие» иллюстрации в потоке reflow.
 *
 * **Force XY-cut path**: `segmentPage` зовётся только если `classify` вернул
 * не-TEXT_BASED. Для этого добавляем одну дополнительную пустую страницу в
 * `assemble`, чтобы `classify` дал HYBRID и XY-cut включился.
 */
class ImageRegionAssignmentTest {
    @Test
    fun image_in_right_column_attaches_to_right_region() {
        // Двухколоночная страница: левая X≈50..200, правая X≈400..550.
        // Картинка в правой колонке (X=420..520, Y=300..380) должна попасть туда.
        val leftGlyphs = columnLines(startX = 50f, prefix = "L")
        val rightGlyphs = columnLines(startX = 400f, prefix = "R")
        val rightImage = ReflowRect(left = 420f, top = 300f, right = 520f, bottom = 380f)
        val doc =
            assembleHybrid(
                page(glyphs = leftGlyphs + rightGlyphs, images = listOf(rightImage)),
            )

        val kinds = doc.blocks.map { it::class.simpleName }
        val figureIdx = kinds.indexOf("Figure")
        assertTrue(figureIdx >= 0, "expected figure in output; got $kinds")
        // Если figure прицепилась к правому региону, она идёт после **обоих**
        // колонок текста (левого и правого). Если бы она дамп'илась на [0]
        // (старое поведение), она была бы между левым и правым текстом.
        val paragraphsBeforeFigure = kinds.take(figureIdx).count { it == "Paragraph" }
        assertTrue(
            paragraphsBeforeFigure >= 2,
            "figure должна идти после обоих текстовых регионов; paragraphsBefore=$paragraphsBeforeFigure: $kinds",
        )
    }

    @Test
    fun image_outside_all_regions_falls_back_to_nearest() {
        val leftGlyphs = columnLines(startX = 50f, prefix = "A")
        val rightGlyphs = columnLines(startX = 400f, prefix = "B")
        // Картинка в нижней зоне страницы, вне обоих текстовых регионов:
        // должна прикрепиться к ближайшему (по факту — правому, т.к. её midX=300
        // ближе к правому центру; main thing — она НЕ теряется).
        val orphanImage = ReflowRect(left = 50f, top = 500f, right = 550f, bottom = 600f)
        val doc =
            assembleHybrid(
                page(glyphs = leftGlyphs + rightGlyphs, images = listOf(orphanImage)),
            )
        val nonFallbackFigures = doc.blocks.filterIsInstance<ReflowBlock.Figure>().count { !it.wasTableFallback }
        assertEquals(1, nonFallbackFigures, "orphan image не должна потеряться (nearest-region fallback)")
    }

    private fun columnLines(
        startX: Float,
        prefix: String,
    ): List<RawGlyph> =
        line(text = "${prefix}orem ipsum dolor sit amet", top = 100f, startX = startX) +
            line(text = "${prefix}onsectetur adipiscing elit", top = 130f, startX = startX) +
            line(text = "${prefix}ed do eiusmod tempor incididunt", top = 160f, startX = startX) +
            line(text = "${prefix}t labore et dolore magna aliqua", top = 190f, startX = startX)

    /**
     * Запускает `ReflowAssembler.assemble` принудительно в HYBRID-режиме: добавляет
     * пустую страницу, чтобы `classify` дал HYBRID (и XY-cut включился).
     */
    private fun assembleHybrid(realPage: RawPage): ru.kyamshanov.notepen.reflow.api.ReflowDocument {
        val pages = listOf(realPage, page(glyphs = emptyList(), pageIndex = 1))
        require(ReflowAssembler.classify(pages) == PdfContentKind.HYBRID) {
            "expected HYBRID, got ${ReflowAssembler.classify(pages)}"
        }
        return ReflowAssembler.assemble(pages)
    }
}
