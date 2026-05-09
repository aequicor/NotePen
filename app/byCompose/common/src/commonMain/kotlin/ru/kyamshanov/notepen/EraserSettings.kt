package ru.kyamshanov.notepen

import kotlinx.serialization.Serializable

/** Форма зоны стирания ластика. */
@Serializable
enum class EraserShape { CIRCLE, SQUARE }

/**
 * Настройки ластика: форма зоны и её размер в нормализованных координатах canvas.
 *
 * [sizeNormalized] хранит «диаметр / сторону» в долях ширины canvas в диапазоне
 * `[MIN_SIZE_NORMALIZED .. MAX_SIZE_NORMALIZED]`. Слайдер в `EraserSettingsPanel`
 * ограничивает значение этим диапазоном (см. EC-10 / EC-11).
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
