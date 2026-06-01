package ru.kyamshanov.notepen.magnifier

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.detectStylusAwareDrag
import ru.kyamshanov.notepen.drawLiveStroke
import ru.kyamshanov.notepen.drawStrokeWithPressure
import ru.kyamshanov.notepen.drawing.api.EraseGesture
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import ru.kyamshanov.notepen.tools.marker.drawMarkerStroke
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas

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
    val eraserPos = remember { mutableStateOf<ru.kyamshanov.notepen.drawing.api.EraserPosition?>(null) }

    LaunchedEffect(toolMode) {
        if (toolMode != ToolMode.ERASER) eraserPos.value = null
    }

    val panelWidthDp = with(density) { state.panelSize.width.toDp() }
    val panelHeightDp = with(density) { state.panelSize.height.toDp() }

    // Цвет грипа-ресайза резолвим здесь: внутри Canvas-лямбды темы нет.
    val resizeGripColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    // Позиционирование панели (panelTopLeft) выполняет хост через offset
    // Popup'а в `EditorPanel`; здесь задаётся только размер. Так панель
    // рисуется в Popup-слое поверх тулрейла, а не под ним.
    Box(
        Modifier.size(panelWidthDp, panelHeightDp + HEADER_HEIGHT + FOOTER_HEIGHT),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 2.dp,
            shadowElevation = 10.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                // ── Шапка: drag → перемещение панели; закрепление и авто-скролл.
                //    Граббер-пилюля подсказывает, что за шапку можно тащить. ──
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(HEADER_HEIGHT)
                        .pointerInput(state) {
                            detectDragGestures(onDrag = { change, drag ->
                                state.movePanel(drag)
                                change.consume()
                            })
                        },
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 6.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Лупа",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        val pinned = state.attachment == MagnifierAttachment.SCREEN
                        IconButton(onClick = { state.toggleAttachment() }) {
                            Icon(
                                imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (pinned) "Открепить от экрана" else "Закрепить на экране",
                                tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { state.toggleAutoScroll() }) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription =
                                    if (state.autoScrollEnabled) "Авто-прокрутка включена" else "Авто-прокрутка выключена",
                                tint =
                                    if (state.autoScrollEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
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
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds(),
                )

                // ── Нижний блок увеличения: кратность лупы (−/+), индикатор ×N
                //    и крестик. Крестик здесь, а не поверх превью: на планшете
                //    до низа дотянуться удобнее, и он не липнет к контенту. ──
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(FOOTER_HEIGHT)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(start = 8.dp, end = RESIZE_HANDLE_DP + 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = { state.magnifyBy(1f / ZOOM_STEP) }) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Уменьшить кратность",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val zf = zoomFactor(state.panelSize, state.targetRect, state.pageCanvasWidthPx)
                    Text(
                        // Дробное значение (1 знак): целочисленное округление
                        // показывало «×1» почти всегда и вводило в заблуждение.
                        text = if (zf > 0f) "×${(zf * 10f).roundToInt() / 10f}" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(min = 48.dp),
                    )
                    IconButton(onClick = { state.magnifyBy(ZOOM_STEP) }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Увеличить кратность",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть лупу",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Resize-уголок — правый нижний угол (тонкий диагональный грип). Лежит
        // над зарезервированным end-отступом нижнего блока, поэтому не
        // пересекается с крестиком.
        Canvas(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(RESIZE_HANDLE_DP)
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
        ) {
            val s = size.minDimension
            val stroke = s * 0.08f
            listOf(0.45f, 0.75f).forEach { f ->
                drawLine(
                    color = resizeGripColor,
                    start = Offset(s * f, s),
                    end = Offset(s, s * f),
                    strokeWidth = stroke,
                )
            }
        }
    }
}

private val RESIZE_HANDLE_DP = 16.dp
private val HEADER_HEIGHT = 36.dp
private val FOOTER_HEIGHT = 52.dp

/** Шаг изменения кратности лупы кнопками −/+ в нижнем блоке. */
private const val ZOOM_STEP = 1.25f

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
    eraserPos: androidx.compose.runtime.MutableState<ru.kyamshanov.notepen.drawing.api.EraserPosition?>,
    onGestureStart: (Int, List<DrawingPath>) -> Unit,
    onStrokeFinished: (Int, DrawingPath) -> Unit,
    onEraseFinished: (Int, List<DrawingPath>, List<DrawingPath>) -> Unit,
    tablet: ru.kyamshanov.notepen.tablet.TabletInputController,
    externalInputController: MagnifierInputController?,
    modifier: Modifier = Modifier,
) {
    val livePath = remember { Path() }
    val frameColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onGloballyPositioned { coords ->
                    state.updateContentBounds(coords.boundsInWindow())
                }
                .then(
                    if (SupportsPanelGestureZoom) {
                        Modifier.pointerInput(state) {
                            detectPanelTransformGestures(
                                state = state,
                                pencilModeEnabled = { pencilModeState.value },
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .pointerInput(
                    toolMode,
                    penSettings,
                    markerSettings,
                    eraserSettings,
                    state,
                    externalInputController,
                ) {
                    val panelW = size.width.toFloat()
                    val panelH = size.height.toFloat()
                    val panelSizeF = Size(panelW, panelH)
                    detectStylusAwareDrag(
                        tablet = tablet,
                        // Гейт palm-rejection — только pencil mode, без
                        // защёлки `stylusEverSeen`: иначе после первого касания
                        // пера палец навсегда переставал писать внутри лупы,
                        // даже когда pencil mode выключен. См. зеркальную правку
                        // в DetailsContent.kt.
                        isPalmRejectionActive = { pencilModeState.value },
                        // Внутри панели лупы палец допускается к рисованию, когда
                        // pencil mode выключен. В обычном `DrawablePdfPage` палец
                        // зарезервирован под scrollable (вертикальный скролл PDF),
                        // но в magnifier-панели скроллить нечего, и пользователь
                        // ожидает писать пальцем. При включённом pencil mode
                        // ветка одноточечного pan'а в `detectPanelTransformGestures`
                        // поглощает finger-события раньше — сюда они не доходят.
                        //
                        // captureGesture ANY pointer type — в панели нет конкурирующего
                        // scrollable, поэтому PointerType.Unknown захватывается наравне
                        // с Touch; иначе первое касание (Unknown) уходило бы в никуда.
                        captureGesture = { !pencilModeState.value },
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
                val srcW =
                    (tw * bmp.width).toInt()
                        .coerceAtLeast(1).coerceAtMost(bmp.width - srcOffsetX)
                val srcH =
                    (th * bmp.height).toInt()
                        .coerceAtLeast(1).coerceAtMost(bmp.height - srcOffsetY)
                drawImage(
                    image = bmp,
                    srcOffset = IntOffset(srcOffsetX, srcOffsetY),
                    srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset(0, segTop.toInt()),
                    dstSize = IntSize(panelW.toInt(), segH.toInt()),
                )
            }

            val pdfDrawingState = pdfDrawingStateProvider(seg.pageIndex)
            val pageExtent = pdfDrawingState.extent.value
            // virtW/virtH — маппинг нормализованных PDF-координат в panel-пиксели
            // полосы сегмента (используется и anti-flicker'ом, и live-tail'ом).
            val virtW = panelW / tw
            val virtH = segH / th

            // Завершенные штрихи рисуем напрямую тем же transform'ом, что и
            // live-tail. Так панель лупы не зависит от готовности кэша страницы
            // после переноса target между страницами разворота.
            val completedPaths = pdfDrawingState.currentPaths
            if (completedPaths.isNotEmpty()) {
                withTransform({
                    clipRect(left = 0f, top = segTop, right = panelW, bottom = segBottom)
                    translate(
                        left = (pageExtent.left - target.left) * virtW,
                        top = segTop + (pageExtent.top - target.top) * virtH,
                    )
                }) {
                    completedPaths.forEach { path ->
                        drawStrokeWithPressure(
                            stroke = path,
                            pdfWidth = virtW,
                            pdfHeight = virtH,
                            extent = pageExtent,
                            scratch = livePath,
                        )
                    }
                }
            }

            // Live-штрих рисуем целиком напрямую тем же transform'ом. Это
            // важнее небольшого incremental-cache выигрыша: при переносе лупы
            // между страницами разворота bitmap-кэш легко рассинхронизировать
            // с текущим page/target-space.
            if (pdfDrawingState.isDrawing.value && pdfDrawingState.livePoints.size > 1) {
                withTransform({
                    clipRect(left = 0f, top = segTop, right = panelW, bottom = segBottom)
                    translate(
                        left = (pageExtent.left - target.left) * virtW,
                        top = segTop + (pageExtent.top - target.top) * virtH,
                    )
                }) {
                    if (pdfDrawingState.liveToolKind.value == ToolKind.MARKER) {
                        drawMarkerStroke(
                            points = pdfDrawingState.livePoints,
                            colorArgb = pdfDrawingState.liveColorArgb.value,
                            normalizedStrokeWidth = pdfDrawingState.liveStrokeWidth.value,
                            pdfWidth = virtW,
                            pdfHeight = virtH,
                            extent = pageExtent,
                            scratch = livePath,
                        )
                    } else {
                        val totalSegments = pdfDrawingState.livePoints.size - 1
                        drawLiveStroke(
                            points = pdfDrawingState.livePoints,
                            colorArgb = pdfDrawingState.liveColorArgb.value,
                            normalizedStrokeWidth = pdfDrawingState.liveStrokeWidth.value,
                            pdfWidth = virtW,
                            pdfHeight = virtH,
                            extent = pageExtent,
                            scratch = livePath,
                            fromSegmentIndex = 0,
                            toSegmentIndexExclusive = totalSegments,
                        )
                    }
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

/**
 * Pinch/pan жест внутри content-области панели лупы.
 *
 * Двухпальцевый pinch + pan работает всегда: ≥2 указателя — масштабируем
 * через [MagnifierState.zoomTargetAroundPanelFocus] и панорамируем рамку через
 * [MagnifierState.panTargetByPanelPx] по дельте центроида (со сменой знака —
 * content-follows-fingers, как в Maps / photo viewer'ах).
 *
 * Однопальцевый touch в обычном режиме не консумится — пролетает в
 * stylus-aware drag ниже и используется как обычное письмо. Но когда включён
 * **pencil mode** (`pencilModeEnabled() == true`), палец заведомо не пишет
 * (palm rejection), поэтому один палец = панорама рамки лупы. Это удобнее
 * двухпальцевого жеста, когда вторая рука держит стилус.
 *
 * Подключается условно — только на платформах с [SupportsPanelGestureZoom]
 * (Android). На desktop'е роль pinch/pan играют мышь+drag рамки на странице
 * и hotkeys.
 */
private suspend fun PointerInputScope.detectPanelTransformGestures(
    state: MagnifierState,
    pencilModeEnabled: () -> Boolean,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var inTransform = false
        var inSinglePan = false
        var lastCentroid = Offset.Zero
        var lastDistance = 0f
        var lastSinglePos = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            val panelSize = Size(size.width.toFloat(), size.height.toFloat())
            if (pressed.size < 2) {
                // Single-pointer ветка: pan только если включён pencil mode
                // **и** указатель — это палец/мышь, а не стилус. Stylus в
                // pencil mode = письмо, его трогать нельзя — иначе рамка лупы
                // ездит при каждом штрихе.
                inTransform = false
                val p = pressed.first()
                val isStylus =
                    p.type == PointerType.Stylus ||
                        p.type == PointerType.Eraser
                if (!pencilModeEnabled() || isStylus) {
                    inSinglePan = false
                    continue
                }
                if (inSinglePan) {
                    val pan = p.position - lastSinglePos
                    if (pan != Offset.Zero) {
                        state.panTargetByPanelPx(-pan, panelSize)
                    }
                }
                lastSinglePos = p.position
                inSinglePan = true
                p.consume()
                continue
            }
            inSinglePan = false
            var sx = 0f
            var sy = 0f
            pressed.forEach {
                sx += it.position.x
                sy += it.position.y
            }
            val centroid = Offset(sx / pressed.size, sy / pressed.size)
            var distSum = 0f
            pressed.forEach { distSum += (it.position - centroid).getDistance() }
            val avgDist = distSum / pressed.size
            if (inTransform) {
                val scale =
                    if (lastDistance > 0f && avgDist > 0f) {
                        avgDist / lastDistance
                    } else {
                        1f
                    }
                if (scale != 1f) {
                    state.zoomTargetAroundPanelFocus(scale, centroid, panelSize)
                }
                val pan = centroid - lastCentroid
                if (pan != Offset.Zero) {
                    state.panTargetByPanelPx(-pan, panelSize)
                }
            }
            inTransform = true
            lastCentroid = centroid
            lastDistance = avgDist
            pressed.forEach { it.consume() }
        }
    }
}
