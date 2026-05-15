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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput

private const val CTRL_SCROLL_ZOOM_IN = 1.1f
private const val CTRL_SCROLL_ZOOM_OUT = 0.9f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun ScrollablePdfColumn(
    state: LazyListState,
    modifier: Modifier,
    onScale: (Float) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier = modifier.pointerInput(onScale) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                        val scrollDelta = event.changes.firstOrNull()?.scrollDelta ?: continue
                        val factor = if (scrollDelta.y < 0f) CTRL_SCROLL_ZOOM_IN else CTRL_SCROLL_ZOOM_OUT
                        onScale(factor)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        },
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = state),
        )
    }
}
