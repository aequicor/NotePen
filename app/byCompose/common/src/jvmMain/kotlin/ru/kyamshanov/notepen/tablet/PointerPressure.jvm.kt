package ru.kyamshanov.notepen.tablet

import androidx.compose.ui.input.pointer.PointerInputChange

/**
 * Desktop has no per-event pressure on the Compose pointer — the WinTab /
 * Cocoa tablet controller drives a [TabletInputController] StateFlow that the
 * caller already consults via [fallback].
 */
actual fun PointerInputChange.effectivePressure(fallback: Float): Float = fallback
