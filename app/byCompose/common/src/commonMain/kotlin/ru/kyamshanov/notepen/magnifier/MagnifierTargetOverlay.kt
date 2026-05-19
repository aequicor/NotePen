package ru.kyamshanov.notepen.magnifier

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke

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
    // Жесты move/resize обрабатываются в `DetailsContent.routedOnMove` через
    // `MagnifierTargetGestureController` — он питается из того же pointer-
    // input, что и drawing-pipeline, и работает как с мышью, так и с
    // нативным pen-стримом. Здесь только отрисовка.
    Canvas(modifier = modifier.fillMaxSize()) {
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


private const val FRAME_FILL_ALPHA = 0.10f
private const val FRAME_STROKE_PX = 2f
private const val RESIZE_HANDLE_FRAC = 0.04f
private val DASH_INTERVALS = floatArrayOf(12f, 8f)
