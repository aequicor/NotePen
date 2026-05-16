package ru.kyamshanov.notepen.tablet

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Attach a passive [pointerInteropFilter] that forwards every `MotionEvent`
 * into [controller]'s `feed(...)`. Returns `false` so the event continues to
 * be processed by Compose's pointer pipeline — drawing gestures keep working.
 *
 * Only meaningful when [controller] is [AndroidTabletInputController]; for
 * other implementations the sink is a no-op to keep the no-op fallback
 * cheap.
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.stylusEventSink(controller: TabletInputController): Modifier {
    if (controller !is AndroidTabletInputController) return this
    var size = IntSize.Zero
    return this
        .onSizeChanged { size = it }
        .pointerInteropFilter { event ->
            controller.feed(event, size.width, size.height)
            false
        }
}
