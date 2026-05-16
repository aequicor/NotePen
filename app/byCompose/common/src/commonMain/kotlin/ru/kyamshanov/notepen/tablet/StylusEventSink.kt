package ru.kyamshanov.notepen.tablet

import androidx.compose.ui.Modifier

/**
 * Attaches a platform-specific "passive sniffer" to the modifier chain that
 * feeds raw stylus events (tool-type, button state, tilt, hover position)
 * into the supplied [controller] without consuming them. The Compose pointer
 * pipeline still receives the events untouched, so gesture handlers above /
 * below this modifier continue to work.
 *
 * On Android this is implemented via `pointerInteropFilter` and reads the
 * underlying `MotionEvent`. On JVM / desktop this is a no-op — the tablet
 * controller there is driven by native side-channels (WinTab, NSEvent dylib)
 * rather than by Compose's input pipeline.
 */
expect fun Modifier.stylusEventSink(controller: TabletInputController): Modifier
