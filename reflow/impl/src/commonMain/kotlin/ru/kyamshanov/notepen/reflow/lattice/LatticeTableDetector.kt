package ru.kyamshanov.notepen.reflow.lattice

import ru.kyamshanov.notepen.reflow.lattice.Morphology.GridLine

/**
 * Детектор решётки (lattice) таблиц: ищет нарисованные на странице грид-линии (актуальные
 * пиксельные линии PDF, не текстовый layer) и собирает из их пересечений
 * прямоугольную сетку ячеек.
 *
 * Применение — OCR/сканированные PDF, где text-layer noisy и Stream-детектор
 * ([ru.kyamshanov.notepen.reflow.ReflowAssembler]) не справляется, но грид-линии
 * нарисованы аккуратно. На «обычных» PDF без нарисованной сетки возвращает
 * `null` — Stream остаётся основной стратегией.
 *
 * Phase 2a — только алгоритм: detect берёт ARGB `IntArray` и возвращает
 * пиксельные координаты. Интеграция с [ru.kyamshanov.notepen.reflow.ReflowDocument]
 * (нормализация в `[0..1]`, mapping глифов → cells, замена low-conf Figure) —
 * следующий слайс.
 */
internal object LatticeTableDetector {
    /**
     * Минимальная длина грид-линии в долях соответствующей стороны страницы:
     * 5% ≈ типичный минимум таблицы — короче не имеет смысла, и при этом отсекаются
     * подчёркивания текста и подписи к фигурам.
     */
    private const val MIN_LINE_LENGTH_FRACTION = 0.05f

    /** Допуск кластеризации параллельных грид-линий, в долях соответствующей стороны. */
    private const val LINE_CLUSTER_TOLERANCE_FRACTION = 0.005f

    /** Минимум грид-линий в каждом направлении: 2H + 2V = одна ячейка (1×1 таблица). */
    private const val MIN_GRID_LINES_PER_AXIS = 2

    /**
     * Прямоугольник в пиксельных координатах ARGB-буфера: левый/верхний
     * включительны, правый/нижний — exclusive (как в обычных bitmap-API).
     */
    data class PixelRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * Результат Lattice-детекции: список грид-линий по обеим осям, ячейки и общий
     * bbox таблицы. Координаты — пиксели исходного `IntArray` (row-major).
     *
     * @property horizontalLines грид-линии по Y (`position` = Y, `start..end` по X)
     * @property verticalLines грид-линии по X (`position` = X, `start..end` по Y)
     * @property cells ячейки — декартово произведение последовательных пар линий
     *   с уточнением по пересечениям; left/top inclusive, right/bottom exclusive
     * @property bounds общий bbox таблицы
     */
    data class TableGrid(
        val horizontalLines: List<GridLine>,
        val verticalLines: List<GridLine>,
        val cells: List<PixelRect>,
        val bounds: PixelRect,
    )

    /**
     * Запускает Lattice-пайплайн на ARGB-буфере [pixels] размером [width]×[height].
     * Возвращает `null`, если грид-линий недостаточно ([MIN_GRID_LINES_PER_AXIS] на
     * каждой оси) — на странице нет нарисованной таблицы либо сетка слишком
     * фрагментирована для надёжного восстановления.
     *
     * Стадии: бинаризация → run-length поиск длинных линий → кластеризация
     * близких perpPos в грид-линии → построение ячеек.
     *
     * @param luminanceThreshold порог «тёмного» пикселя (см. [Morphology.argbToBinary])
     */
    fun detect(
        pixels: IntArray,
        width: Int,
        height: Int,
        luminanceThreshold: Int = Morphology.DEFAULT_LUMINANCE_THRESHOLD,
    ): TableGrid? {
        if (width <= 0 || height <= 0 || pixels.size != width * height) return null
        val lines = extractGridLines(pixels, width, height, luminanceThreshold)
        val cells = buildCells(lines.horizontal, lines.vertical)
        val gridIsValid =
            lines.horizontal.size >= MIN_GRID_LINES_PER_AXIS &&
                lines.vertical.size >= MIN_GRID_LINES_PER_AXIS &&
                cells.isNotEmpty()
        return if (!gridIsValid) {
            null
        } else {
            TableGrid(
                horizontalLines = lines.horizontal,
                verticalLines = lines.vertical,
                cells = cells,
                bounds =
                    PixelRect(
                        left = lines.vertical.first().position,
                        top = lines.horizontal.first().position,
                        right = lines.vertical.last().position + 1,
                        bottom = lines.horizontal.last().position + 1,
                    ),
            )
        }
    }

    /** Грид-линии по обеим осям — внутренний возврат [extractGridLines]. */
    private data class AxisLines(
        val horizontal: List<GridLine>,
        val vertical: List<GridLine>,
    )

    private fun extractGridLines(
        pixels: IntArray,
        width: Int,
        height: Int,
        luminanceThreshold: Int,
    ): AxisLines {
        val minHRun = (width * MIN_LINE_LENGTH_FRACTION).toInt().coerceAtLeast(1)
        val minVRun = (height * MIN_LINE_LENGTH_FRACTION).toInt().coerceAtLeast(1)
        val tolY = (height * LINE_CLUSTER_TOLERANCE_FRACTION).toInt().coerceAtLeast(1)
        val tolX = (width * LINE_CLUSTER_TOLERANCE_FRACTION).toInt().coerceAtLeast(1)
        val binary = Morphology.argbToBinary(pixels, luminanceThreshold)
        val hRuns = Morphology.findHorizontalRuns(binary, width, height, minHRun)
        val vRuns = Morphology.findVerticalRuns(binary, width, height, minVRun)
        return AxisLines(
            horizontal = Morphology.clusterToGridLines(hRuns, tolY),
            vertical = Morphology.clusterToGridLines(vRuns, tolX),
        )
    }

    /**
     * Декартово произведение последовательных пар грид-линий → прямоугольники
     * ячеек. Линии должны идти по возрастанию `position` (что гарантировано
     * [Morphology.clusterToGridLines]). Ячейка эмитится, если она реально
     * непустая (left<right, top<bottom).
     *
     * В Phase 2a НЕ учитывается частичность линий: ячейка может быть пустой, даже
     * если горизонтальные/вертикальные линии в её углах прерывистые. Тонкая
     * настройка — Slice 2b (отсев ячеек без подтверждающих углов).
     */
    private fun buildCells(
        hLines: List<GridLine>,
        vLines: List<GridLine>,
    ): List<PixelRect> {
        val cells = mutableListOf<PixelRect>()
        for (rowIdx in 0 until hLines.size - 1) {
            val top = hLines[rowIdx].position
            val bottom = hLines[rowIdx + 1].position + 1
            if (bottom <= top) continue
            for (colIdx in 0 until vLines.size - 1) {
                val left = vLines[colIdx].position
                val right = vLines[colIdx + 1].position + 1
                if (right <= left) continue
                cells += PixelRect(left = left, top = top, right = right, bottom = bottom)
            }
        }
        return cells
    }
}
