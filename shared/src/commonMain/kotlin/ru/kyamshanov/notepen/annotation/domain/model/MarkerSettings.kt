package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/**
 * Highlighter/marker tool settings.
 *
 * [colorArgb] encodes both hue and opacity — marker presets have ~50 % alpha
 * baked in so the underlying PDF content remains visible.
 */
@Serializable
data class MarkerSettings(
    val colorArgb: Long = PRESET_COLORS[0],
    val strokeWidth: Float = DEFAULT_STROKE_WIDTH,
) {
    companion object {
        const val DEFAULT_STROKE_WIDTH = 30f
        const val MIN_STROKE_WIDTH = 10f
        const val MAX_STROKE_WIDTH = 80f

        /** Preset highlight colours — all at ~50 % (0x80) alpha. */
        val PRESET_COLORS: List<Long> = listOf(
            0x80FFEB3BL, // yellow
            0x8076FF03L, // lime
            0x8000BCD4L, // cyan
            0x80FF4081L, // pink
            0x80FF9800L, // orange
        )
    }
}

/** Switch to a preset colour (alpha is part of the preset value). */
fun MarkerSettings.applyPreset(presetArgb: Long): MarkerSettings = copy(colorArgb = presetArgb)

/** Apply a new stroke width; clamped to [[MIN_STROKE_WIDTH]..[MAX_STROKE_WIDTH]]. */
fun MarkerSettings.applyStrokeWidth(newWidth: Float): MarkerSettings =
    copy(strokeWidth = newWidth.coerceIn(MarkerSettings.MIN_STROKE_WIDTH, MarkerSettings.MAX_STROKE_WIDTH))
