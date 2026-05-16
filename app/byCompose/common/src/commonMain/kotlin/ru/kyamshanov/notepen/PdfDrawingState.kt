package ru.kyamshanov.notepen

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings

class PdfDrawingState {
    var currentPaths = mutableStateListOf<DrawingPath>()

    /**
     * Live (still-being-drawn) stroke samples. Append-only during a stroke;
     * cleared on [finishDrawing]. Backed by a [SnapshotStateList] so each
     * `add` is amortised O(1) and recomposition of readers (the live-stroke
     * renderer / front-buffered overlay) is incremental rather than re-copying
     * the whole point list per sample — the latter caused GC stalls visible
     * as stutter during long pen strokes.
     */
    val livePoints = mutableStateListOf<DrawingPoint>()

    var isDrawing = mutableStateOf(false)
    var strokeWidth = mutableStateOf(DrawingPath.DEFAULT_STROKE_WIDTH)
    /** ARGB-packed color of the active stroke; kept in sync with [PenSettings.colorArgb]. */
    var strokeColorArgb = mutableStateOf(DrawingPath.BLACK_ARGB)

    /** Color of the currently-live stroke. Snapshotted at [startDrawing]. */
    val liveColorArgb = mutableStateOf(DrawingPath.BLACK_ARGB)

    /** Normalised stroke width of the currently-live stroke. Snapshotted at [startDrawing]. */
    val liveStrokeWidth = mutableStateOf(DrawingPath.DEFAULT_STROKE_WIDTH)

    /**
     * Monotonic counter bumped whenever [currentPaths] changes through one of
     * the public mutators ([finishDrawing], [clearDrawing], [restoreSnapshot],
     * `eraseInZone` and friends). Consumers that cache rendered output of
     * completed strokes (e.g. the `completedLayer` bitmap in `DrawablePdfPage`)
     * read it as an invalidation signal — bumping it is cheaper than diffing
     * the list contents, and avoids re-rendering finished strokes every frame.
     *
     * Code that mutates [currentPaths] directly (sync engine, tests) should
     * call [markHistoryChanged] afterwards.
     */
    val historyVersion = mutableStateOf(0)

    /** Bump [historyVersion] to invalidate caches keyed on completed-stroke content. */
    fun markHistoryChanged() {
        historyVersion.value++
    }

    fun startDrawing(
        x: Float,
        y: Float,
        normalizedStrokeWidth: Float = strokeWidth.value,
        pressure: Float = 1f,
        tilt: Float = 0f,
    ) {
        isDrawing.value = true
        liveColorArgb.value = strokeColorArgb.value
        liveStrokeWidth.value = normalizedStrokeWidth
        livePoints.clear()
        livePoints.add(DrawingPoint(x, y, isNewPath = true, pressure = pressure, tilt = tilt))
    }

    fun addPoint(x: Float, y: Float, pressure: Float = 1f, tilt: Float = 0f) {
        if (isDrawing.value) {
            livePoints.add(DrawingPoint(x, y, pressure = pressure, tilt = tilt))
        }
    }

    fun finishDrawing(): DrawingPath? {
        val completed = if (isDrawing.value && livePoints.size > 1) {
            val path = DrawingPath(
                points = livePoints.toList(),
                colorArgb = liveColorArgb.value,
                strokeWidth = liveStrokeWidth.value,
            )
            currentPaths.add(path)
            markHistoryChanged()
            path
        } else {
            null
        }
        isDrawing.value = false
        livePoints.clear()
        return completed
    }

    fun clearDrawing() {
        currentPaths.clear()
        livePoints.clear()
        markHistoryChanged()
    }

    fun restoreSnapshot(snapshot: List<DrawingPath>) {
        currentPaths.clear()
        currentPaths.addAll(snapshot)
        markHistoryChanged()
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
        markHistoryChanged()
    }
    return anyChange
}

/**
 * Object-mode erase: removes any stroke that has at least one point inside the zone.
 *
 * Unlike [erasePointsInZone], the whole stroke is deleted — not just the intersecting points.
 * Returns `true` if at least one stroke was removed.
 */
fun PdfDrawingState.eraseStrokesInZone(
    centerX: Float,
    centerY: Float,
    halfSizeNormalized: Float,
    shape: EraserShape,
): Boolean {
    if (currentPaths.isEmpty()) return false

    val r2 = halfSizeNormalized * halfSizeNormalized
    val before = currentPaths.size

    currentPaths.removeAll { path ->
        path.points.any { pt ->
            val dx = pt.x - centerX
            val dy = pt.y - centerY
            when (shape) {
                EraserShape.CIRCLE -> dx * dx + dy * dy <= r2
                EraserShape.SQUARE -> kotlin.math.abs(dx) <= halfSizeNormalized &&
                    kotlin.math.abs(dy) <= halfSizeNormalized
            }
        }
    }

    val changed = currentPaths.size != before
    if (changed) markHistoryChanged()
    return changed
}

/**
 * Dispatches to [erasePointsInZone] or [eraseStrokesInZone] based on [EraserSettings.mode].
 */
fun PdfDrawingState.eraseInZone(
    centerX: Float,
    centerY: Float,
    halfSizeNormalized: Float,
    settings: EraserSettings,
): Boolean = when (settings.mode) {
    EraserMode.POINT -> erasePointsInZone(centerX, centerY, halfSizeNormalized, settings.shape)
    EraserMode.OBJECT -> eraseStrokesInZone(centerX, centerY, halfSizeNormalized, settings.shape)
}
