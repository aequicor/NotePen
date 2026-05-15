package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/** Shape of the eraser zone. */
@Serializable
enum class EraserShape { CIRCLE, SQUARE }

/** Erasure strategy: remove individual points vs entire strokes. */
@Serializable
enum class EraserMode { POINT, OBJECT }

/**
 * Eraser tool settings.
 *
 * [sizeNormalized] is the diameter / side length as a fraction of canvas width,
 * clamped to [[MIN_SIZE_NORMALIZED]..[MAX_SIZE_NORMALIZED]].
 * [mode] selects between point-based (pixel) and object-based (whole-stroke) erasure.
 */
@Serializable
data class EraserSettings(
    val shape: EraserShape = EraserShape.CIRCLE,
    val sizeNormalized: Float = DEFAULT_SIZE_NORMALIZED,
    val mode: EraserMode = EraserMode.POINT,
) {
    companion object {
        const val DEFAULT_SIZE_NORMALIZED = 0.04f
        const val MIN_SIZE_NORMALIZED = 0.01f
        const val MAX_SIZE_NORMALIZED = 0.20f
    }
}

/** Switch the eraser shape; other fields are preserved. */
fun EraserSettings.applyShape(newShape: EraserShape): EraserSettings = copy(shape = newShape)

/** Switch the erasure mode; other fields are preserved. */
fun EraserSettings.applyMode(newMode: EraserMode): EraserSettings = copy(mode = newMode)

/**
 * Apply a new size from the slider; clamp to [MIN_SIZE_NORMALIZED]..[MAX_SIZE_NORMALIZED].
 */
fun EraserSettings.applySize(newSize: Float): EraserSettings =
    copy(sizeNormalized = newSize.coerceIn(EraserSettings.MIN_SIZE_NORMALIZED, EraserSettings.MAX_SIZE_NORMALIZED))
