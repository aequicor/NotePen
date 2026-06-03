package ru.kyamshanov.notepen

private const val DESKTOP_INK_CACHE_MAX_DIMENSION_PX = 8192

internal actual fun completedInkCacheMaxDimensionPx(): Int = DESKTOP_INK_CACHE_MAX_DIMENSION_PX

internal actual fun vectorCompletedInkWhenCacheUpscaled(): Boolean = false
