package ru.kyamshanov.notepen.tablet

import androidx.compose.ui.input.pointer.PointerInputChange

/**
 * Returns the pressure value from this [PointerInputChange] when the platform
 * provides a meaningful one (Android: `MotionEvent.getPressure()` for stylus
 * input). Falls back to [fallback] otherwise — on desktop the WinTab/Cocoa
 * side-channel populates the controller, and a Compose pointer change has no
 * pressure information.
 *
 * Implementations must return `fallback` when the pressure is unknown (0f or
 * exactly 1f on a mouse) so the caller's tablet controller stays in control.
 */
expect fun PointerInputChange.effectivePressure(fallback: Float): Float
