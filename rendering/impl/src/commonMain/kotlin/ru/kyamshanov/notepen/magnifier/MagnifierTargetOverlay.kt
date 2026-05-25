package ru.kyamshanov.notepen.magnifier

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
    /** Активен ли drag рамки пером/мышью — для визуальной подсветки GRAB. */
    isGrabbing: Boolean = false,
    /** Закреплена ли рамка на экране (для альтернативной визуализации). */
    isScreenPinned: Boolean = false,
) {
    val segment = state.segments.firstOrNull { it.pageIndex == pageIndex } ?: return
    // В SCREEN-режиме (вне активного GRAB'а) рамка отрисовывается отдельным
    // viewport-overlay'ем (см. [MagnifierScreenPinnedOverlay]) — там она
    // визуально неподвижна при скролле. Здесь её рендерить не нужно, иначе
    // на один кадр после смены `pan`/`zoom` рамка дёргается из-за того, что
    // overlay живёт внутри страницы и сдвигается со страницей до того, как
    // `repinFromViewportRect` пересчитает page-coords.
    if (isScreenPinned && !isGrabbing) return
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
        // При GRAB заметнее, чтобы пользователь видел, что «держит» рамку.
        val fillAlpha = if (isGrabbing) FRAME_FILL_ALPHA_GRAB else FRAME_FILL_ALPHA
        drawRect(
            color = frameColor.copy(alpha = fillAlpha),
            topLeft = Offset(left, top),
            size = Size(w, h),
        )
        // Обводка: dashed для PAGE-режима, solid+утолщённая для GRAB или
        // SCREEN-pinned (даёт визуальную обратную связь о смене состояния).
        val solid = isGrabbing || isScreenPinned
        drawRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(w, h),
            style =
                Stroke(
                    width = if (solid) FRAME_STROKE_PX_ACTIVE else FRAME_STROKE_PX,
                    pathEffect = if (solid) null else PathEffect.dashPathEffect(DASH_INTERVALS),
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

/**
 * Viewport-привязанный оверлей рамки лупы для режима
 * [MagnifierAttachment.SCREEN]. Рисуется поверх PDF-viewer'а на уровне
 * `DetailsContent` по абсолютным координатам `viewportRect`, поэтому при
 * скролле/зуме страницы рамка остаётся визуально неподвижной — без one-frame
 * лага, который был бы при рендеринге через page-coords + LaunchedEffect.
 */
@Composable
fun MagnifierScreenPinnedOverlay(
    viewportRect: Rect,
    frameColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val left = viewportRect.left
        val top = viewportRect.top
        val w = viewportRect.width
        val h = viewportRect.height
        drawRect(
            color = frameColor.copy(alpha = FRAME_FILL_ALPHA_GRAB),
            topLeft = Offset(left, top),
            size = Size(w, h),
        )
        drawRect(
            color = frameColor,
            topLeft = Offset(left, top),
            size = Size(w, h),
            style = Stroke(width = FRAME_STROKE_PX_ACTIVE),
        )
    }
}

private const val FRAME_FILL_ALPHA = 0.10f
private const val FRAME_FILL_ALPHA_GRAB = 0.20f
private const val FRAME_STROKE_PX = 2f
private const val FRAME_STROKE_PX_ACTIVE = 3f
private const val RESIZE_HANDLE_FRAC = 0.04f
private val DASH_INTERVALS = floatArrayOf(12f, 8f)
