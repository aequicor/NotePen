package ru.kyamshanov.notepen.magnifier

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.EraserSettings
import ru.kyamshanov.notepen.EraserShape
import ru.kyamshanov.notepen.MarkerSettings
import ru.kyamshanov.notepen.PdfDrawingState
import ru.kyamshanov.notepen.PenSettings
import ru.kyamshanov.notepen.ToolMode
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.detectStylusAwareDrag
import ru.kyamshanov.notepen.drawLiveStroke
import ru.kyamshanov.notepen.drawStrokeWithPressure
import ru.kyamshanov.notepen.EraseGesture
import ru.kyamshanov.notepen.tablet.LocalTabletInputController

private const val FRAME_FILL_ALPHA = 0.10f

/**
 * Плавающее окно ввода magnifier'а.
 *
 * Состоит из titlebar (drag → перемещение, кнопки авто-скролла и закрытия),
 * увеличенного содержимого страницы (PDF-тайл + завершённые штрихи + live
 * stroke), и resize-handle в правом-нижнем углу.
 *
 * Pointer-вход внутри content области преобразуется в page-normalized
 * координаты целевой страницы через [panelLocalToPageNormalized] и
 * передаётся в [pdfDrawingState] / [EraseGesture] так же, как это делает
 * обычный pen-pipeline в `DrawablePdfPage`. Толщина штриха = такая же,
 * как у обычного пера на странице, — реальный визуальный размер на
 * странице соответствует выбранным [penSettings].
 *
 * Композбл не отображается, если `state.enabled == false`.
 */
@Composable
fun MagnifierInputPanel(
    state: MagnifierState,
    pdfDrawingState: PdfDrawingState,
    toolMode: ToolMode,
    penSettings: PenSettings,
    markerSettings: MarkerSettings,
    eraserSettings: EraserSettings,
    eraserOverride: () -> Boolean,
    pencilModeEnabled: Boolean,
    onGestureStart: (snapshot: List<DrawingPath>) -> Unit,
    onStrokeFinished: (path: DrawingPath) -> Unit,
    onEraseFinished: (before: List<DrawingPath>, after: List<DrawingPath>) -> Unit,
    onClose: () -> Unit,
) {
    if (!state.enabled) return

    val density = LocalDensity.current
    val tablet = LocalTabletInputController.current
    val pencilModeState = rememberUpdatedState(pencilModeEnabled)
    val eraserPos = remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(toolMode) {
        if (toolMode != ToolMode.ERASER) eraserPos.value = null
    }

    val panelOffsetDp = with(density) {
        IntOffset(state.panelTopLeft.x.toInt(), state.panelTopLeft.y.toInt())
    }
    val panelWidthDp = with(density) { state.panelSize.width.toDp() }
    val panelHeightDp = with(density) { state.panelSize.height.toDp() }
    val titleBarHeight = 32.dp

    Box(
        Modifier
            .offset { panelOffsetDp }
            .size(panelWidthDp, panelHeightDp + titleBarHeight + RESIZE_HANDLE_DP),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                // Title bar — drag → перемещение всей панели; кнопки авто-скролла и закрытия.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(titleBarHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(state) {
                            detectDragGestures(onDrag = { change, drag ->
                                state.movePanel(drag)
                                change.consume()
                            })
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Лупа",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { state.toggleAutoScroll() }) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = if (state.autoScrollEnabled) {
                                "Авто-прокрутка включена"
                            } else {
                                "Авто-прокрутка выключена"
                            },
                            tint = if (state.autoScrollEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть лупу",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Содержимое (увеличенная область страницы + штрихи + ввод).
                MagnifierContent(
                    state = state,
                    pdfDrawingState = pdfDrawingState,
                    toolMode = toolMode,
                    penSettings = penSettings,
                    markerSettings = markerSettings,
                    eraserSettings = eraserSettings,
                    eraserOverride = eraserOverride,
                    pencilModeState = pencilModeState,
                    eraserPos = eraserPos,
                    onGestureStart = onGestureStart,
                    onStrokeFinished = onStrokeFinished,
                    onEraseFinished = onEraseFinished,
                    tablet = tablet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds(),
                )

                // Resize-handle (drag → ресайз).
                Box(
                    Modifier
                        .align(Alignment.End)
                        .size(RESIZE_HANDLE_DP)
                        .background(MaterialTheme.colorScheme.outline)
                        .pointerInput(state) {
                            detectDragGestures(onDrag = { change, drag ->
                                state.resizePanel(
                                    Size(
                                        state.panelSize.width + drag.x,
                                        state.panelSize.height + drag.y,
                                    ),
                                )
                                change.consume()
                            })
                        },
                )
            }
        }
    }
}

private val RESIZE_HANDLE_DP = 16.dp

@Composable
private fun MagnifierContent(
    state: MagnifierState,
    pdfDrawingState: PdfDrawingState,
    toolMode: ToolMode,
    penSettings: PenSettings,
    markerSettings: MarkerSettings,
    eraserSettings: EraserSettings,
    eraserOverride: () -> Boolean,
    pencilModeState: androidx.compose.runtime.State<Boolean>,
    eraserPos: androidx.compose.runtime.MutableState<Offset?>,
    onGestureStart: (List<DrawingPath>) -> Unit,
    onStrokeFinished: (DrawingPath) -> Unit,
    onEraseFinished: (List<DrawingPath>, List<DrawingPath>) -> Unit,
    tablet: ru.kyamshanov.notepen.tablet.TabletInputController,
    modifier: Modifier = Modifier,
) {
    val livePath = remember { Path() }
    val scratch = remember { Path() }
    val frameColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(toolMode, penSettings, markerSettings, eraserSettings, state) {
                val panelW = size.width.toFloat()
                val panelH = size.height.toFloat()
                val panelSizeF = Size(panelW, panelH)
                var activeErase: EraseGesture? = null

                val stylusEverSeen = tablet.stylusEverSeen
                detectStylusAwareDrag(
                    tablet = tablet,
                    isPalmRejectionActive = { pencilModeState.value || stylusEverSeen.value },
                    onDown = { offset, pressure, tilt ->
                        val pageCanvasW = state.pageCanvasWidthPx
                        if (pageCanvasW > 0f && panelW > 0f && panelH > 0f) {
                            val page = panelLocalToPageNormalized(offset, panelSizeF, state.targetRect)
                            val effectiveTool = if (eraserOverride()) ToolMode.ERASER else toolMode
                            when (effectiveTool) {
                                ToolMode.PEN, ToolMode.MARKER -> {
                                    val widthPx = if (effectiveTool == ToolMode.PEN) {
                                        penSettings.strokeWidth
                                    } else {
                                        markerSettings.strokeWidth
                                    }
                                    val colorArgb = if (effectiveTool == ToolMode.PEN) {
                                        penSettings.colorArgb
                                    } else {
                                        markerSettings.colorArgb
                                    }
                                    onGestureStart(pdfDrawingState.currentPaths.toList())
                                    pdfDrawingState.strokeColorArgb.value = colorArgb
                                    pdfDrawingState.strokeWidth.value = widthPx
                                    pdfDrawingState.startDrawing(
                                        x = page.x,
                                        y = page.y,
                                        normalizedStrokeWidth = widthPx / pageCanvasW,
                                        pressure = pressure,
                                        tilt = tilt,
                                    )
                                }
                                ToolMode.ERASER -> {
                                    activeErase = EraseGesture(
                                        pdfDrawingState = pdfDrawingState,
                                        eraserSettings = eraserSettings,
                                        eraserPos = eraserPos,
                                        onGestureStart = onGestureStart,
                                        onEraseFinished = onEraseFinished,
                                    ).also { it.start(page.x, page.y) }
                                }
                                ToolMode.NONE -> Unit
                            }
                        }
                    },
                    onMove = { offset, pressure, tilt ->
                        if (panelW > 0f && panelH > 0f) {
                            val page = panelLocalToPageNormalized(offset, panelSizeF, state.targetRect)
                            val erase = activeErase
                            if (erase != null) {
                                erase.move(page.x, page.y)
                            } else if (pdfDrawingState.isDrawing.value) {
                                pdfDrawingState.addPoint(
                                    x = page.x,
                                    y = page.y,
                                    pressure = pressure,
                                    tilt = tilt,
                                )
                            }
                        }
                    },
                    onUp = {
                        val erase = activeErase
                        if (erase != null) {
                            erase.end()
                            activeErase = null
                        } else {
                            val completed = pdfDrawingState.finishDrawing()
                            if (completed != null) {
                                onStrokeFinished(completed)
                                if (state.autoScrollEnabled) {
                                    val last = completed.points.lastOrNull()
                                    if (last != null) {
                                        val panelLocal = pageNormalizedToPanelLocal(
                                            Offset(last.x, last.y),
                                            panelSizeF,
                                            state.targetRect,
                                        )
                                        if (panelLocal.x > panelW * AUTO_SCROLL_TRIGGER_FRAC) {
                                            state.shiftTargetForAutoscroll(AutoScrollDir.RIGHT)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    onCancel = {
                        val erase = activeErase
                        if (erase != null) {
                            erase.cancel()
                            activeErase = null
                        } else {
                            pdfDrawingState.finishDrawing()
                        }
                    },
                )
            },
    ) {
        // PDF тайл из исходного битмапа страницы.
        val bmp = state.pageBitmap
        val target = state.targetRect
        if (bmp != null) {
            val srcOffsetX = (target.left * bmp.width).toInt().coerceAtLeast(0)
            val srcOffsetY = (target.top * bmp.height).toInt().coerceAtLeast(0)
            val srcW = ((target.right - target.left) * bmp.width).toInt()
                .coerceAtLeast(1)
                .coerceAtMost(bmp.width - srcOffsetX)
            val srcH = ((target.bottom - target.top) * bmp.height).toInt()
                .coerceAtLeast(1)
                .coerceAtMost(bmp.height - srcOffsetY)
            drawImage(
                image = bmp,
                srcOffset = IntOffset(srcOffsetX, srcOffsetY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
        }

        // Завершённые штрихи и live stroke в page-normalized → мап через scale + translate.
        val tw = target.right - target.left
        val th = target.bottom - target.top
        if (tw > 0f && th > 0f) {
            val virtW = size.width / tw
            val virtH = size.height / th
            withTransform({
                translate(left = -target.left * virtW, top = -target.top * virtH)
            }) {
                // Magnifier render target — в чистых PDF-page координатах,
                // поэтому extent = Pdf (никакого extent-сдвига внутри draw
                // функций). Смещение под target.left/top уже сделано
                // withTransform выше.
                val noExtent = ru.kyamshanov.notepen.annotation.domain.model.PageExtent.Pdf
                pdfDrawingState.currentPaths.forEach { path ->
                    drawStrokeWithPressure(
                        stroke = path,
                        pdfWidth = virtW,
                        pdfHeight = virtH,
                        extent = noExtent,
                        scratch = scratch,
                    )
                }
                if (pdfDrawingState.isDrawing.value && pdfDrawingState.livePoints.size > 1) {
                    drawLiveStroke(
                        points = pdfDrawingState.livePoints,
                        colorArgb = pdfDrawingState.liveColorArgb.value,
                        normalizedStrokeWidth = pdfDrawingState.liveStrokeWidth.value,
                        pdfWidth = virtW,
                        pdfHeight = virtH,
                        extent = noExtent,
                        scratch = livePath,
                    )
                }
            }
        }

        // Индикатор ластика.
        val ePos = eraserPos.value
        if (toolMode == ToolMode.ERASER && ePos != null) {
            val panelPos = pageNormalizedToPanelLocal(
                Offset(ePos.x, ePos.y),
                Size(size.width, size.height),
                target,
            )
            val sizePx = eraserSettings.sizeNormalized * size.width / (target.right - target.left)
            val halfPx = sizePx / 2f
            when (eraserSettings.shape) {
                EraserShape.CIRCLE -> {
                    drawCircle(
                        color = frameColor.copy(alpha = FRAME_FILL_ALPHA),
                        radius = halfPx,
                        center = panelPos,
                    )
                    drawCircle(
                        color = frameColor,
                        radius = halfPx,
                        center = panelPos,
                        style = Stroke(width = 2f),
                    )
                }
                EraserShape.SQUARE -> {
                    drawRect(
                        color = frameColor.copy(alpha = FRAME_FILL_ALPHA),
                        topLeft = Offset(panelPos.x - halfPx, panelPos.y - halfPx),
                        size = Size(sizePx, sizePx),
                    )
                    drawRect(
                        color = frameColor,
                        topLeft = Offset(panelPos.x - halfPx, panelPos.y - halfPx),
                        size = Size(sizePx, sizePx),
                        style = Stroke(width = 2f),
                    )
                }
            }
        }
    }
}

/**
 * Если перо ушло за эту долю ширины панели — после lift-off сдвигаем
 * рамку (Scribble-like). 75% даёт комфортное «упреждение», когда
 * пользователь ещё не упёрся в край.
 */
private const val AUTO_SCROLL_TRIGGER_FRAC = 0.75f
