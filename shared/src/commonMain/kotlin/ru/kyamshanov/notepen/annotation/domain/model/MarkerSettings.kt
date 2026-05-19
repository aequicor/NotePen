package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/**
 * Highlighter/marker tool settings.
 *
 * [colorArgb] encodes both hue and opacity — marker presets have ~50 % alpha
 * baked in so the underlying PDF content remains visible.
 *
 * [strokeWidth] is the line width as a fraction of the PDF page width
 * (DPI / zoom / device-independent). On A4 (210 mm wide) `0.025` ≈ 5.25 mm.
 */
@Serializable
data class MarkerSettings(
    val colorArgb: Long = PRESET_COLORS[0],
    val strokeWidth: Float = DEFAULT_STROKE_WIDTH,
) {
    companion object {
        /** ≈ 5.25 mm on A4 — typical highlighter swath. */
        const val DEFAULT_STROKE_WIDTH: Float = 0.025f

        /** ≈ 1.7 mm on A4. */
        const val MIN_STROKE_WIDTH: Float = 0.008f

        /** ≈ 12.6 mm on A4 — fat chisel-tip. */
        const val MAX_STROKE_WIDTH: Float = 0.060f

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

/**
 * Apply a new stroke width; clamped to [[MIN_STROKE_WIDTH]..[MAX_STROKE_WIDTH]].
 *
 * Legacy values stored in raw pixels (old range `10f..80f`) are reset to the new
 * default — see the same migration note on `PenSettings.applyStrokeWidth`.
 */
fun MarkerSettings.applyStrokeWidth(newWidth: Float): MarkerSettings =
    copy(strokeWidth = sanitizeMarkerStrokeWidth(newWidth))

/**
 * Migrate / clamp a raw marker stroke-width value read from persistence.
 * Legacy data stored width in raw pixels (range ~10..80); now it's a fraction
 * of page width. Anything an order of magnitude above the new max is reset.
 */
fun sanitizeMarkerStrokeWidth(width: Float): Float {
    val migrated = if (width > MarkerSettings.MAX_STROKE_WIDTH * 5f) {
        MarkerSettings.DEFAULT_STROKE_WIDTH
    } else {
        width
    }
    return migrated.coerceIn(MarkerSettings.MIN_STROKE_WIDTH, MarkerSettings.MAX_STROKE_WIDTH)
}

/** Counterpart of [PenSettings.sanitizedForCurrentScheme]. */
fun MarkerSettings.sanitizedForCurrentScheme(): MarkerSettings =
    copy(strokeWidth = sanitizeMarkerStrokeWidth(strokeWidth))
