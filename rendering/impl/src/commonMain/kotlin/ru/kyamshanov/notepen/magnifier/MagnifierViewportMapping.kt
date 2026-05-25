package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Offset

/**
 * Перевод позиции из viewport-координат в panel-local content. Возвращает
 * `null`, если позиция вне content-области.
 */
fun viewportToPanelLocal(
    state: MagnifierState,
    viewportPos: Offset,
): Offset? {
    val r = state.contentBoundsInViewport
    if (r.width <= 0f || r.height <= 0f) return null
    val local = Offset(viewportPos.x - r.left, viewportPos.y - r.top)
    if (local.x < 0f || local.y < 0f || local.x > r.width || local.y > r.height) return null
    return local
}
