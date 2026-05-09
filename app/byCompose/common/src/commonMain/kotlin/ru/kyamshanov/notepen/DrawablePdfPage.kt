package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

@Composable
fun DrawablePdfPage(
    bitmap: ImageBitmap,
    pdfDrawingState: PdfDrawingState,
    modifier: Modifier = Modifier,
    isDrawingEnabled: Boolean = true,
) {
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isDrawingEnabled) {
        if (!isDrawingEnabled && pdfDrawingState.isDrawing.value) {
            pdfDrawingState.finishDrawing()
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { canvasSize.value = it }
            .then(
                if (isDrawingEnabled) {
                    Modifier.pointerInput(isDrawingEnabled) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val (w, h) = canvasSize.value
                                if (w > 0 && h > 0) {
                                    pdfDrawingState.startDrawing(
                                        x = offset.x / w,
                                        y = offset.y / h,
                                        normalizedStrokeWidth = pdfDrawingState.strokeWidth.value / w,
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
                } else Modifier
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
                    path = Path().apply {
                        path.points.forEachIndexed { index, point ->
                            val x = point.x * size.width
                            val y = point.y * size.height
                            if (index == 0 || point.isNewPath) moveTo(x, y) else lineTo(x, y)
                        }
                    },
                    color = path.color,
                    style = Stroke(
                        width = path.strokeWidth * size.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            if (pdfDrawingState.isDrawing.value && pdfDrawingState.currentPath.value.points.size > 1) {
                drawPath(
                    path = Path().apply {
                        pdfDrawingState.currentPath.value.points.forEachIndexed { index, point ->
                            val x = point.x * size.width
                            val y = point.y * size.height
                            if (index == 0 || point.isNewPath) moveTo(x, y) else lineTo(x, y)
                        }
                    },
                    color = pdfDrawingState.currentPath.value.color,
                    style = Stroke(
                        width = pdfDrawingState.currentPath.value.strokeWidth * size.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}
