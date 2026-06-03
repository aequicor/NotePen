package ru.kyamshanov.notepen.annotation.domain

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.math.abs

/**
 * Removes screen-pixel stair steps from pen strokes captured at low zoom.
 *
 * Windows WM_POINTER reports client positions in whole pixels. When the PDF is
 * zoomed out, one input pixel maps to a large normalized page delta, so a
 * diagonal pen move can be stored as tiny horizontal/vertical stairs. This pass
 * only runs when that quantization pattern is dominant, and keeps endpoints plus
 * pressure/tilt untouched.
 */
public object StrokeStairSmoother {
    private const val MIN_POINTS: Int = 6
    private const val MIN_AXIS_ALIGNED_RATIO: Float = 0.45f
    private const val MIN_DIAGONAL_RATIO: Float = 0.25f
    private const val AXIS_EPSILON: Float = 0.0000005f

    public fun cleanPenStroke(points: List<DrawingPoint>): List<DrawingPoint> {
        if (!looksLikePixelStairs(points)) return points
        return smoothOnePass(points)
    }

    public fun smoothIfStairStepped(points: List<DrawingPoint>): List<DrawingPoint> {
        if (!looksLikePixelStairs(points)) return points
        return smoothOnePass(points)
    }

    internal fun looksLikePixelStairs(points: List<DrawingPoint>): Boolean {
        if (points.size < MIN_POINTS) return false
        var eligibleSegments = 0
        var axisAlignedSegments = 0
        for (i in 0 until points.size - 1) {
            val next = points[i + 1]
            if (next.isNewPath) continue
            val dx = abs(next.x - points[i].x)
            val dy = abs(next.y - points[i].y)
            if (dx <= AXIS_EPSILON && dy <= AXIS_EPSILON) continue
            eligibleSegments++
            if (dx <= AXIS_EPSILON || dy <= AXIS_EPSILON) axisAlignedSegments++
        }
        if (eligibleSegments < MIN_POINTS - 1) return false
        val first = points.first()
        val last = points.last()
        val totalDx = abs(last.x - first.x)
        val totalDy = abs(last.y - first.y)
        val longer = maxOf(totalDx, totalDy)
        val shorter = minOf(totalDx, totalDy)
        if (longer <= AXIS_EPSILON || shorter / longer < MIN_DIAGONAL_RATIO) return false
        return axisAlignedSegments.toFloat() / eligibleSegments >= MIN_AXIS_ALIGNED_RATIO
    }

    private fun smoothOnePass(points: List<DrawingPoint>): List<DrawingPoint> {
        val out = ArrayList<DrawingPoint>(points.size)
        for (i in points.indices) {
            val point = points[i]
            val prev = points.getOrNull(i - 1)
            val next = points.getOrNull(i + 1)
            if (i == 0 || i == points.lastIndex || point.isNewPath || next?.isNewPath == true || prev == null || next == null) {
                out.add(point)
            } else {
                out.add(
                    point.copy(
                        x = prev.x * 0.25f + point.x * 0.5f + next.x * 0.25f,
                        y = prev.y * 0.25f + point.y * 0.5f + next.y * 0.25f,
                    ),
                )
            }
        }
        return out
    }
}
