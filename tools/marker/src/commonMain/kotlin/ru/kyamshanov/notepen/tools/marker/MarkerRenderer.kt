package ru.kyamshanov.notepen.tools.marker

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import kotlin.math.cos
import kotlin.math.sin

/** Angle of the marker's chisel nib, in radians (~45°). */
private const val NIB_ANGLE_RADIANS: Float = 0.7853982f

/**
 * Renders a marker stroke as a chisel-nib ribbon.
 *
 * Unlike the pen (round nib drawn as a stroked path), the marker nib is a flat
 * edge held at a fixed [NIB_ANGLE_RADIANS] angle. The stroke is the area swept
 * by that edge along the path, so its visible width varies with travel
 * direction — wide across the nib, thin along it — exactly like a real highlighter.
 *
 * The ribbon is filled once with [BlendMode.Multiply] so the (semi-transparent)
 * ink darkens the content underneath while leaving dark text readable, and so a
 * self-overlapping stroke does not compound into a darker blob (a single filled
 * path counts overlapping area only once).
 *
 * [points] are normalised page coordinates; [normalizedStrokeWidth] is the nib
 * breadth as a fraction of [pdfWidth]. [scratch] is a reusable [Path] the caller
 * owns to avoid per-frame allocation.
 */
public fun DrawScope.drawMarkerStroke(
    points: List<DrawingPoint>,
    colorArgb: Long,
    normalizedStrokeWidth: Float,
    pdfWidth: Float,
    pdfHeight: Float,
    extent: PageExtent,
    scratch: Path,
) {
    if (points.size < 2) return

    val halfWidthPx = (normalizedStrokeWidth * pdfWidth) * 0.5f
    if (halfWidthPx <= 0f) return

    // Nib offset vector in pixels — constant breadth regardless of page aspect.
    val nibX = cos(NIB_ANGLE_RADIANS) * halfWidthPx
    val nibY = sin(NIB_ANGLE_RADIANS) * halfWidthPx

    val offX = -extent.left
    val offY = -extent.top

    scratch.reset()
    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        if (p2.isNewPath) continue

        val x1 = (p1.x + offX) * pdfWidth
        val y1 = (p1.y + offY) * pdfHeight
        val x2 = (p2.x + offX) * pdfWidth
        val y2 = (p2.y + offY) * pdfHeight

        // Quad swept by the nib edge between consecutive samples.
        scratch.moveTo(x1 + nibX, y1 + nibY)
        scratch.lineTo(x1 - nibX, y1 - nibY)
        scratch.lineTo(x2 - nibX, y2 - nibY)
        scratch.lineTo(x2 + nibX, y2 + nibY)
        scratch.close()
    }

    drawPath(
        path = scratch,
        color = Color(colorArgb.toInt()),
        style = Fill,
        blendMode = BlendMode.Multiply,
    )
}
