package ru.kyamshanov.notepen.rendering.api

/** Результат расчёта оптимального разрешения растеризации. */
public data class RenderResolution(
    val widthPx: Int,
    val heightPx: Int,
)
