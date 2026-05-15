package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/**
 * A single stroke composed of normalised sample points.
 *
 * [colorArgb] is packed ARGB: bits 31-24 alpha, 23-16 red, 15-8 green, 7-0 blue.
 * Converting to/from a platform Color is done at the presentation boundary.
 *
 * [strokeWidth] is normalised to canvas width [0..1].
 */
@Serializable
data class DrawingPath(
    val points: List<DrawingPoint> = emptyList(),
    val colorArgb: Long = BLACK_ARGB,
    val strokeWidth: Float = DEFAULT_STROKE_WIDTH,
) {
    companion object {
        const val BLACK_ARGB = 0xFF000000L
        const val DEFAULT_STROKE_WIDTH = 10f
    }
}
