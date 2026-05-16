package ru.kyamshanov.notepen

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * Container that hosts the PDF [LazyColumn] and reports two-pointer transform
 * gestures (pinch + pan) and platform zoom shortcuts (e.g. Ctrl+wheel on
 * desktop) to the caller.
 *
 * @param gestureScale current visual scale multiplier applied on top of the
 *   committed scale; rendered via `graphicsLayer` so the bitmap+strokes scale
 *   in lock-step during a pinch.
 * @param panOffset accumulated viewport-pixel translation; rendered via
 *   `graphicsLayer` (origin top-left).
 * @param onTransform invoked on every transform event with the gesture
 *   centroid (viewport-local px), incremental pan (px) and incremental zoom.
 */
@Composable
expect fun ScrollablePdfColumn(
    state: LazyListState,
    gestureScale: Float,
    panOffset: Offset,
    modifier: Modifier = Modifier,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit = { _, _, _ -> },
    content: LazyListScope.() -> Unit,
)
