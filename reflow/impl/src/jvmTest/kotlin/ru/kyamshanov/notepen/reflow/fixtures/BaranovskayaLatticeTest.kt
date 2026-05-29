package ru.kyamshanov.notepen.reflow.fixtures

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import ru.kyamshanov.notepen.reflow.JvmPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.PageBitmapProvider
import ru.kyamshanov.notepen.reflow.api.PageRaster
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end проверка P7-Slice-2c (LatticeTableRefiner) на реальном PDF (Барановская).
 *
 * **Что делает:**
 *  1. `extract()` — baseline: сколько Figures с `wasTableFallback` родятся из Stream-фолбэка.
 *  2. `extractWithLattice()` с реальным [PageBitmapProvider] (PDFBox [PDFRenderer]) — сколько из них
 *     Lattice восстанавливает в Tables.
 *  3. Параллельно instrument'ируется per-page Lattice (страницы с fallback Figure'ми
 *     рендерятся отдельно, прогоняются через детектор), и считаются:
 *       - сколько страниц рендерится;
 *       - сколько детектор находит грид-линии (TableGrid не null);
 *       - сколько cells суммарно;
 *     Полные числа дампятся в `build/reports/notepen-discovery/baranovskaya_lattice.txt`.
 *
 * **Контракты, защищаемые тестом** (всё, что обязательно для корректности refiner'а):
 *  - Refiner НИКОГДА не теряет блоки (count preservation).
 *  - Не «прибавляет» Figures: `refined fallbacks ≤ baseline fallbacks`.
 *  - Не «убавляет» Tables: `refined tables ≥ baseline tables`.
 *  - Каждое recovered fallback соответствует ровно одной новой Table.
 *
 * **Что НЕ ассертится строго:** `recoveredFallbacks > 0`. Это валидная политика, что
 * Lattice не находит ничего, если в источнике нет нарисованных грид-линий
 * (Барановская — текстовый OCR-HYBRID без bordered tables). Диагностика записывается
 * в отчёт для оффлайн-анализа.
 *
 * Performance: PDFRenderer на 228 страницах при 1200 px ≈ 0.5–1.5 минуты, поэтому
 * **тест работает только по реальной фикстуре** (no-op в CI) и таймбюджет щедрый.
 */
class BaranovskayaLatticeTest {
    private val baranovskayaPath =
        "/Users/kruz18/Documents/english/Барановская_Грамматика_англ_языка_202509160919_58958.pdf"

    @Test
    fun lattice_refiner_runs_e2e_and_preserves_invariants() {
        val file = File(baranovskayaPath)
        if (!file.exists() || !file.canRead()) {
            println("[lattice] Baranovskaya fixture not found — skipping")
            return
        }
        val extractor = JvmPdfReflowExtractor(Dispatchers.IO)
        val baseline = runBlocking { extractor.extract(baranovskayaPath) }
        val baselineFigures = baseline.blocks.filterIsInstance<ReflowBlock.Figure>()
        val baselineFallbacks = baselineFigures.count { it.wasTableFallback }
        val baselineTables = baseline.blocks.filterIsInstance<ReflowBlock.Table>().size
        assertTrue(baselineFallbacks > 0, "expected stream-fallback Figures in baseline; got 0")

        Loader.loadPDF(file).use { doc ->
            val renderedPages = AtomicInteger(0)
            val provider = pdfBoxPageBitmapProvider(doc, renderedPages)
            val refined = runBlocking { extractor.extractWithLattice(baranovskayaPath, provider) }
            val diag = perPageLatticeDiagnostics(doc, baseline)
            verifyRefinerInvariants(
                baseline = baseline,
                refined = refined,
                baselineFigures = baselineFigures.size,
                baselineFallbacks = baselineFallbacks,
                baselineTables = baselineTables,
                renderedPages = renderedPages.get(),
                diag = diag,
            )
        }
    }

    /**
     * Прогоняет [LatticeTableDetector.detect] на каждой странице с fallback Figure
     * напрямую: измеряет, сколько pages детектор расценил как «таблица с гридом» и
     * суммарное количество cells. Не модифицирует pipeline — чисто диагностика.
     */
    private fun perPageLatticeDiagnostics(
        doc: PDDocument,
        baseline: ReflowDocument,
    ): LatticeDiagnostics {
        val pagesWithFallback =
            baseline.blocks
                .filterIsInstance<ReflowBlock.Figure>()
                .filter { it.wasTableFallback }
                .map { it.pageIndex }
                .toSortedSet()
        var rendered = 0
        var withGrid = 0
        var totalCells = 0
        val renderer = PDFRenderer(doc)
        for (pi in pagesWithFallback) {
            val widthPt = doc.getPage(pi).mediaBox.width
            if (widthPt <= 0f) continue
            val dpi = (LATTICE_TARGET_WIDTH_PX.toFloat() / widthPt) * INCH_PER_POINT_DENOMINATOR
            val image = renderer.renderImageWithDPI(pi, dpi, ImageType.RGB)
            val raster = bufferedToRaster(image)
            rendered++
            val grid =
                ru.kyamshanov.notepen.reflow.lattice.LatticeTableDetector
                    .detect(raster.pixels, raster.widthPx, raster.heightPx)
            if (grid != null) {
                withGrid++
                totalCells += grid.cells.size
            }
        }
        return LatticeDiagnostics(
            pagesWithFallback = pagesWithFallback.size,
            renderedDirect = rendered,
            withGrid = withGrid,
            totalCells = totalCells,
        )
    }

    private data class LatticeDiagnostics(
        val pagesWithFallback: Int,
        val renderedDirect: Int,
        val withGrid: Int,
        val totalCells: Int,
    )

    private fun verifyRefinerInvariants(
        baseline: ReflowDocument,
        refined: ReflowDocument,
        baselineFigures: Int,
        baselineFallbacks: Int,
        baselineTables: Int,
        renderedPages: Int,
        diag: LatticeDiagnostics,
    ) {
        val refinedFiguresList = refined.blocks.filterIsInstance<ReflowBlock.Figure>()
        val refinedFigures = refinedFiguresList.size
        val refinedFallbacks = refinedFiguresList.count { it.wasTableFallback }
        val refinedTables = refined.blocks.filterIsInstance<ReflowBlock.Table>().size
        val recoveredFallbacks = baselineFallbacks - refinedFallbacks
        val addedTables = refinedTables - baselineTables

        dumpReport(
            baselineFigures = baselineFigures,
            baselineFallbacks = baselineFallbacks,
            baselineTables = baselineTables,
            refinedFigures = refinedFigures,
            refinedFallbacks = refinedFallbacks,
            refinedTables = refinedTables,
            recoveredFallbacks = recoveredFallbacks,
            addedTables = addedTables,
            renderedPages = renderedPages,
            diag = diag,
        )

        assertTrue(
            recoveredFallbacks >= 0,
            "refined fallbacks ($refinedFallbacks) cannot exceed baseline ($baselineFallbacks)",
        )
        assertTrue(
            addedTables >= 0,
            "refined tables ($refinedTables) cannot be less than baseline ($baselineTables)",
        )
        assertTrue(
            refined.blocks.size == baseline.blocks.size,
            "refiner must preserve block count; baseline=${baseline.blocks.size} refined=${refined.blocks.size}",
        )
        assertTrue(
            recoveredFallbacks == addedTables,
            "recoveredFallbacks=$recoveredFallbacks != addedTables=$addedTables — типы блоков рассинхронизированы",
        )
        // Pipeline должен реально дёргать renderer'а — иначе Lattice path молча
        // disabled. На Барановской ~520 fallback'ов покрывают многие страницы;
        // pipeline дёргает renderPage хотя бы по разу на страницу с fallback.
        assertTrue(
            renderedPages > 0,
            "extractWithLattice не дёрнул PageBitmapProvider ни разу — pipeline сломан?",
        )
    }

    /**
     * Реализует [PageBitmapProvider] поверх PDFBox [PDFRenderer]. Считает вызовы
     * в [callCounter] — нужно для assert'а, что pipeline реально дёргает рендер.
     */
    private fun pdfBoxPageBitmapProvider(
        document: PDDocument,
        callCounter: AtomicInteger,
    ): PageBitmapProvider {
        val renderer = PDFRenderer(document)
        return label@{ pageIndex, targetWidthPx ->
            if (pageIndex !in 0 until document.numberOfPages) return@label null
            val page = document.getPage(pageIndex)
            val widthPt = page.mediaBox.width
            if (widthPt <= 0f) return@label null
            val dpi = (targetWidthPx.toFloat() / widthPt) * INCH_PER_POINT_DENOMINATOR
            val image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB)
            callCounter.incrementAndGet()
            bufferedToRaster(image)
        }
    }

    private fun bufferedToRaster(image: BufferedImage): PageRaster {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getRGB(0, 0, w, h, pixels, 0, w)
        return PageRaster(pixels = pixels, widthPx = w, heightPx = h)
    }

    @Suppress("LongParameterList")
    private fun dumpReport(
        baselineFigures: Int,
        baselineFallbacks: Int,
        baselineTables: Int,
        refinedFigures: Int,
        refinedFallbacks: Int,
        refinedTables: Int,
        recoveredFallbacks: Int,
        addedTables: Int,
        renderedPages: Int,
        diag: LatticeDiagnostics,
    ) {
        val outDir = File("build/reports/notepen-discovery")
        if (!outDir.exists() && !outDir.mkdirs()) return
        val report =
            buildString {
                appendLine("=== Lattice e2e on Baranovskaya ===")
                appendLine()
                appendLine("Baseline (extract only, no Lattice):")
                appendLine("  figures      = $baselineFigures")
                appendLine("  ↳ fallback   = $baselineFallbacks")
                appendLine("  tables       = $baselineTables")
                appendLine()
                appendLine("Refined (extractWithLattice + PDFBox renderer):")
                appendLine("  figures      = $refinedFigures")
                appendLine("  ↳ fallback   = $refinedFallbacks")
                appendLine("  tables       = $refinedTables")
                appendLine("  provider calls = $renderedPages")
                appendLine()
                appendLine("Delta (refiner work):")
                appendLine("  recovered fallbacks → tables = $recoveredFallbacks")
                appendLine("  added tables                 = $addedTables")
                val pct =
                    if (baselineFallbacks > 0) {
                        "%.1f".format(recoveredFallbacks * 100f / baselineFallbacks)
                    } else {
                        "n/a"
                    }
                appendLine("  recovery ratio              = $pct%")
                appendLine()
                appendLine("Per-page Lattice diagnostics (direct detector probe):")
                appendLine("  pages with fallback Figure    = ${diag.pagesWithFallback}")
                appendLine("  rendered & probed             = ${diag.renderedDirect}")
                appendLine("  detector reported a grid     = ${diag.withGrid}")
                appendLine("  total cells across grids     = ${diag.totalCells}")
                appendLine()
                if (diag.withGrid == 0) {
                    appendLine("NB: detector found no grid lines на ни одной странице с fallback.")
                    appendLine("    Это валидно для PDF без bordered tables (Барановская — text-OCR")
                    appendLine("    HYBRID): «таблицы» Stream — это aligned columns без линий.")
                }
            }
        File(outDir, "baranovskaya_lattice.txt").writeText(report)
        report.lines().forEach(::println)
    }

    private companion object {
        /** Pt → inch: 72 pt в дюйме. Используется в формуле DPI = (px/pt) × 72. */
        const val INCH_PER_POINT_DENOMINATOR = 72f

        /**
         * Целевая ширина рендера для diagnostic-probe. Совпадает с
         * `LatticeTableRefiner.DEFAULT_TARGET_WIDTH_PX` (1200) — чтобы дублирующий
         * проход видел тот же сигнал, что и refiner.
         */
        const val LATTICE_TARGET_WIDTH_PX = 1200
    }
}
