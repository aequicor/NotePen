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

    fun startDrawing(x: Float, y: Float) {
        isDrawing.value = true
        currentPath.value = DrawingPath(
            points = listOf(DrawingPoint(x, y, true)),
            color = strokeColor.value,
            strokeWidth = strokeWidth.value
        )
    }

    fun addPoint(x: Float, y: Float) {
        if (isDrawing.value) {
            val newPoints = currentPath.value.points + DrawingPoint(x, y)
            currentPath.value = currentPath.value.copy(points = newPoints)
        }
    }

    fun finishDrawing() {
        println("finishDrawing")
        if (isDrawing.value && currentPath.value.points.size > 1) {
            println("addPoint: ${currentPath.value.points.size}")
            currentPaths.add(currentPath.value)
        }
        isDrawing.value = false
        currentPath.value = DrawingPath()
    }

    fun clearDrawing() {
        currentPaths.clear()
        currentPath.value = DrawingPath()
    }

    fun undo() {
        if (currentPaths.isNotEmpty()) {
            currentPaths.removeLast()
        }
    }
}