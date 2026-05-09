package ru.kyamshanov.notepen

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Настройки пера: цвет (включая alpha), толщина штриха, общий уровень прозрачности.
 *
 * Значение [color] хранит уже собранный ARGB (с применённым [alpha]) — это важно
 * для round-trip через [ColorAsLongSerializer], который сериализует Color как Long.
 * Слайдер alpha визуально работает поверх R/G/B-пресета: на каждое изменение
 * формируется итоговый `color = preset.copy(alpha = alpha)`.
 */
@Serializable
data class PenSettings(
    @Serializable(with = ColorAsLongSerializer::class)
    val color: Color = Color.Black,
    val strokeWidth: Float = DEFAULT_STROKE_WIDTH,
    val alpha: Float = 1f,
) {
    companion object {
        const val DEFAULT_STROKE_WIDTH = 10f
        const val MIN_STROKE_WIDTH = 1f
        const val MAX_STROKE_WIDTH = 60f

        /**
         * Пресет цветов для горизонтальной ленты в `PenSettingsPanel`.
         * Все цвета — fully opaque; alpha применяется отдельно через слайдер.
         */
        val PRESET_COLORS: List<Color> = listOf(
            Color.Black,
            Color(0xFFE53935),
            Color(0xFF1E88E5),
            Color(0xFF43A047),
            Color(0xFFFB8C00),
            Color(0xFF8E24AA),
        )
    }
}
