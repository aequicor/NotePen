package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

@Composable
fun DrawablePdfPage(
    bitmap: ImageBitmap,
    pdfDrawingState: PdfDrawingState,
    modifier: Modifier = Modifier
) {

    Box(
        modifier = modifier
            /*.pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        pdfDrawingState.startDrawing(offset.x, offset.y)
                        tryAwaitRelease()
                        pdfDrawingState.finishDrawing()
                    }
                )
            }*/
            .pointerInput(Unit) {
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