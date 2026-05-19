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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
    pdfDrawingStateProvider: (pageIndex: Int) -> PdfDrawingState,
    toolMode: ToolMode,
    penSettings: PenSettings,
    markerSettings: MarkerSettings,
    eraserSettings: EraserSettings,
    eraserOverride: () -> Boolean,
    pencilModeEnabled: Boolean,
    onGestureStart: (pageIndex: Int, snapshot: List<DrawingPath>) -> Unit,
    onStrokeFinished: (pageIndex: Int, path: DrawingPath) -> Unit,
    onEraseFinished: (
        pageIndex: Int,
        before: List<DrawingPath>,
        after: List<DrawingPath>,
    ) -> Unit,
    onClose: () -> Unit,
    /**
     * Внешний контроллер ввода для нативного pen-stream'а (`WindowsPointerHook`).
     * Compose `pointerInput` его не видит, поэтому контроллер вызывается
     * напрямую из `DetailsContent`. Если `null` — панель работает только
     * с мышью/touch'ем через свой собственный `pointerInput`.
     */
    externalInputController: MagnifierInputController? = null,
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
                    pdfDrawingStateProvider = pdfDrawingStateProvider,
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
                    externalInputController = externalInputController,
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
    pdfDrawingStateProvider: (pageIndex: Int) -> PdfDrawingState,
    toolMode: ToolMode,
    penSettings: PenSettings,
    markerSettings: MarkerSettings,
    eraserSettings: EraserSettings,
    eraserOverride: () -> Boolean,
    pencilModeState: androidx.compose.runtime.State<Boolean>,
    eraserPos: androidx.compose.runtime.MutableState<Offset?>,
    onGestureStart: (Int, List<DrawingPath>) -> Unit,
    onStrokeFinished: (Int, DrawingPath) -> Unit,
    onEraseFinished: (Int, List<DrawingPath>, List<DrawingPath>) -> Unit,
    tablet: ru.kyamshanov.notepen.tablet.TabletInputController,
    externalInputController: MagnifierInputController?,
    modifier: Modifier = Modifier,
) {
    val livePath = remember { Path() }
    val scratch = remember { Path() }
    val frameColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onGloballyPositioned { coords ->
                state.updateContentBounds(coords.boundsInWindow())
            }
            .pointerInput(
                toolMode, penSettings, markerSettings, eraserSettings,
                state, externalInputController,
            ) {
                val panelW = size.width.toFloat()
                val panelH = size.height.toFloat()
                val panelSizeF = Size(panelW, panelH)
                val stylusEverSeen = tablet.stylusEverSeen
                detectStylusAwareDrag(
                    tablet = tablet,
                    isPalmRejectionActive = { pencilModeState.value || stylusEverSeen.value },
                    onDown = { offset, pressure, tilt ->
                        externalInputController?.onDown(offset, panelSizeF, pressure, tilt)
                    },
                    onMove = { offset, pressure, tilt ->
                        externalInputController?.onMove(offset, panelSizeF, pressure, tilt)
                    },
                    onUp = { externalInputController?.onUp(panelSizeF) },
                    onCancel = { externalInputController?.onCancel() },
                )
            },
    ) {
        val segments = state.segments
        if (segments.isEmpty()) return@Canvas

        val panelW = size.width
        val panelH = size.height
        val noExtent = ru.kyamshanov.notepen.annotation.domain.model.PageExtent.Pdf

        // Рендерим каждый сегмент в свою «полосу» панели:
        //  - PDF-тайл из соответствующего битмапа;
        //  - завершённые и live штрихи этой страницы.
        segments.forEach { seg ->
            val segTop = seg.panelTopFrac * panelH
            val segBottom = seg.panelBottomFrac * panelH
            val segH = (segBottom - segTop).coerceAtLeast(0f)
            if (segH <= 0f) return@forEach
            val target = seg.targetOnPage
            val tw = target.right - target.left
            val th = target.bottom - target.top
            if (tw <= 0f || th <= 0f) return@forEach

            val bmp = state.pageBitmap(seg.pageIndex)
            if (bmp != null) {
                val srcOffsetX = (target.left * bmp.width).toInt().coerceAtLeast(0)
                val srcOffsetY = (target.top * bmp.height).toInt().coerceAtLeast(0)
                val srcW = (tw * bmp.width).toInt()
                    .coerceAtLeast(1).coerceAtMost(bmp.width - srcOffsetX)
                val srcH = (th * bmp.height).toInt()
                    .coerceAtLeast(1).coerceAtMost(bmp.height - srcOffsetY)
                drawImage(
                    image = bmp,
                    srcOffset = IntOffset(srcOffsetX, srcOffsetY),
                    srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset(0, segTop.toInt()),
                    dstSize = IntSize(panelW.toInt(), segH.toInt()),
                )
            }

            // Штрихи: clip к полосе сегмента, чтобы соседи не пересекались.
            val virtW = panelW / tw
            val virtH = segH / th
            withTransform({
                clipRect(left = 0f, top = segTop, right = panelW, bottom = segBottom)
                translate(left = -target.left * virtW, top = segTop - target.top * virtH)
            }) {
                val pdfDrawingState = pdfDrawingStateProvider(seg.pageIndex)
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

        // Индикатор ластика — рисуем по первому сегменту (multi-page eraser
        // hover-индикация — отдельный кейс, не критичный для v1).
        val ePos = eraserPos.value
        if (toolMode == ToolMode.ERASER && ePos != null && segments.isNotEmpty()) {
            val seg = segments.first()
            val target = seg.targetOnPage
            val tw = target.right - target.left
            if (tw > 0f) {
                val segTop = seg.panelTopFrac * panelH
                val segH = (seg.panelBottomFrac - seg.panelTopFrac) * panelH
                val px = (ePos.x - target.left) / tw * panelW
                val py = segTop + (ePos.y - target.top) / (target.bottom - target.top) * segH
                val sizePx = eraserSettings.sizeNormalized * panelW / tw
                val halfPx = sizePx / 2f
                when (eraserSettings.shape) {
                    EraserShape.CIRCLE -> {
                        drawCircle(
                            color = frameColor.copy(alpha = FRAME_FILL_ALPHA),
                            radius = halfPx,
                            center = Offset(px, py),
                        )
                        drawCircle(
                            color = frameColor,
                            radius = halfPx,
                            center = Offset(px, py),
                            style = Stroke(width = 2f),
                        )
                    }
                    EraserShape.SQUARE -> {
                        drawRect(
                            color = frameColor.copy(alpha = FRAME_FILL_ALPHA),
                            topLeft = Offset(px - halfPx, py - halfPx),
                            size = Size(sizePx, sizePx),
                        )
                        drawRect(
                            color = frameColor,
                            topLeft = Offset(px - halfPx, py - halfPx),
                            size = Size(sizePx, sizePx),
                            style = Stroke(width = 2f),
                        )
                    }
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
