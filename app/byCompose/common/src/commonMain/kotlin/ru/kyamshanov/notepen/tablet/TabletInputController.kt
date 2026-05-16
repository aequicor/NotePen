package ru.kyamshanov.notepen.tablet

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stylus / graphics tablet input port.
 *
 * Compose drives stroke geometry (x, y) via its normal pointer pipeline.
 * This controller supplies the **side-channel** the pointer pipeline can't
 * see — pen pressure and barrel-button state — sourced from a platform-
 * specific API (WinTab on Windows, no-op elsewhere).
 *
 * Consumers read [latestPressure] / [barrelPressed] on every `onDrag` tick;
 * the controller pushes updates from its own polling thread. The read-side
 * race is intentional — losing a single packet at 200+ Hz is invisible.
 */
interface TabletInputController {
    /** Latest pressure sample in `[0..1]`. `1f` when no tablet is attached. */
    val latestPressure: StateFlow<Float>

    /** `true` while the pen's barrel (side) button is held down. */
    val barrelPressed: StateFlow<Boolean>
}

/** Fallback controller for platforms without tablet support (Android, macOS, Linux). */
object NoOpTabletInputController : TabletInputController {
    override val latestPressure: StateFlow<Float> = MutableStateFlow(1f)
    override val barrelPressed: StateFlow<Boolean> = MutableStateFlow(false)
}

/**
 * Provides the active [TabletInputController] to the composition tree.
 * Defaults to [NoOpTabletInputController]; Desktop main wraps the app in a
 * `CompositionLocalProvider` supplying the WinTab-backed implementation.
 */
val LocalTabletInputController = staticCompositionLocalOf<TabletInputController> {
    NoOpTabletInputController
}
