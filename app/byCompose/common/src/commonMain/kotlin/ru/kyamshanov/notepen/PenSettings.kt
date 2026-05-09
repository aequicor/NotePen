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

/**
 * Replace the RGB channels with [preset] while keeping the current alpha slider value.
 * Verifies AC-8 — preset selection does not reset the alpha slider.
 */
fun PenSettings.applyPreset(preset: Color): PenSettings =
    copy(color = preset.copy(alpha = alpha))

/**
 * Apply a new alpha value coming from the slider: clamp to `[0, 1]`, store it
 * verbatim in [PenSettings.alpha], and rebuild [PenSettings.color] so the
 * persisted color carries the same alpha (the file format keeps a single
 * ARGB Long — see `ColorAsLongSerializer`).
 *
 * Verifies AC-7 + EC-9 (next-stroke semantics handled by the caller).
 */
fun PenSettings.applyAlpha(newAlpha: Float): PenSettings {
    val clamped = newAlpha.coerceIn(0f, 1f)
    return copy(color = color.copy(alpha = clamped), alpha = clamped)
}

/**
 * Apply a new stroke width coming from the slider; clamp to
 * `[MIN_STROKE_WIDTH, MAX_STROKE_WIDTH]`. Verifies AC-6.
 */
fun PenSettings.applyStrokeWidth(newWidth: Float): PenSettings =
    copy(strokeWidth = newWidth.coerceIn(PenSettings.MIN_STROKE_WIDTH, PenSettings.MAX_STROKE_WIDTH))
