package ru.kyamshanov.notepen.drawing.api

/**
 * Позиция курсора ластика в нормализованных координатах страницы `[0..1]`.
 *
 * Заменяет `Offset` из `compose.ui.geometry`, чтобы модуль `rendering-api`
 * не зависел от `compose.ui`.
 */
public data class EraserPosition(
    val x: Float,
    val y: Float,
)
