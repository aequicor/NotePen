package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/** A single sample of a stroke path, in normalised canvas coordinates [0..1]. */
@Serializable
data class DrawingPoint(
    val x: Float,
    val y: Float,
    /** True if this point starts a new sub-path (e.g. after an erase split). */
    val isNewPath: Boolean = false,
    /**
     * Stylus pressure normalised to [0..1]. Defaults to `1f` for samples coming
     * from a mouse or any source without pressure info — ensures backward
     * compatibility with strokes serialised before this field existed.
     */
    val pressure: Float = 1f,
    /**
     * Stylus tilt normalised to [0..1] where `0f` is perpendicular to the
     * screen and `1f` is fully parallel. Defaults to `0f` for sources without
     * tilt info; older serialised strokes load with this default.
     */
    val tilt: Float = 0f,
)
