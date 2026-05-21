package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/**
 * Which drawing tool produced a stroke. Drives tool-specific rendering
 * (e.g. the marker's chisel nib + multiply blend vs the pen's round nib).
 *
 * Only tools that commit strokes appear here — the eraser removes strokes
 * rather than producing them.
 */
@Serializable
enum class ToolKind { PEN, MARKER }

/**
 * A single stroke composed of normalised sample points.
 *
 * [colorArgb] is packed ARGB: bits 31-24 alpha, 23-16 red, 15-8 green, 7-0 blue.
 * Converting to/from a platform Color is done at the presentation boundary.
 *
 * [strokeWidth] is normalised to canvas width [0..1].
 */
@Serializable
data class DrawingPath(
    val points: List<DrawingPoint> = emptyList(),
    val colorArgb: Long = BLACK_ARGB,
    val strokeWidth: Float = DEFAULT_STROKE_WIDTH,
    /** Stable identifier for sync; empty string means local-only (not yet assigned). */
    val strokeId: String = "",
    /** Tool that produced this stroke. Defaults to [ToolKind.PEN] so legacy data deserialises unchanged. */
    val toolType: ToolKind = ToolKind.PEN,
) {
    companion object {
        const val BLACK_ARGB = 0xFF000000L

        /** Default normalised stroke width — matches [PenSettings.DEFAULT_STROKE_WIDTH]. */
        const val DEFAULT_STROKE_WIDTH = 0.0020f
    }
}
