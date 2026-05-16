package ru.kyamshanov.notepen

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput

private const val CTRL_SCROLL_ZOOM_IN = 1.1f
private const val CTRL_SCROLL_ZOOM_OUT = 0.9f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun ScrollablePdfColumn(
    state: LazyListState,
    gestureScale: Float,
    panOffset: Offset,
    modifier: Modifier,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                // Track cursor position from Move / Enter events ONLY. On
                // Compose Desktop a Scroll event's `change.position` is
                // unreliable — sometimes the cursor, sometimes the
                // post-state position drifts as graphicsLayer recomposes,
                // which manifests as the focal point sliding off the cursor
                // during a Ctrl+wheel zoom. Use the most recent Move-event
                // position instead — that one is always the true cursor.
                // Intercept on the Initial pass so the inner LazyColumn does
                // NOT consume a Ctrl+wheel scroll for its own vertical
                // scrolling before we get to handle it. Without this the
                // page slides down under the cursor before our zoom math
                // runs, and the focal point drifts off.
                var lastCursor = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type != PointerEventType.Scroll) {
                        event.changes.firstOrNull()?.position?.let { lastCursor = it }
                        continue
                    }
                    if (!event.keyboardModifiers.isCtrlPressed) continue
                    val change = event.changes.firstOrNull() ?: continue
                    val scrollDelta = change.scrollDelta
                    val zoom = if (scrollDelta.y < 0f) CTRL_SCROLL_ZOOM_IN else CTRL_SCROLL_ZOOM_OUT
                    onTransform(lastCursor, Offset.Zero, zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        },
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = gestureScale,
                    scaleY = gestureScale,
                    translationX = panOffset.x,
                    translationY = panOffset.y,
                    transformOrigin = TransformOrigin(0f, 0f),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = state),
        )
    }
}
