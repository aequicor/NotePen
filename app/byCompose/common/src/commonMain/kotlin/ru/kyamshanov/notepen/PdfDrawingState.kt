package ru.kyamshanov.notepen

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Serializable
data class DrawingPoint(
    val x: Float,
    val y: Float,
    val isNewPath: Boolean = false,
)

@Serializable
data class DrawingPath(
    val points: List<DrawingPoint> = emptyList(),
    @Serializable(with = ColorAsLongSerializer::class)
    val color: Color = Color.Black,
    val strokeWidth: Float = 10f,
)

class PdfDrawingState {
    var currentPaths = mutableStateListOf<DrawingPath>()
    var currentPath = mutableStateOf(DrawingPath())
    var isDrawing = mutableStateOf(false)
    var strokeWidth = mutableStateOf(10f)
    var strokeColor = mutableStateOf(Color.Black)
    fun startDrawing(x: Float, y: Float, normalizedStrokeWidth: Float = strokeWidth.value) {
        isDrawing.value = true
        currentPath.value = DrawingPath(
            points = listOf(DrawingPoint(x, y, true)),
            color = strokeColor.value,
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
 * Точечное стирание (EC-3..EC-7, AC-10/AC-13).
 *
 * Для каждого штриха в [PdfDrawingState.currentPaths]:
 *   1. Точки, попавшие в зону `(centerX, centerY, halfSizeNormalized)` по метрике [shape],
 *      удаляются.
 *   2. Подряд идущие выжившие точки группируются в подштрихи; первая точка каждого
 *      подштриха получает `isNewPath = true`.
 *   3. Подштрихи с `points.size < 2` отбрасываются (EC-7).
 *   4. Цвет и `strokeWidth` исходного штриха наследуются всеми подштрихами.
 *
 * Все координаты — нормализованные `[0..1]` относительно canvas.
 *
 * Возвращает `true`, если хотя бы один штрих был изменён или удалён.
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