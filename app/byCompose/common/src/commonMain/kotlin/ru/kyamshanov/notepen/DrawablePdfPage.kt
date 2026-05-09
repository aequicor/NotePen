package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun DrawablePdfPage(
    bitmap: ImageBitmap,
    pdfDrawingState: PdfDrawingState,
    modifier: Modifier = Modifier,
    isDrawingEnabled: Boolean = true,
) {
    LaunchedEffect(isDrawingEnabled) {
        if (!isDrawingEnabled && pdfDrawingState.isDrawing.value) {
            pdfDrawingState.finishDrawing()
        }
    }

    Box(
        modifier = modifier.then(
            if (isDrawingEnabled) {
                Modifier.pointerInput(isDrawingEnabled) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            pdfDrawingState.startDrawing(offset.x, offset.y)
                        },
                        onDrag = { change, _ ->
                            pdfDrawingState.addPoint(change.position.x, change.position.y)
                        },
                        onDragEnd = {
                            pdfDrawingState.finishDrawing()
                        }
                    )
                }
            } else Modifier
        )
    ) {
        // Отображение PDF страницы
        Image(
            bitmap = bitmap,
            contentDescription = "PDF Page",
            modifier = Modifier
                .fillMaxSize()
        )

        // Отображение рисунков
        Canvas(modifier = Modifier.fillMaxSize()) {

            pdfDrawingState.currentPaths.forEach { path ->
                drawPath(
                    path = Path().apply {
                        path.points.forEachIndexed { index, point ->
                            if (index == 0 || point.isNewPath) {
                                moveTo(point.x, point.y)
                            } else {
                                lineTo(point.x, point.y)
                            }
                        }
                    },
                    color = path.color,
                    style = Stroke(width = path.strokeWidth)
                )
            }

            if (pdfDrawingState.isDrawing.value && pdfDrawingState.currentPath.value.points.size > 1) {
                drawPath(
                    path = Path().apply {
                        pdfDrawingState.currentPath.value.points.forEachIndexed { index, point ->
                            if (index == 0 || point.isNewPath) {
                                moveTo(point.x, point.y)
                            } else {
                                lineTo(point.x, point.y)
                            }
                        }
                    },
                    color = pdfDrawingState.currentPath.value.color,
                    style = Stroke(width = pdfDrawingState.currentPath.value.strokeWidth)
                )
            }
        }
    }
}