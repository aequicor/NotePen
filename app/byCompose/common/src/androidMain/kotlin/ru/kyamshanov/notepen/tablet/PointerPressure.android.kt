package ru.kyamshanov.notepen.tablet

import androidx.compose.ui.input.pointer.PointerInputChange

actual fun PointerInputChange.effectivePressure(fallback: Float): Float {
    val p = this.pressure
    return if (p > 0f && p < 1f) p else fallback
}
