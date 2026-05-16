package ru.kyamshanov.notepen.lowlatency

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.kyamshanov.notepen.PdfDrawingState

/** Desktop has no front-buffered surface — Compose Canvas renders the live stroke. */
@Composable
actual fun LowLatencyStrokeOverlay(drawingState: PdfDrawingState, modifier: Modifier) {
    // no-op
}

@Composable
actual fun rememberLowLatencyOverlayAvailable(): Boolean = false
