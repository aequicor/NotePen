package ru.kyamshanov.notepen

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings

class PdfDrawingState {
    var currentPaths = mutableStateListOf<DrawingPath>()
    var currentPath = mutableStateOf(DrawingPath())
    var isDrawing = mutableStateOf(false)
    var strokeWidth = mutableStateOf(DrawingPath.DEFAULT_STROKE_WIDTH)
    /** ARGB-packed color of the active stroke; kept in sync with [PenSettings.colorArgb]. */
    var strokeColorArgb = mutableStateOf(DrawingPath.BLACK_ARGB)

    fun startDrawing(x: Float, y: Float, normalizedStrokeWidth: Float = strokeWidth.value) {
        isDrawing.value = true
        currentPath.value = DrawingPath(
            points = listOf(DrawingPoint(x, y, true)),
            colorArgb = strokeColorArgb.value,
            strokeWidth = normalizedStrokeWidth,
        )
    }

    fun addPoint(x: Float, y: Float) {
        if (isDrawing.value) {
            val newPoints = currentPath.value.points + DrawingPoint(x, y)
            currentPath.value = currentPath.value.copy(points = newPoints)
        }
    }

    fun finishDrawing() {
        if (isDrawing.value && currentPath.value.points.size > 1) {
            currentPaths.add(currentPath.value)
        }
        isDrawing.value = false
        currentPath.value = DrawingPath()
    }

    fun clearDrawing() {
        currentPaths.clear()
        currentPath.value = DrawingPath()
    }

    fun restoreSnapshot(snapshot: List<DrawingPath>) {
        currentPaths.clear()
        currentPaths.addAll(snapshot)
    }
}

/**
 * Point-based erase within a zone.
 *
 * For each stroke in [PdfDrawingState.currentPaths]:
 *  1. Points inside the zone (by [shape] metric) are removed.
 *  2. Consecutive surviving points are grouped into sub-strokes;
 *     the first point of each sub-stroke gets `isNewPath = true`.
 *  3. Sub-strokes with fewer than 2 points are discarded.
 *  4. Color and strokeWidth are inherited from the original stroke.
 *
 * All coordinates are normalised [0..1] relative to the canvas.
 * Returns `true` if at least one stroke was modified or removed.
 */
fun PdfDrawingState.erasePointsInZone(
    centerX: Float,
    centerY: Float,
    halfSizeNormalized: Float,
    shape: EraserShape,
): Boolean {
    if (currentPaths.isEmpty()) return false

    val r2 = halfSizeNormalized * halfSizeNormalized
    val rebuilt = ArrayList<DrawingPath>(currentPaths.size)
    var anyChange = false

    for (path in currentPaths) {
        val survivors = ArrayList<ArrayList<DrawingPoint>>()
        var current: ArrayList<DrawingPoint>? = null
        var pathChanged = false

        for (pt in path.points) {
            val dx = pt.x - centerX
            val dy = pt.y - centerY
            val inZone = when (shape) {
                EraserShape.CIRCLE -> dx * dx + dy * dy <= r2
                EraserShape.SQUARE -> kotlin.math.abs(dx) <= halfSizeNormalized &&
                    kotlin.math.abs(dy) <= halfSizeNormalized
            }
            if (inZone) {
                pathChanged = true
                current = null
            } else {
                if (current == null) {
                    current = ArrayList()
                    survivors.add(current)
                }
                current.add(pt)
            }
        }

        if (!pathChanged) {
            rebuilt.add(path)
            continue
        }

        anyChange = true
        for (group in survivors) {
            if (group.size < 2) continue
            val pts = ArrayList<DrawingPoint>(group.size)
            group.forEachIndexed { idx, p ->
                pts.add(p.copy(isNewPath = idx == 0))
            }
            rebuilt.add(path.copy(points = pts))
        }
    }

    if (anyChange) {
        currentPaths.clear()
        currentPaths.addAll(rebuilt)
    }
    return anyChange
}
