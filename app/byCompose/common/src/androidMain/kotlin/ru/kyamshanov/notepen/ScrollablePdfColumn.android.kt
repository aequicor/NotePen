package ru.kyamshanov.notepen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

@Composable
actual fun ScrollablePdfColumn(
    state: LazyListState,
    gestureScale: Float,
    panOffset: Offset,
    modifier: Modifier,
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier
            .pointerInput(Unit) {
                // Two-pointer pinch + pan. Inspected on the Initial pass so we
                // see events before LazyColumn's own scroll handler; we only
                // consume when ≥2 fingers are down so a single-finger drag
                // still reaches LazyColumn (vertical scroll) and the per-page
                // stylus pointerInput (drawing). Centroid + delta-centroid +
                // span-ratio give zoom-to-pinch-centre out of the box.
                awaitEachGesture {
                    var prevCentroid: Offset? = null
                    var prevSpan = 0f
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val pressed: List<PointerInputChange> = event.changes.filter { it.pressed }
                        if (pressed.size < 2) {
                            if (pressed.isEmpty()) break
                            prevCentroid = null
                            prevSpan = 0f
                            continue
                        }
                        val p0 = pressed[0].position
                        val p1 = pressed[1].position
                        val centroid = Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                        val dx = p1.x - p0.x
                        val dy = p1.y - p0.y
                        val span = kotlin.math.sqrt(dx * dx + dy * dy)
                        val prevC = prevCentroid
                        if (prevC != null && prevSpan > 0f) {
                            val pan = Offset(centroid.x - prevC.x, centroid.y - prevC.y)
                            val zoom = if (span > 0f) span / prevSpan else 1f
                            onTransform(centroid, pan, zoom)
                            pressed.forEach { it.consume() }
                        }
                        prevCentroid = centroid
                        prevSpan = span
                    }
                }
            }
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
}
