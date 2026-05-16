package ru.kyamshanov.notepen.lowlatency

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.kyamshanov.notepen.PdfDrawingState

/**
 * Platform-specific low-latency overlay for the in-flight pen stroke.
 *
 * On Android (API 29+) this is backed by `CanvasFrontBufferedRenderer` on a
 * `SurfaceView` placed above the Compose `Canvas`. Each new sample appended
 * to [PdfDrawingState.livePoints] is drawn directly to the front buffer that
 * is already on-screen, in a special render thread that bypasses Compose's
 * back-buffered compositor. End-to-end pen latency drops from ~30 ms (two
 * compose frames + one composite) to ~5–10 ms (one front-buffer render).
 *
 * On JVM / desktop and on Android API < 29 this is a no-op composable —
 * Compose's own live-stroke rendering (in `DrawablePdfPage`) handles display.
 *
 * The composable must be placed **inside** the same Box that hosts the
 * Compose `Canvas` and pointer-input modifiers, sized to match (e.g.
 * `Modifier.matchParentSize()`), so its coordinate system matches the
 * normalised `[0..1]` space used by [PdfDrawingState.livePoints].
 */
@Composable
expect fun LowLatencyStrokeOverlay(
    drawingState: PdfDrawingState,
    modifier: Modifier = Modifier,
)

/**
 * `true` when [LowLatencyStrokeOverlay] takes over live-stroke rendering on
 * the current platform / OS version. Callers gate their own
 * Compose-rendered live stroke on the negation of this value to avoid
 * drawing the stroke twice.
 */
@Composable
expect fun rememberLowLatencyOverlayAvailable(): Boolean
