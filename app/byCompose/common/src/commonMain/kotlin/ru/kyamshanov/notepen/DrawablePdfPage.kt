package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tablet.effectivePressure

/** Прозрачность заливки индикатора зоны ластика (AC-12, UI / UX § «Индикатор ластика»). */
private const val ERASER_INDICATOR_FILL_ALPHA = 0.35f

/** Толщина обводки индикатора зоны ластика, в пикселях canvas. */
private const val ERASER_INDICATOR_STROKE_WIDTH_PX = 2f

@Composable
fun DrawablePdfPage(
    bitmap: ImageBitmap,
    pdfDrawingState: PdfDrawingState,
    toolMode: ToolMode,
    penSettings: PenSettings,
    markerSettings: MarkerSettings,
    eraserSettings: EraserSettings,
    onGestureStart: (snapshot: List<DrawingPath>) -> Unit = {},
    onStrokeFinished: (path: DrawingPath) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    val tablet = LocalTabletInputController.current

    // Позиция пальца ластика в нормализованных координатах [0..1] относительно canvas.
    // null → жест ластика не активен (палец не на экране) → индикатор не отрисовывается.
    val eraserPos = remember { mutableStateOf<Offset?>(null) }

    // EC-1 / EC-2: при смене инструмента финализируем незавершённый штрих и
    // сбрасываем активную сессию стирания.
    LaunchedEffect(toolMode) {
        if (toolMode != ToolMode.PEN && toolMode != ToolMode.MARKER && pdfDrawingState.isDrawing.value) {
            pdfDrawingState.finishDrawing()
        }
        if (toolMode != ToolMode.ERASER) {
            eraserPos.value = null
        }
    }

    val indicatorColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize.value = it }
            .then(
                when (toolMode) {
                    // ПРИМЕЧАНИЕ к ключам pointerInput:
                    // pointerInput кэширует suspending-block по ключам и НЕ перезапускает
                    // его, пока ключи неизменны. Если в ключе только `toolMode`, замыкание
                    // onDragStart захватывает старое значение `penSettings` (или
                    // `eraserSettings`), и при движении слайдеров «Толщина» / «Прозрачность» /
                    // выборе нового цвета-пресета следующий штрих использует устаревшие
                    // значения. Поэтому ключи включают и сами settings — при их изменении
                    // gesture-handler перезапускается с актуальным замыканием. EC-1/EC-2
                    // (финализация незавершённого штриха) обрабатываются LaunchedEffect выше
                    // по `toolMode`, поэтому риск частичных штрихов при перезапуске
                    // pointerInput на лету покрыт.
                    ToolMode.PEN -> Modifier.pointerInput(toolMode, penSettings) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    onGestureStart(pdfDrawingState.currentPaths.toList())
                                    pdfDrawingState.strokeColorArgb.value = penSettings.colorArgb
                                    pdfDrawingState.strokeWidth.value = penSettings.strokeWidth
                                    pdfDrawingState.startDrawing(
                                        x = offset.x / w,
                                        y = offset.y / h,
                                        normalizedStrokeWidth = penSettings.strokeWidth / w,
                                        pressure = tablet.latestPressure.value,
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    pdfDrawingState.addPoint(
                                        x = change.position.x / w,
                                        y = change.position.y / h,
                                        pressure = change.effectivePressure(tablet.latestPressure.value),
                                    )
                                }
                            },
                            onDragEnd = {
                                val completed = pdfDrawingState.finishDrawing()
                                if (completed != null) onStrokeFinished(completed)
                            },
                        )
                    }

                    ToolMode.MARKER -> Modifier.pointerInput(toolMode, markerSettings) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    onGestureStart(pdfDrawingState.currentPaths.toList())
                                    pdfDrawingState.strokeColorArgb.value = markerSettings.colorArgb
                                    pdfDrawingState.strokeWidth.value = markerSettings.strokeWidth
                                    pdfDrawingState.startDrawing(
                                        x = offset.x / w,
                                        y = offset.y / h,
                                        normalizedStrokeWidth = markerSettings.strokeWidth / w,
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    pdfDrawingState.addPoint(
                                        change.position.x / w,
                                        change.position.y / h,
                                    )
                                }
                            },
                            onDragEnd = {
                                val completed = pdfDrawingState.finishDrawing()
                                if (completed != null) onStrokeFinished(completed)
                            },
                        )
                    }

                    ToolMode.ERASER -> Modifier.pointerInput(toolMode, eraserSettings) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    onGestureStart(pdfDrawingState.currentPaths.toList())
                                    val nx = offset.x / w
                                    val ny = offset.y / h
                                    eraserPos.value = Offset(nx, ny)
                                    pdfDrawingState.eraseInZone(
                                        centerX = nx,
                                        centerY = ny,
                                        halfSizeNormalized = eraserSettings.sizeNormalized / 2f,
                                        settings = eraserSettings,
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    val nx = change.position.x / w
                                    val ny = change.position.y / h
                                    eraserPos.value = Offset(nx, ny)
                                    pdfDrawingState.eraseInZone(
                                        centerX = nx,
                                        centerY = ny,
                                        halfSizeNormalized = eraserSettings.sizeNormalized / 2f,
                                        settings = eraserSettings,
                                    )
                                }
                            },
                            onDragEnd = { eraserPos.value = null },
                            onDragCancel = { eraserPos.value = null },
                        )
                    }

                    ToolMode.NONE -> Modifier
                }
            )
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "PDF Page",
            modifier = Modifier.fillMaxSize(),
        )

        // One reusable Path instance captured by the Canvas draw lambda.
        // Per-segment rendering reset()s it instead of allocating a new Path
        // for every cubic — at 200+ points per stroke × 240 fps this eliminates
        // the GC pressure that caused EDT stalls and AWT mouse-event coalescing
        // (the visible "lag → straight line" artefact during long pen strokes).
        val scratchPath = remember { Path() }
        Canvas(modifier = Modifier.fillMaxSize()) {
            pdfDrawingState.currentPaths.forEach { path ->
                drawStrokeWithPressure(path, size.width, size.height, scratchPath)
            }

            if (pdfDrawingState.isDrawing.value && pdfDrawingState.currentPath.value.points.size > 1) {
                drawStrokeWithPressure(
                    pdfDrawingState.currentPath.value, size.width, size.height, scratchPath,
                )
            }

            // Индикатор зоны ластика (AC-12). Отрисовывается только если палец на экране
            // и выбран ластик. Заливка полупрозрачная; обводка — сплошная.
            val pos = eraserPos.value
            if (toolMode == ToolMode.ERASER && pos != null) {
                val cx = pos.x * size.width
                val cy = pos.y * size.height
                val sizePx = eraserSettings.sizeNormalized * size.width
                val halfPx = sizePx / 2f
                when (eraserSettings.shape) {
                    EraserShape.CIRCLE -> {
                        drawCircle(
                            color = indicatorColor.copy(alpha = ERASER_INDICATOR_FILL_ALPHA),
                            radius = halfPx,
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = indicatorColor,
                            radius = halfPx,
                            center = Offset(cx, cy),
                            style = Stroke(width = ERASER_INDICATOR_STROKE_WIDTH_PX),
                        )
                    }
                    EraserShape.SQUARE -> {
                        val topLeft = Offset(cx - halfPx, cy - halfPx)
                        val rectSize = Size(sizePx, sizePx)
                        drawRect(
                            color = indicatorColor.copy(alpha = ERASER_INDICATOR_FILL_ALPHA),
                            topLeft = topLeft,
                            size = rectSize,
                        )
                        drawRect(
                            color = indicatorColor,
                            topLeft = topLeft,
                            size = rectSize,
                            style = Stroke(width = ERASER_INDICATOR_STROKE_WIDTH_PX),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders [stroke] with per-segment width modulated by [DrawingPoint.pressure].
 *
 * When every point has the same pressure (typical mouse / marker stroke), the
 * whole stroke is painted as a single Catmull-Rom-smoothed path — cheap and
 * visually identical to the legacy renderer. When pressure varies (tablet),
 * each segment is painted as its own short cubic with width derived from the
 * average pressure of its two endpoints; this trades one draw call for N but
 * gives the smooth taper users expect from pressure-sensitive pens.
 *
 * Segments are joined with [StrokeCap.Round] so the width steps are invisible.
 */
private fun DrawScope.drawStrokeWithPressure(
    stroke: DrawingPath,
    w: Float,
    h: Float,
    scratch: Path,
) {
    val points = stroke.points
    if (points.size < 2) return

    val color = Color(stroke.colorArgb.toInt())
    val baseWidth = stroke.strokeWidth * w

    val uniformPressure = points.first().pressure
    val pressureVaries = points.any { it.pressure != uniformPressure }

    if (!pressureVaries) {
        scratch.reset()
        points.appendCatmullRomTo(scratch, w, h)
        drawPath(
            path = scratch,
            color = color,
            style = Stroke(
                width = baseWidth * uniformPressure,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        return
    }

    // Per-segment render: split on sub-paths first so erased gaps stay gaps.
    val starts = points.indices.filter { i -> i == 0 || points[i].isNewPath }
    starts.forEachIndexed { si, start ->
        val end = if (si + 1 < starts.size) starts[si + 1] else points.size
        val seg = points.subList(start, end)
        if (seg.size < 2) return@forEachIndexed

        for (i in 0 until seg.size - 1) {
            val p0 = if (i > 0) seg[i - 1] else seg[0]
            val p1 = seg[i]
            val p2 = seg[i + 1]
            val p3 = if (i + 2 < seg.size) seg[i + 2] else seg[i + 1]

            val x1 = p1.x * w; val y1 = p1.y * h
            val x2 = p2.x * w; val y2 = p2.y * h

            scratch.reset()
            scratch.moveTo(x1, y1)
            scratch.cubicTo(
                x1 + (p2.x - p0.x) * w / 6f, y1 + (p2.y - p0.y) * h / 6f,
                x2 - (p3.x - p1.x) * w / 6f, y2 - (p3.y - p1.y) * h / 6f,
                x2, y2,
            )
            val avgPressure = (p1.pressure + p2.pressure) * 0.5f
            drawPath(
                path = scratch,
                color = color,
                style = Stroke(
                    width = baseWidth * avgPressure,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/**
 * Appends a smooth Catmull-Rom → cubic-Bézier approximation of the receiver's
 * points to [target]. Caller is responsible for [Path.reset]ting [target] first
 * if a fresh path is wanted — passing an existing Path lets callers reuse one
 * scratch instance across many strokes instead of allocating per frame.
 *
 * Sub-strokes (points with [DrawingPoint.isNewPath] == true) are appended as
 * independent smooth curves. Segments shorter than 3 points fall back to
 * straight lines so every recorded point is still included.
 */
private fun List<DrawingPoint>.appendCatmullRomTo(target: Path, w: Float, h: Float) {
    if (isEmpty()) return

    // Collect segment start indices (index 0 is always a start).
    val starts = indices.filter { i -> i == 0 || get(i).isNewPath }

    starts.forEachIndexed { si, start ->
        val end = if (si + 1 < starts.size) starts[si + 1] else size
        val seg = subList(start, end)

        target.moveTo(seg[0].x * w, seg[0].y * h)
        if (seg.size < 2) return@forEachIndexed
        if (seg.size == 2) {
            target.lineTo(seg[1].x * w, seg[1].y * h)
            return@forEachIndexed
        }

        // Catmull-Rom: for segment i→i+1 use ghost endpoints at the boundaries.
        for (i in 0 until seg.size - 1) {
            val p0 = if (i > 0) seg[i - 1] else seg[0]
            val p1 = seg[i]
            val p2 = seg[i + 1]
            val p3 = if (i + 2 < seg.size) seg[i + 2] else seg[i + 1]

            val x1 = p1.x * w; val y1 = p1.y * h
            val x2 = p2.x * w; val y2 = p2.y * h

            target.cubicTo(
                x1 + (p2.x - p0.x) * w / 6f, y1 + (p2.y - p0.y) * h / 6f,
                x2 - (p3.x - p1.x) * w / 6f, y2 - (p3.y - p1.y) * h / 6f,
                x2, y2,
            )
        }
    }
}
