package ru.kyamshanov.notepen.magnifier

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as GraphicsCanvas
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
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
                    val pinned = state.attachment == MagnifierAttachment.SCREEN
                    IconButton(onClick = { state.toggleAttachment() }) {
                        Icon(
                            imageVector = if (pinned) {
                                Icons.Filled.PushPin
                            } else {
                                Icons.Outlined.PushPin
                            },
                            contentDescription = if (pinned) {
                                "Открепить от экрана"
                            } else {
                                "Закрепить на экране"
                            },
                            tint = if (pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
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

        // Кнопка закрытия — плавающая в правом нижнем углу панели (удобнее
        // дотягиваться большим пальцем, чем до титул-бара сверху). Отодвинута
        // вверх от resize-хэндла, чтобы тач-таргеты не пересекались.
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = -(RESIZE_HANDLE_DP + 4.dp)),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Закрыть лупу",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val frameColor = MaterialTheme.colorScheme.primary

    // Кэш завершённых штрихов per-page. Аналогично `completedLayer` в
    // [DrawablePdfPage]: на каждый кадр в Canvas раньше итерировался весь
    // `currentPaths.forEach { drawStrokeWithPressure }`, что при активном пере
    // (≥120 Hz и O(N) на live-сэмпл) давало заметный лаг по мере накопления
    // инка на странице. Здесь штрихи запекаются в off-screen `ImageBitmap`
    // размера PDF-битмапа сегмента и инвалидируются только при бампе
    // `historyVersion` (finishDrawing / undo / redo / eraser).
    val completedLayers = rememberMagnifierCompletedLayers(state, pdfDrawingStateProvider)

    // Инкрементальный кэш live-штриха для активной страницы. «Стабильные»
    // сегменты (у которых уже устоялись соседи p[i-1]/p[i+2]) запекаются
    // в off-screen `ImageBitmap` один раз через `drawLiveStroke` с полной
    // varying-width фиделити. Каждый кадр Canvas блитит этот битмап и
    // дорисовывает только нестабильный «хвост» из последних
    // [MAGNIFIER_LIVE_TIP_SEGMENTS] сегментов — благодаря чему per-frame
    // стоимость live-рендера константна (≈10 GPU-вызовов) при любой длине
    // штриха.
    val liveLayer = rememberMagnifierLiveLayer(state, pdfDrawingStateProvider)

    Canvas(
        modifier = modifier
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
                toolMode, penSettings, markerSettings, eraserSettings,
                state, externalInputController,
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
        val noExtent = PageExtent.Pdf

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

            // Завершённые штрихи — один блит из закэшированного слоя
            // (см. `rememberMagnifierCompletedLayers`). Слой имеет ту же
            // размерность, что и PDF-битмап сегмента, поэтому src/dst-маппинг
            // совпадает 1:1 с PDF выше.
            val completed = completedLayers[seg.pageIndex]
            if (completed != null) {
                val srcOffsetX = (target.left * completed.width).toInt().coerceAtLeast(0)
                val srcOffsetY = (target.top * completed.height).toInt().coerceAtLeast(0)
                val srcW = (tw * completed.width).toInt()
                    .coerceAtLeast(1).coerceAtMost(completed.width - srcOffsetX)
                val srcH = (th * completed.height).toInt()
                    .coerceAtLeast(1).coerceAtMost(completed.height - srcOffsetY)
                drawImage(
                    image = completed,
                    srcOffset = IntOffset(srcOffsetX, srcOffsetY),
                    srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset(0, segTop.toInt()),
                    dstSize = IntSize(panelW.toInt(), segH.toInt()),
                )
            }

            val pdfDrawingState = pdfDrawingStateProvider(seg.pageIndex)
            val activeLayer = liveLayer?.takeIf { it.pageIndex == seg.pageIndex }
            val liveBmp = activeLayer?.bitmap
            if (liveBmp != null) {
                // liveLayer хранится уже в panel-координатах (см.
                // `rememberMagnifierLiveLayer`), поэтому блитим полностью
                // битмап 1:1 в полосу сегмента — без upscale/blur.
                drawImage(
                    image = liveBmp,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(liveBmp.width, liveBmp.height),
                    dstOffset = IntOffset(0, segTop.toInt()),
                    dstSize = IntSize(panelW.toInt(), segH.toInt()),
                )
            }

            // Хвост live-штриха: запечённые сегменты уже в `liveBmp`, рисуем
            // только последние [MAGNIFIER_LIVE_TIP_SEGMENTS] (или весь штрих,
            // если он короче порога).
            val virtW = panelW / tw
            val virtH = segH / th
            if (pdfDrawingState.isDrawing.value && pdfDrawingState.livePoints.size > 1) {
                val totalSegments = pdfDrawingState.livePoints.size - 1
                val tipFrom = activeLayer?.bakedSegments?.coerceAtMost(totalSegments) ?: 0
                withTransform({
                    clipRect(left = 0f, top = segTop, right = panelW, bottom = segBottom)
                    translate(left = -target.left * virtW, top = segTop - target.top * virtH)
                }) {
                    drawLiveStroke(
                        points = pdfDrawingState.livePoints,
                        colorArgb = pdfDrawingState.liveColorArgb.value,
                        normalizedStrokeWidth = pdfDrawingState.liveStrokeWidth.value,
                        pdfWidth = virtW,
                        pdfHeight = virtH,
                        extent = noExtent,
                        scratch = livePath,
                        fromSegmentIndex = tipFrom,
                        toSegmentIndexExclusive = totalSegments,
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

/**
 * Бакет для cache dim — устраняет per-pixel-инвалидацию `remember`-ключа
 * при микро-изменениях `panelSize`/`pageBitmap.width`.
 */
private const val MAGNIFIER_INK_CACHE_BUCKET_PX = 256

/**
 * Хард-кэп для **completedLayer** — кэша уже завершённых штрихов. Эти кэши
 * пересобираются только при pen-up / undo / redo (historyVersion bump),
 * texture upload — один раз на штрих, поэтому можно держать высокое
 * разрешение для резкости при зуме. Привязан к разрешению `pageBitmap`
 * (high-res ~4000 px), bucketed.
 */
private const val MAGNIFIER_COMPLETED_CACHE_MAX_DIM_PX = 4096

/**
 * Хард-кэп для **liveLayer** — кэша текущего рисуемого штриха. liveLayer
 * хранит штрихи в **panel-координатах** (а не в PDF-нормализованных, как
 * completedLayer), то есть битмап ~= размер содержимого панели. drawImage
 * блитит его 1:1 → нет upscale-блёра, видна толщина от давления. Cap нужен
 * только защитный — типично битмап получится 1200-1500 px.
 */
private const val MAGNIFIER_LIVE_CACHE_MAX_DIM_PX = 2048

/**
 * Бакетированный размер completed-кэша. Берёт разрешение source-битмапа
 * страницы (`pageBitmap`), кэпит и бакетирует. При приходе high-res-битмапа
 * посреди штриха ключ может измениться один раз — но completedLayer
 * относится к **завершённым** штрихам, которых во время рисования нет
 * новых, так что rebuild дешёвый.
 */
private fun magnifierCompletedCacheDim(pageBitmap: ImageBitmap?): IntSize {
    val bw = pageBitmap?.width ?: 0
    val bh = pageBitmap?.height ?: 0
    if (bw <= 0 || bh <= 0) return IntSize.Zero
    val maxDim = MAGNIFIER_COMPLETED_CACHE_MAX_DIM_PX
    val bucket = MAGNIFIER_INK_CACHE_BUCKET_PX
    val w = bw.coerceAtMost(maxDim)
    val h = bh.coerceAtMost(maxDim)
    return IntSize(
        width = ((w + bucket - 1) / bucket * bucket).coerceAtLeast(bucket),
        height = ((h + bucket - 1) / bucket * bucket).coerceAtLeast(bucket),
    )
}

/**
 * Бакетированный размер live-кэша. Зависит от `panelSize` (не от pageBitmap),
 * поэтому стабилен при приходе high-res-битмапа посреди штриха.
 */
private fun magnifierLiveCacheDim(panelSize: Size): IntSize {
    val maxDim = MAGNIFIER_LIVE_CACHE_MAX_DIM_PX
    val bucket = MAGNIFIER_INK_CACHE_BUCKET_PX
    val w = panelSize.width.toInt().coerceAtMost(maxDim)
    val h = panelSize.height.toInt().coerceAtMost(maxDim)
    return IntSize(
        width = ((w + bucket - 1) / bucket * bucket).coerceAtLeast(bucket),
        height = ((h + bucket - 1) / bucket * bucket).coerceAtLeast(bucket),
    )
}

/**
 * Запекает завершённые штрихи каждой видимой страницы лупы в off-screen
 * [ImageBitmap]. Слой пересобирается только при бампе `historyVersion`
 * (finishDrawing / undo / redo / eraser) или при изменении бакетированного
 * `cacheDim` — поэтому per-frame стоимость отрисовки штрихов в Canvas
 * сведена к одиночному `drawImage` среза вместо итерации `currentPaths`
 * на каждый сэмпл пера.
 */
@Composable
private fun rememberMagnifierCompletedLayers(
    state: MagnifierState,
    pdfDrawingStateProvider: (Int) -> PdfDrawingState,
): Map<Int, ImageBitmap?> {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val result = mutableMapOf<Int, ImageBitmap?>()
    state.segments.forEach { seg ->
        key(seg.pageIndex) {
            val pdfDrawingState = pdfDrawingStateProvider(seg.pageIndex)
            val cacheDim = magnifierCompletedCacheDim(state.pageBitmap(seg.pageIndex))
            val historyVersion = pdfDrawingState.historyVersion.value
            val pathsEmpty = pdfDrawingState.currentPaths.isEmpty()
            val layer = remember(cacheDim, historyVersion, pathsEmpty) {
                if (pathsEmpty) {
                    null
                } else {
                    buildMagnifierCompletedLayer(
                        paths = pdfDrawingState.currentPaths.toList(),
                        cacheW = cacheDim.width,
                        cacheH = cacheDim.height,
                        density = density,
                        layoutDirection = layoutDirection,
                    )
                }
            }
            result[seg.pageIndex] = layer
        }
    }
    return result
}

/**
 * Сколько последних сегментов live-штриха не запекаем в [MagnifierLiveLayerHolder]
 * и рисуем каждый кадр через [drawLiveStroke]. Эти сегменты ещё не имеют
 * устоявшихся соседей (`p[i+2]` для Catmull-Rom может быть точкой, которой
 * скоро «передвинется» по мере прихода новых сэмплов), поэтому запекать их
 * рано. 6 — компромисс между качеством джойнтов и стоимостью кадра.
 */
private const val MAGNIFIER_LIVE_TIP_SEGMENTS = 6

/**
 * Holder инкрементального live-кэша в **panel-координатах**. Хранится через
 * `remember { ... }` без `mutableStateOf` — Compose не подписывается на
 * изменения этих полей, чтобы запись `bakedSegments` во время composition
 * не запускала каскад инвалидаций. Canvas re-invalidates по наблюдаемым
 * `livePoints.size` / `isDrawing.value` и при следующем drawscope-runе
 * читает свежие значения holder'а.
 *
 * Битмап содержит срез страницы, видимый сквозь рамку лупы, отрендеренный
 * в panel-разрешении 1:1. Кэш инвалидируется при смене страницы, размера
 * содержимого панели или прямоугольника `targetOnPage` (последнее во время
 * штриха обычно не происходит — autoscroll срабатывает на pen-up).
 */
private class MagnifierLiveLayerHolder {
    var bitmap: ImageBitmap? = null
    var pageIndex: Int = -1
    var cacheW: Int = 0
    var cacheH: Int = 0
    var targetLeft: Float = Float.NaN
    var targetTop: Float = Float.NaN
    var targetW: Float = Float.NaN
    var targetH: Float = Float.NaN
    var bakedSegments: Int = 0

    fun reset() {
        bitmap = null
        pageIndex = -1
        cacheW = 0
        cacheH = 0
        targetLeft = Float.NaN
        targetTop = Float.NaN
        targetW = Float.NaN
        targetH = Float.NaN
        bakedSegments = 0
    }
}

@Composable
private fun rememberMagnifierLiveLayer(
    state: MagnifierState,
    pdfDrawingStateProvider: (Int) -> PdfDrawingState,
): MagnifierLiveLayerHolder? {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val holder = remember { MagnifierLiveLayerHolder() }

    // Активная для рисования страница (в один момент времени их максимум 1).
    val activeSeg = state.segments.firstOrNull {
        pdfDrawingStateProvider(it.pageIndex).isDrawing.value
    }
    if (activeSeg == null) {
        if (holder.bitmap != null) holder.reset()
        return null
    }

    val pdfDrawingState = pdfDrawingStateProvider(activeSeg.pageIndex)
    val livePoints = pdfDrawingState.livePoints
    val size = livePoints.size
    if (size < 2) return null

    val target = activeSeg.targetOnPage
    val tw = target.right - target.left
    val th = target.bottom - target.top
    if (tw <= 0f || th <= 0f) return null

    // Размер liveLayer — содержимое панели, занимаемое активным сегментом
    // (single-segment кейс: вся высота). Берём `contentBoundsInViewport`,
    // которое обновляется `onGloballyPositioned` Canvas'а и совпадает с
    // фактическим пиксельным размером выводимой области → бит-в-бит 1:1.
    val contentSize = state.contentBoundsInViewport.size
    val segFrac = (activeSeg.panelBottomFrac - activeSeg.panelTopFrac).coerceAtLeast(0f)
    val segContentH = contentSize.height * segFrac
    val cacheDim = magnifierLiveCacheDim(Size(contentSize.width, segContentH))
    val cacheW = cacheDim.width
    val cacheH = cacheDim.height
    if (cacheW <= 0 || cacheH <= 0) return null

    if (holder.bitmap == null ||
        holder.pageIndex != activeSeg.pageIndex ||
        holder.cacheW != cacheW ||
        holder.cacheH != cacheH ||
        holder.targetLeft != target.left ||
        holder.targetTop != target.top ||
        holder.targetW != tw ||
        holder.targetH != th
    ) {
        holder.bitmap = ImageBitmap(cacheW, cacheH)
        holder.pageIndex = activeSeg.pageIndex
        holder.cacheW = cacheW
        holder.cacheH = cacheH
        holder.targetLeft = target.left
        holder.targetTop = target.top
        holder.targetW = tw
        holder.targetH = th
        holder.bakedSegments = 0
    }

    val totalSegments = size - 1
    val targetBakeEnd = (totalSegments - MAGNIFIER_LIVE_TIP_SEGMENTS).coerceAtLeast(0)
    if (targetBakeEnd > holder.bakedSegments) {
        val bitmap = holder.bitmap ?: return holder
        val canvas = GraphicsCanvas(bitmap)
        val scope = CanvasDrawScope()
        val scratch = Path()
        // virtW/virtH — те же, что используются в Canvas-tail для пересчёта
        // нормализованных PDF-координат в panel-координаты (см. внутри
        // `withTransform` в drawscope для tail-сегментов).
        val virtW = cacheW.toFloat() / tw
        val virtH = cacheH.toFloat() / th
        scope.draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = canvas,
            size = Size(cacheW.toFloat(), cacheH.toFloat()),
        ) {
            translate(left = -target.left * virtW, top = -target.top * virtH) {
                drawLiveStroke(
                    points = livePoints,
                    colorArgb = pdfDrawingState.liveColorArgb.value,
                    normalizedStrokeWidth = pdfDrawingState.liveStrokeWidth.value,
                    pdfWidth = virtW,
                    pdfHeight = virtH,
                    extent = PageExtent.Pdf,
                    scratch = scratch,
                    fromSegmentIndex = holder.bakedSegments,
                    toSegmentIndexExclusive = targetBakeEnd,
                )
            }
        }
        holder.bakedSegments = targetBakeEnd
    }
    return holder
}

private fun buildMagnifierCompletedLayer(
    paths: List<DrawingPath>,
    cacheW: Int,
    cacheH: Int,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap? {
    if (cacheW <= 0 || cacheH <= 0 || paths.isEmpty()) return null
    // Магнифер рендерит штрихи в координатах PDF-страницы (extent = Pdf) — см.
    // основной Canvas. Здесь зеркалим то же поведение: pdfWidth/pdfHeight равны
    // размерам PDF-битмапа сегмента, extent.left/top = 0.
    val pdfBw = cacheW.toFloat()
    val pdfBh = cacheH.toFloat()
    val bmp = ImageBitmap(cacheW, cacheH)
    val canvas = GraphicsCanvas(bmp)
    val scope = CanvasDrawScope()
    val scratch = Path()
    scope.draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = canvas,
        size = Size(pdfBw, pdfBh),
    ) {
        paths.forEach { path ->
            drawStrokeWithPressure(
                stroke = path,
                pdfWidth = pdfBw,
                pdfHeight = pdfBh,
                extent = PageExtent.Pdf,
                scratch = scratch,
            )
        }
    }
    return bmp
}

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
                val isStylus = p.type == PointerType.Stylus ||
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
                val scale = if (lastDistance > 0f && avgDist > 0f) {
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
