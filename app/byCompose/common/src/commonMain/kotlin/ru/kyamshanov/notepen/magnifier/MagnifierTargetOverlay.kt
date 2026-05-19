package ru.kyamshanov.notepen.magnifier

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Оверлей, рисующий рамку-цель magnifier'а на странице и обрабатывающий
 * жесты перетаскивания/ресайза.
 *
 * Размещается **внутри** `DrawablePdfPage` поверх completedLayer и
 * live-stroke; должен показываться только когда `state.enabled` и
 * `state.pageIndex == thisPage`. Координаты рамки — page-normalized
 * `[0..1]` (см. [MagnifierState.targetRect]).
 */
@Composable
fun MagnifierTargetOverlay(
    state: MagnifierState,
    pageIndex: Int,
    frameColor: Color,
    modifier: Modifier = Modifier,
) {
    val segment = state.segments.firstOrNull { it.pageIndex == pageIndex } ?: return
    val isSinglePage = state.segments.size == 1
    val dragModeRef = remember { arrayOf(DragMode.Move) }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(state, pageIndex) {
                if (!isSinglePage) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                        val nx = offset.x / size.width
                        val ny = offset.y / size.height
                        val r = segment.targetOnPage
                        val nearRight = nx in (r.right - RESIZE_HANDLE_FRAC)..(r.right + RESIZE_HANDLE_FRAC)
                        val nearBottom = ny in (r.bottom - RESIZE_HANDLE_FRAC)..(r.bottom + RESIZE_HANDLE_FRAC)
                        dragModeRef[0] = if (nearRight && nearBottom) DragMode.Resize else DragMode.Move
                    },
                    onDrag = { change, dragAmount ->
                        if (size.width <= 0 || size.height <= 0) return@detectDragGestures
                        val dxN = dragAmount.x / size.width
                        val dyN = dragAmount.y / size.height
                        when (dragModeRef[0]) {
                            DragMode.Move -> state.moveTarget(Offset(dxN, dyN))
                            DragMode.Resize -> {
                                val r = segment.targetOnPage
                                state.resizeTarget(
                                    newWidth = (r.right - r.left + dxN).coerceAtLeast(MIN_TARGET_DIM),
                                    newHeight = (r.bottom - r.top + dyN).coerceAtLeast(MIN_TARGET_DIM),
                                )
                            }
                        }
                        change.consume()
                    },
                )
            },
    ) {
        val r = segment.targetOnPage
        val left = r.left * size.width
        val top = r.top * size.height
        val w = (r.right - r.left) * size.width
        val h = (r.bottom - r.top) * size.height
        // Полупрозрачная заливка — подсвечивает, какую область смотрит панель.
        drawRect(
            color = frameColor.copy(alpha = FRAME_FILL_ALPHA),
            topLeft = Offset(left, top),
            size = Size(w, h),
        )
        // Пунктирная обводка.
        drawRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(
                width = FRAME_STROKE_PX,
                pathEffect = PathEffect.dashPathEffect(DASH_INTERVALS),
            ),
        )
        // Маркер ресайз-хэндла — только для single-page (multi-page рамку
        // в этой версии нельзя интерактивно ресайзить).
        if (isSinglePage) {
            val handle = RESIZE_HANDLE_FRAC * size.width
            drawRect(
                color = frameColor,
                topLeft = Offset(left + w - handle, top + h - handle),
                size = Size(handle, handle),
            )
        }
    }
}

private enum class DragMode { Move, Resize }

private const val FRAME_FILL_ALPHA = 0.10f
private const val FRAME_STROKE_PX = 2f
private const val RESIZE_HANDLE_FRAC = 0.04f
private val DASH_INTERVALS = floatArrayOf(12f, 8f)
