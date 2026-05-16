package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Pen tool settings.
 *
 * [colorArgb] is packed ARGB (bits 31-24 alpha, 23-16 red, 15-8 green, 7-0 blue).
 * [alpha] mirrors the alpha channel of [colorArgb] as a Float [0..1] for slider UX;
 * always kept in sync by [applyAlpha].
 */
@Serializable
data class PenSettings(
    val colorArgb: Long = DrawingPath.BLACK_ARGB,
    val strokeWidth: Float = DEFAULT_STROKE_WIDTH,
    val alpha: Float = 1f,
    /**
     * Minimum line width as a fraction of [strokeWidth] when stylus pressure
     * approaches zero. Final per-segment width is interpolated linearly:
     * `width = strokeWidth * (minWidthFactor + pressure * (1 - minWidthFactor))`.
     * `1f` disables pressure modulation entirely.
     */
    val minWidthFactor: Float = DEFAULT_MIN_WIDTH_FACTOR,
) {
    companion object {
        const val DEFAULT_STROKE_WIDTH = 10f
        const val MIN_STROKE_WIDTH = 1f
        const val MAX_STROKE_WIDTH = 60f
        const val DEFAULT_MIN_WIDTH_FACTOR = 0.2f

        /**
         * Preset colours as packed ARGB Longs (fully opaque).
         * Alpha is applied separately via [applyAlpha].
         */
        val PRESET_COLORS: List<Long> = listOf(
            0xFF000000L, // black
            0xFFE53935L, // red
            0xFF1E88E5L, // blue
            0xFF43A047L, // green
            0xFFFB8C00L, // orange
            0xFF8E24AAL, // purple
        )
    }
}

/**
 * Replace the RGB channels with [presetArgb] while preserving the current alpha.
 */
fun PenSettings.applyPreset(presetArgb: Long): PenSettings {
    val alphaByte = (colorArgb shr 24) and 0xFFL
    val rgb = presetArgb and 0x00FFFFFFL
    return copy(colorArgb = (alphaByte shl 24) or rgb)
}

/**
 * Apply a new alpha value from the slider; clamp to [0..1].
 * Updates both [alpha] and the alpha channel of [colorArgb].
 */
fun PenSettings.applyAlpha(newAlpha: Float): PenSettings {
    val clamped = newAlpha.coerceIn(0f, 1f)
    val alphaByte = (clamped * 255).roundToInt().toLong() and 0xFFL
    val rgb = colorArgb and 0x00FFFFFFL
    return copy(colorArgb = (alphaByte shl 24) or rgb, alpha = clamped)
}

/**
 * Apply a new stroke width from the slider; clamp to [MIN_STROKE_WIDTH]..[MAX_STROKE_WIDTH].
 */
fun PenSettings.applyStrokeWidth(newWidth: Float): PenSettings =
    copy(strokeWidth = newWidth.coerceIn(PenSettings.MIN_STROKE_WIDTH, PenSettings.MAX_STROKE_WIDTH))
