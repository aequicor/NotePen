package ru.kyamshanov.notepen.lowlatency

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.drawLiveStroke
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.tools.marker.drawMarkerStroke

@Composable
actual fun LowLatencyStrokeOverlay(
    drawingState: PdfDrawingState,
    modifier: Modifier,
    viewportScale: Float,
    windowBounds: Rect?,
    completedStrokeCount: Int,
) {
    val livePath = remember { Path() }
    Canvas(modifier = modifier) {
        if (!drawingState.isDrawing.value || drawingState.livePoints.isEmpty()) return@Canvas

        val extent = drawingState.extent.value
        if (extent.width <= 0f || extent.height <= 0f) return@Canvas

        val pdfW = size.width / extent.width
        val pdfH = size.height / extent.height
        if (drawingState.liveToolKind.value == ToolKind.MARKER) {
            drawMarkerStroke(
                points = drawingState.livePoints,
                colorArgb = drawingState.liveColorArgb.value,
                normalizedStrokeWidth = drawingState.liveStrokeWidth.value,
                pdfWidth = pdfW,
                pdfHeight = pdfH,
                extent = extent,
                scratch = livePath,
            )
        } else {
            drawLiveStroke(
                points = drawingState.livePoints,
                colorArgb = drawingState.liveColorArgb.value,
                normalizedStrokeWidth = drawingState.liveStrokeWidth.value,
                pdfWidth = pdfW,
                pdfHeight = pdfH,
                extent = extent,
                scratch = livePath,
            )
        }
    }
}

@Composable
actual fun rememberLowLatencyOverlayAvailable(): Boolean = true

@Composable
actual fun rememberLowLatencyOverlayMaxDimensionPx(): Int = Int.MAX_VALUE
