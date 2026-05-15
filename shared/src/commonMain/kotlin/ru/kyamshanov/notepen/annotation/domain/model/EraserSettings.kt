package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/** Shape of the eraser zone. */
@Serializable
enum class EraserShape { CIRCLE, SQUARE }

/**
 * Eraser tool settings.
 *
 * [sizeNormalized] is the diameter / side length as a fraction of canvas width,
 * clamped to [[MIN_SIZE_NORMALIZED]..[MAX_SIZE_NORMALIZED]].
 */
@Serializable
data class EraserSettings(
    val shape: EraserShape = EraserShape.CIRCLE,
    val sizeNormalized: Float = DEFAULT_SIZE_NORMALIZED,
) {
    companion object {
        const val DEFAULT_SIZE_NORMALIZED = 0.04f
        const val MIN_SIZE_NORMALIZED = 0.01f
        const val MAX_SIZE_NORMALIZED = 0.20f
    }
}

/** Switch the eraser shape; size is preserved. */
fun EraserSettings.applyShape(newShape: EraserShape): EraserSettings = copy(shape = newShape)

/**
 * Apply a new size from the slider; clamp to [MIN_SIZE_NORMALIZED]..[MAX_SIZE_NORMALIZED].
 */
fun EraserSettings.applySize(newSize: Float): EraserSettings =
    copy(sizeNormalized = newSize.coerceIn(EraserSettings.MIN_SIZE_NORMALIZED, EraserSettings.MAX_SIZE_NORMALIZED))
