package ru.kyamshanov.notepen.reflow.fixtures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.TextAnchor
import ru.kyamshanov.notepen.reflow.ui.ReaderPagination
import ru.kyamshanov.notepen.reflow.ui.ReaderPagination.BlockLayout
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration-тест P6 TextAnchor persistence на реальном документе (Барановская).
 *
 * **Сценарий, который покрывается:** пользователь читает на одних типографических
 * настройках → сохраняем якорь → пользователь меняет шрифт/ширину/preset →
 * пересчитываем пагинацию → `pageForAnchor` должен вернуть страницу, **которая
 * реально содержит** блок якоря. Без этого свойства якорь декоративен.
 *
 * **Что в тесте «реального»:**
 *  - реальный документ Барановской (~3300 блоков после Stream tightening);
 *  - распределение типов блоков (Heading, Paragraph, Table, Figure, ListItem),
 *    отражающее реальный учебник, а не синтетические fixtures.
 *
 * **Что упрощается:** обмер высот блоков. Реальный обмер делает
 * [ru.kyamshanov.notepen.reflow.ui.BlockHeightCalculator] через Compose TextMeasurer
 * — тяжёлая операция, требующая Compose Density. Здесь высоты считаются упрощённо
 * (proportional to character count + line wrapping), достаточно для свойств:
 *  - меньшая ширина страницы → больше строк в делимых блоках → больше окон;
 *  - блоки сохраняют монотонный порядок;
 *  - `pageForAnchor` находит окно, чей `firstBlock ≤ anchor.blockIndex` и которое
 *    либо начинается с anchor.blockIndex (тогда return), либо является последним
 *    с `firstBlock < anchor.blockIndex` (содержит блок внутри).
 *
 * Скип gracefully, если PDF отсутствует.
 */
class BaranovskayaAnchorIntegrationTest {
    private val baranovskayaPath =
        "/Users/kruz18/Documents/english/Барановская_Грамматика_англ_языка_202509160919_58958.pdf"

    @Test
    fun anchor_survives_pagination_change() {
        val file = File(baranovskayaPath)
        if (!file.exists() || !file.canRead()) {
            println("[anchor-int] Baranovskaya fixture not found — skipping")
            return
        }
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(baranovskayaPath) }
        assertTrue(doc.blocks.size > 500, "expected hundreds of blocks; got ${doc.blocks.size}")

        // «Preset 1» — обычная книжная ширина и средний кегль.
        val layoutsPreset1 = layoutBlocks(doc, contentWidthPx = 600, baseLineHeightPx = 24f)
        val windowsPreset1 =
            ReaderPagination.pageWindows(
                blocks = layoutsPreset1,
                pageHeightPx = 900f,
                spacingPx = 8f,
            )
        assertTrue(windowsPreset1.size > 20, "preset1 must produce many pages; got ${windowsPreset1.size}")

        // «Preset 2» — уже и крупнее (≈ long-reading): больше строк на блок → больше страниц.
        val layoutsPreset2 = layoutBlocks(doc, contentWidthPx = 420, baseLineHeightPx = 30f)
        val windowsPreset2 =
            ReaderPagination.pageWindows(
                blocks = layoutsPreset2,
                pageHeightPx = 900f,
                spacingPx = 12f,
            )
        // Замечание: общее число страниц preset1 vs preset2 не ассертим — синтетический
        // layout масштабирует Figure по ширине (height = width/aspect), поэтому
        // меньшая ширина даёт меньшие figures, и баланс пагинации непредсказуем.
        // Реальному UI это не свойственно (figures сохраняют natural size); для теста
        // достаточно, что обе пагинации непусты.
        assertTrue(windowsPreset2.isNotEmpty(), "preset2 paginated empty: ${windowsPreset2.size}")

        // Тестируем якоря через весь документ: equal-spaced sampling, охватывает разные
        // типы блоков и разные «глубины» в документе.
        val anchorBlocks = sampleBlockIndices(doc.blocks.size, sampleSize = 20)
        for (blockIdx in anchorBlocks) {
            val anchor = TextAnchor.ofBlock(blockIdx)
            assertAnchorResolves(anchor, windowsPreset1, "preset1")
            assertAnchorResolves(anchor, windowsPreset2, "preset2")
        }
    }

    /**
     * Проверяет инвариант [ReaderPagination.pageForAnchor]: возвращённая страница либо
     * начинается с блока якоря, либо начинается с блока строго меньше, и следующая
     * страница начинается с блока строго больше якоря (т.е. блок якоря лежит
     * целиком в этом окне).
     */
    private fun assertAnchorResolves(
        anchor: TextAnchor,
        windows: List<ReaderPagination.PageWindow>,
        label: String,
    ) {
        val page = ReaderPagination.pageForAnchor(windows, anchor)
        assertTrue(page in windows.indices, "$label: page=$page вне диапазона ${windows.indices}")
        val w = windows[page]
        val containsByExact = w.firstBlock == anchor.blockIndex
        val containsByRange =
            run {
                val next = windows.getOrNull(page + 1)
                w.firstBlock < anchor.blockIndex && (next == null || next.firstBlock > anchor.blockIndex)
            }
        assertTrue(
            containsByExact || containsByRange,
            "$label: page $page (firstBlock=${w.firstBlock}) не содержит anchor blockIndex=${anchor.blockIndex}",
        )
    }

    /**
     * Сэмплирование индексов блоков в [0..size): равномерное распределение из
     * [sampleSize] точек. Не используем Random — детерминированность важна, чтобы
     * падение теста воспроизводилось.
     */
    private fun sampleBlockIndices(
        size: Int,
        sampleSize: Int,
    ): List<Int> {
        if (size <= sampleSize) return (0 until size).toList()
        val step = (size - 1).toFloat() / (sampleSize - 1)
        val out = LinkedHashSet<Int>()
        for (i in 0 until sampleSize) out += (i * step).toInt()
        return out.toList()
    }

    @Test
    fun anchor_resolves_to_block_start_when_block_is_window_start() {
        val file = File(baranovskayaPath)
        if (!file.exists() || !file.canRead()) {
            println("[anchor-int] Baranovskaya fixture not found — skipping")
            return
        }
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val doc = runBlocking { extractor.extract(baranovskayaPath) }
        val layouts = layoutBlocks(doc, contentWidthPx = 600, baseLineHeightPx = 24f)
        val windows = ReaderPagination.pageWindows(layouts, pageHeightPx = 900f, spacingPx = 8f)
        assertTrue(windows.isNotEmpty(), "expected non-empty pagination")

        // Для каждого окна, начинающегося с нового блока, якорь на этот блок должен
        // указывать именно на это окно (а не на предыдущее, в котором закончился
        // прошлый блок).
        for ((pageIndex, window) in windows.withIndex()) {
            val prev = windows.getOrNull(pageIndex - 1)
            val isBlockStart = prev == null || prev.firstBlock != window.firstBlock
            if (isBlockStart) {
                val anchor = TextAnchor.ofBlock(window.firstBlock)
                val resolved = ReaderPagination.pageForAnchor(windows, anchor)
                assertEquals(
                    pageIndex,
                    resolved,
                    "якорь на блок ${window.firstBlock} должен указывать на страницу $pageIndex",
                )
            }
        }
    }

    /**
     * Приближённый обмер высот блоков, аналогичный [BlockHeightCalculator], но без
     * Compose-зависимостей:
     *  - Heading: 1.4× базовая высота строки (один обмер, не делимый).
     *  - Paragraph/ListItem/Blockquote: рассчитываем число строк = ceil(длина / charsPerLine),
     *    высота = lines × baseLineHeightPx. lineBottoms — кумулятивные точки строк.
     *  - Table: ceil(rows × 1.6 × baseLineHeightPx) — заведомо переборщено, но не делимая.
     *  - Figure: contentWidth / aspectRatio, минимум 100 px.
     *  - Divider: 1 px + 2× spacing (фактически 2 spacing).
     *
     * Это **не** замена настоящему обмеру — этого достаточно для проверки инвариантов
     * pageForAnchor, которые от точности высот не зависят (главное — что блоки
     * имеют положительную высоту и не делятся вне line bottoms).
     */
    private fun layoutBlocks(
        document: ReflowDocument,
        contentWidthPx: Int,
        baseLineHeightPx: Float,
    ): List<BlockLayout> {
        val avgCharWidthPx = baseLineHeightPx * AVG_CHAR_TO_LINE_HEIGHT
        val charsPerLine = max(1, (contentWidthPx / avgCharWidthPx).toInt())
        return document.blocks.map { block ->
            when (block) {
                is ReflowBlock.Heading -> {
                    val lines = max(1, ceil(block.text.length.toFloat() / charsPerLine).toInt())
                    BlockLayout(
                        heightPx = lines * baseLineHeightPx * HEADING_LINE_MULTIPLIER,
                        lineBottomsPx = emptyList(),
                        breakAfter = false, // heading shouldn't orphan, как и в реальном UI
                    )
                }
                is ReflowBlock.Paragraph -> splittableLayout(block.text, charsPerLine, baseLineHeightPx)
                is ReflowBlock.ListItem -> splittableLayout(block.text, charsPerLine, baseLineHeightPx)
                is ReflowBlock.Blockquote -> splittableLayout(block.text, charsPerLine, baseLineHeightPx)
                is ReflowBlock.Table -> {
                    val rows = max(1, block.rows.size)
                    BlockLayout(heightPx = rows * baseLineHeightPx * TABLE_ROW_MULTIPLIER, lineBottomsPx = emptyList())
                }
                is ReflowBlock.Figure -> {
                    val ar = block.aspectRatio.coerceAtLeast(0.1f)
                    val h = (contentWidthPx / ar).coerceAtLeast(MIN_FIGURE_HEIGHT_PX)
                    BlockLayout(heightPx = h, lineBottomsPx = emptyList())
                }
                ReflowBlock.Divider -> BlockLayout(heightPx = DIVIDER_HEIGHT_PX, lineBottomsPx = emptyList())
            }
        }
    }

    private fun splittableLayout(
        text: String,
        charsPerLine: Int,
        baseLineHeightPx: Float,
    ): BlockLayout {
        val lines = max(1, ceil(text.length.toFloat() / charsPerLine).toInt())
        return BlockLayout(
            heightPx = lines * baseLineHeightPx,
            lineBottomsPx = (1..lines).map { it * baseLineHeightPx },
        )
    }

    private companion object {
        /**
         * Грубое отношение средней ширины символа к высоте строки. ~0.5 — типично для
         * латиницы/кириллицы основного текста; для нашего обмера, который сравнивает
         * относительный объём блоков, достаточно.
         */
        const val AVG_CHAR_TO_LINE_HEIGHT = 0.5f

        /** Heading-multiplier: 1.4× — компромисс между крупным шрифтом и одной строкой. */
        const val HEADING_LINE_MULTIPLIER = 1.4f

        /** Высота строки в Table: «1.6 × кегль» — оценка с padding'ом. */
        const val TABLE_ROW_MULTIPLIER = 1.6f

        /** Минимум высоты для Figure (даже бесконечный aspect ratio даёт ≥ 100 px). */
        const val MIN_FIGURE_HEIGHT_PX = 100f

        /** Высота Divider — две доли spacing'а. Используется только для прохождения pagination. */
        const val DIVIDER_HEIGHT_PX = 16f
    }
}
