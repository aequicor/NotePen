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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint

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
    eraserSettings: EraserSettings,
    onGestureStart: (snapshot: List<DrawingPath>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }

    // Позиция пальца ластика в нормализованных координатах [0..1] относительно canvas.
    // null → жест ластика не активен (палец не на экране) → индикатор не отрисовывается.
    val eraserPos = remember { mutableStateOf<Offset?>(null) }

    // EC-1 / EC-2: при смене инструмента финализируем незавершённый штрих и
    // сбрасываем активную сессию стирания.
    LaunchedEffect(toolMode) {
        if (toolMode != ToolMode.PEN && pdfDrawingState.isDrawing.value) {
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
                            onDragEnd = { pdfDrawingState.finishDrawing() },
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
                                    pdfDrawingState.erasePointsInZone(
                                        centerX = nx,
                                        centerY = ny,
                                        halfSizeNormalized = eraserSettings.sizeNormalized / 2f,
                                        shape = eraserSettings.shape,
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    val nx = change.position.x / w
                                    val ny = change.position.y / h
                                    eraserPos.value = Offset(nx, ny)
                                    pdfDrawingState.erasePointsInZone(
                                        centerX = nx,
                                        centerY = ny,
                                        halfSizeNormalized = eraserSettings.sizeNormalized / 2f,
                                        shape = eraserSettings.shape,
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

        Canvas(modifier = Modifier.fillMaxSize()) {
            pdfDrawingState.currentPaths.forEach { path ->
                drawPath(
                    path = path.points.toCatmullRomPath(size.width, size.height),
                    color = Color(path.colorArgb.toInt()),
                    style = Stroke(
                        width = path.strokeWidth * size.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            if (pdfDrawingState.isDrawing.value && pdfDrawingState.currentPath.value.points.size > 1) {
                val livePath = pdfDrawingState.currentPath.value
                drawPath(
                    path = livePath.points.toCatmullRomPath(size.width, size.height),
                    color = Color(livePath.colorArgb.toInt()),
                    style = Stroke(
                        width = livePath.strokeWidth * size.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
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
 * Converts a list of normalised [DrawingPoint]s to a smooth Compose [Path] using
 * a Catmull-Rom → cubic-Bézier approximation.
 *
 * Sub-strokes (points with [DrawingPoint.isNewPath] == true) are drawn as
 * independent smooth curves.  Segments shorter than 3 points fall back to
 * straight lines so every recorded point is still included.
 */
private fun List<DrawingPoint>.toCatmullRomPath(w: Float, h: Float): Path = Path().apply {
    if (isEmpty()) return@apply

    // Collect segment start indices (index 0 is always a start).
    val starts = indices.filter { i -> i == 0 || get(i).isNewPath }

    starts.forEachIndexed { si, start ->
        val end = if (si + 1 < starts.size) starts[si + 1] else size
        val seg = subList(start, end)

        moveTo(seg[0].x * w, seg[0].y * h)
        if (seg.size < 2) return@forEachIndexed
        if (seg.size == 2) {
            lineTo(seg[1].x * w, seg[1].y * h)
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

            cubicTo(
                x1 + (p2.x - p0.x) * w / 6f, y1 + (p2.y - p0.y) * h / 6f,
                x2 - (p3.x - p1.x) * w / 6f, y2 - (p3.y - p1.y) * h / 6f,
                x2, y2,
            )
        }
    }
}
