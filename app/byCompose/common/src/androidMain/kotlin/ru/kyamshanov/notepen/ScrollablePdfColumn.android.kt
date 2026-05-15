package ru.kyamshanov.notepen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

@Composable
actual fun ScrollablePdfColumn(
    state: LazyListState,
    modifier: Modifier,
    onScale: (Float) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = modifier.pointerInput(onScale) {
            awaitEachGesture {
                var prevSpan = 0f
                do {
                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                    val pressed: List<PointerInputChange> = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val dx = pressed[1].position.x - pressed[0].position.x
                        val dy = pressed[1].position.y - pressed[0].position.y
                        val span = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (prevSpan > 0f) {
                            onScale(span / prevSpan)
                        }
                        prevSpan = span
                    } else {
                        prevSpan = 0f
                    }
                } while (event.changes.any { it.pressed })
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
