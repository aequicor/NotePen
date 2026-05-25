package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pen tool settings.
 *
 * [colorArgb] is packed ARGB (bits 31-24 alpha, 23-16 red, 15-8 green, 7-0 blue).
 * [alpha] mirrors the alpha channel of [colorArgb] as a Float [0..1] for slider UX;
 * always kept in sync by [applyAlpha].
 *
 * [strokeWidth] is the line width as a fraction of the PDF page width
 * (DPI / zoom / device-independent). On A4 (210 mm wide) `0.002` ≈ 0.42 mm —
 * the look of an ordinary ballpoint over body text.
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
        /** ≈ 0.42 mm on A4 — matches text stroke thickness. */
        const val DEFAULT_STROKE_WIDTH: Float = 0.0020f

        /** ≈ 0.13 mm on A4 — thinnest pleasant pen. */
        const val MIN_STROKE_WIDTH: Float = 0.0006f

        /** ≈ 4.2 mm on A4 — heaviest pen before it reads as a marker. */
        const val MAX_STROKE_WIDTH: Float = 0.020f

        const val DEFAULT_MIN_WIDTH_FACTOR: Float = 0.2f

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
 * Apply a new stroke width; values outside [[MIN_STROKE_WIDTH]..[MAX_STROKE_WIDTH]] are clamped.
 *
 * Also performs a one-shot migration: legacy settings stored width in raw pixels
 * (range `1f..60f`). Anything above [MAX_STROKE_WIDTH] is reset to the new default —
 * dropping a 10× over-thick line is preferable to silently mis-scaling user input.
 */
fun PenSettings.applyStrokeWidth(newWidth: Float): PenSettings =
    copy(strokeWidth = sanitizePenStrokeWidth(newWidth))

/**
 * Migrate / clamp a raw stroke-width value read from persistence or user input.
 * `strokeWidth` is now a fraction of page width; legacy data stored it in raw
 * pixels (range ~1..60). Treat anything an order of magnitude above the new
 * [PenSettings.MAX_STROKE_WIDTH] as legacy and reset to the default.
 */
fun sanitizePenStrokeWidth(width: Float): Float {
    val migrated = if (width > PenSettings.MAX_STROKE_WIDTH * 5f) {
        PenSettings.DEFAULT_STROKE_WIDTH
    } else {
        width
    }
    return migrated.coerceIn(PenSettings.MIN_STROKE_WIDTH, PenSettings.MAX_STROKE_WIDTH)
}

/**
 * Return a copy with `strokeWidth` sanitised — see [sanitizePenStrokeWidth].
 * Call this on settings restored from disk so legacy pixel-range values don't
 * blow up into page-wide blobs.
 */
fun PenSettings.sanitizedForCurrentScheme(): PenSettings =
    copy(strokeWidth = sanitizePenStrokeWidth(strokeWidth))

/**
 * Perceptually-uniform slider position `[0..1]` for a stroke width.
 * Log-mapped so each ~10 % of slider travel multiplies width by the same factor.
 */
fun strokeWidthToSliderPosition(width: Float, min: Float, max: Float): Float {
    val w = width.coerceIn(min, max)
    return (ln(w / min) / ln(max / min)).coerceIn(0f, 1f)
}

/**
 * Inverse of [strokeWidthToSliderPosition]: `t∈[0..1] → width∈min..max` on a log scale.
 */
fun sliderPositionToStrokeWidth(t: Float, min: Float, max: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return min * (max / min).pow(clamped)
}
