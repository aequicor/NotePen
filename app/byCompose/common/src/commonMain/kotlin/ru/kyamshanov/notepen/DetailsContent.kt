package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository as SharedAnnotationRepository
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.pdf.presentation.toImageBitmap
import ru.kyamshanov.notepen.sync.SyncBridge
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.model.toDomain
import ru.kyamshanov.notepen.sync.domain.model.toDto
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import ru.kyamshanov.notepen.sync.domain.viewstate.ViewStateSync
import ru.kyamshanov.notepen.sync.infrastructure.WebSocketFileTransfer
import ru.kyamshanov.notepen.pdfviewer.PdfDesktopPagesViewer
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState
import ru.kyamshanov.notepen.pdfviewer.isJvmDesktop
import ru.kyamshanov.notepen.pdfviewer.rememberPdfViewerState
import ru.kyamshanov.notepen.tablet.LocalTabletInputController
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

internal const val BACK_CONTENT_DESCRIPTION = "Назад"
private val SCROLL_BOTTOM_EXTRA = 240.dp

/** Toolbar `+` button zoom factor (matches Ctrl+wheel zoom-in). */
private const val TOOLBAR_ZOOM_STEP_IN = 1.1f

/** Toolbar `−` button zoom factor (matches Ctrl+wheel zoom-out). */
private const val TOOLBAR_ZOOM_STEP_OUT = 1f / TOOLBAR_ZOOM_STEP_IN

/**
 * Maximum pixel count for a rendered page bitmap (ARGB → 4 bytes/pixel).
 * 4 MP → ~16 MB per page, safely under Android's per-bitmap draw ceiling
 * (~100 MB) even with several pages cached. The Box can still grow beyond
 * this; `Image.fillMaxSize` stretches a capped bitmap.
 */
private const val MAX_RENDER_PIXELS = 4_000_000

/** Result of [computeCursorAnchoredZoom]: the new graphicsLayer state. */
internal data class ZoomResult(val panOffset: Offset, val gestureScale: Float)

/**
 * Pure math for a cursor-anchored zoom step on the desktop / pinch pipeline.
 *
 * The PDF viewport renders through a single `graphicsLayer` on the LazyColumn:
 *     viewport = panOffset + lazyP * gestureScale       (origin = top-left)
 * where `lazyP` is a point in LazyColumn render-box coordinates (which already
 * incorporate `committedScale` — it drives item width and horizontal
 * centering via `horizontalAlignment = CenterHorizontally`).
 *
 * Inverse: `lazyP = (viewport − panOffset) / gestureScale`.
 *
 * Invariant: the LazyColumn pixel that was under [centroid] before the zoom
 * is under [centroid] after the zoom. From the forward formula:
 *     centroid = panOffset_new + lazyP * gestureScale_new
 *  ⇒ panOffset_new = centroid − lazyP * gestureScale_new
 *
 * [committedScale] does not appear: it is layout-side state and never enters
 * the graphicsLayer transform. Mixing it in (the previous implementation) is
 * what made the focal point drift whenever the document was rendered at a
 * non-100 % committed scale.
 *
 * [pan] is added on top of the cursor-anchored result for combined pinch+pan
 * gestures.
 */
internal fun computeCursorAnchoredZoom(
    centroid: Offset,
    pan: Offset,
    zoom: Float,
    committedScale: Int,
    gestureScale: Float,
    panOffset: Offset,
    minScale: Int = MIN_SCALE,
    maxScale: Int = MAX_SCALE,
): ZoomResult {
    val minGesture = minScale.toFloat() / committedScale
    val maxGesture = maxScale.toFloat() / committedScale
    val gOld = gestureScale
    val gNew = (gOld * zoom).coerceIn(minGesture, maxGesture)
    if (gNew == gOld || gOld <= 0f) {
        return ZoomResult(panOffset = panOffset + pan, gestureScale = gOld)
    }
    val lazyP = (centroid - panOffset) / gOld
    return ZoomResult(
        panOffset = centroid - lazyP * gNew + pan,
        gestureScale = gNew,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailsContent(
    component: DetailsComponent,
    loader: PdfDocumentLoader,
    renderer: PdfPageRenderer,
    syncEngine: SyncEngine? = null,
    peerServer: PeerServer? = null,
    peerClient: SyncClient? = null,
    modifier: Modifier = Modifier,
) {
    val localWindowInfo = LocalWindowInfo.current
    val windowSizeInPx = localWindowInfo.containerSize
    val model by component.model.subscribeAsState()
    val filePath = remember(model.title) { model.title }

    var pdfDocument by remember(filePath) { mutableStateOf<PdfDocument?>(null) }
    val pages by remember { derivedStateOf { pdfDocument?.info?.pages ?: emptyList() } }

    // Split scale into a committed value (drives bitmap render resolution and
    // is what gets persisted / synced) and a gesture multiplier that's applied
    // visually via `graphicsLayer` on the LazyColumn. During a pinch only
    // `gestureScale` updates → bitmap and strokes scale through the same
    // matrix every frame, eliminating the "strokes jump ahead of bitmap"
    // effect. After a short debounce we bake `gestureScale` into
    // `committedScale` and trigger a single re-render at the new resolution.
    var committedScale by remember { mutableStateOf(100) }
    var gestureScale by remember { mutableStateOf(1f) }
    // Viewport-pixel translation applied via the LazyColumn's `graphicsLayer`.
    // X is persistent (LazyColumn has no native horizontal scroll); Y is kept
    // at 0 — vertical pan is delegated to `lazyListState.scrollBy` so
    // virtualisation continues to work.
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    // Cache survives `committedScale` changes: on bake we re-render at a
    // higher resolution, but the old bitmap is kept as a fallback so newly
    // composed page slots (those scrolled into view by the bake) start with
    // *something* instead of "Loading", which is what produces the visible
    // jump when zooming towards a neighbour page.
    val pagesCache = remember(pdfDocument) { LruCache<Int, ImageBitmap>(maxSize = 8) }

    var toolMode by remember { mutableStateOf(ToolMode.NONE) }
    val tabletController = LocalTabletInputController.current
    val barrelPressed by tabletController.barrelPressed.collectAsState()
    val eraserTipActive by tabletController.eraserTipActive.collectAsState()
    // Hold-to-erase: while the pen's barrel button is held *or* the eraser tip
    // is touching the screen (e.g. flipped S-Pen), override the active tool
    // with ERASER. Releasing either returns to the user-selected tool. Because
    // `toolMode` is a key of `pointerInput` in DrawablePdfPage, the gesture
    // handler restarts on the override flip — any in-flight stroke is finalised
    // cleanly via the existing `LaunchedEffect(toolMode)` path.
    val eraserOverride = barrelPressed || eraserTipActive
    // Do NOT remap `toolMode` to ERASER on override — that flip restarts the
    // `pointerInput` block mid-gesture, and the new ERASER block's
    // `awaitFirstDown` misses the still-held stylus DOWN, so the stylus
    // eraser tip / barrel button silently does nothing until the user lifts
    // and re-presses. Instead, `DrawablePdfPage` reads `eraserOverride`
    // dynamically at gesture start (see its `eraserOverride` parameter) and
    // routes the gesture to the erase pipeline without restarting.
    val effectiveToolMode = toolMode
    var penSettings by remember { mutableStateOf(PenSettings()) }
    var markerSettings by remember { mutableStateOf(MarkerSettings()) }
    var eraserSettings by remember { mutableStateOf(EraserSettings()) }
    var showThumbnails by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val drawingStates = remember { mutableStateMapOf<Int, PdfDrawingState>() }
    val hasAnnotations by remember {
        derivedStateOf { drawingStates.values.any { it.currentPaths.isNotEmpty() } }
    }
    val lazyListState = rememberLazyListState()
    // Desktop путь: единственный источник правды по позиции + зуму. На
    // Android этот state существует, но не подключён — Android-ветка
    // продолжает использовать `lazyListState` + `committedScale` /
    // `gestureScale` / `panOffset` напрямую.
    val pdfViewerState: PdfViewerState = rememberPdfViewerState()
    val firstVisiblePage by remember {
        derivedStateOf {
            if (isJvmDesktop) pdfViewerState.firstVisiblePageIndex
            else lazyListState.firstVisibleItemIndex
        }
    }
    // Унифицированные читалки текущей позиции / масштаба — sync-код,
    // сохранение и индикаторы работают через них и не должны знать о
    // платформе.
    val currentScalePercent: Int by remember {
        derivedStateOf {
            if (isJvmDesktop) pdfViewerState.scalePercent else committedScale
        }
    }
    val currentPageOffsetPx: Int by remember {
        derivedStateOf {
            if (isJvmDesktop) pdfViewerState.firstVisiblePageOffsetPx
            else lazyListState.firstVisibleItemScrollOffset
        }
    }
    suspend fun scrollToPageUnified(pageIndex: Int, offsetPx: Int) {
        if (isJvmDesktop) pdfViewerState.scrollToPage(pageIndex, offsetPx)
        else lazyListState.scrollToItem(pageIndex, offsetPx)
    }
    fun setScalePercentUnified(scale: Int) {
        if (isJvmDesktop) pdfViewerState.setScalePercent(scale)
        else committedScale = scale
    }
    val globalUndoStack = remember { ArrayDeque<Pair<Int, List<DrawingPath>>>() }
    val globalRedoStack = remember { ArrayDeque<Pair<Int, List<DrawingPath>>>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val annotationRepository = remember { createAnnotationRepository() }
    val pdfExporter = remember { createPdfExporter() }
    val focusRequester = remember { FocusRequester() }
    var shiftHeld by remember { mutableStateOf(false) }

    val syncBridge = remember(syncEngine) {
        syncEngine?.let { engine ->
            SyncBridge(engine = engine, drawingStates = drawingStates, scope = coroutineScope)
        }
    }

    LaunchedEffect(syncBridge) { syncBridge?.start() }

    // Symmetric viewport sync: page + zoom + in-page offset travel both ways.
    // Bound to whichever peer endpoint is present (host has server, tablet has
    // client; never both on one device in practice).
    val viewStateSync = remember(peerServer, peerClient, coroutineScope) {
        when {
            peerServer != null -> ViewStateSync(
                incoming = peerServer.incomingMessages,
                send = peerServer::send,
                scope = coroutineScope,
            )
            peerClient != null -> ViewStateSync(
                incoming = peerClient.incomingMessages,
                send = peerClient::send,
                scope = coroutineScope,
            )
            else -> null
        }
    }

    // Last viewport snapshot we *applied* from the remote — used by the
    // publisher to suppress the immediate echo back to the peer.
    var lastAppliedRemote by remember(viewStateSync) {
        mutableStateOf<Triple<Int, Int, Int>?>(null)
    }

    // Publisher: react to local scale / page / in-page offset changes.
    LaunchedEffect(viewStateSync, pages.size) {
        val bus = viewStateSync ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            Triple(firstVisiblePage, currentScalePercent, currentPageOffsetPx)
        }.collect { (page, sc, offset) ->
            val snapshot = Triple(page, sc, offset)
            if (snapshot == lastAppliedRemote) return@collect
            bus.publish(page = page, scale = sc, pageScrollOffsetPx = offset)
        }
    }

    // Consumer: apply remote viewport changes to local scroll / zoom.
    LaunchedEffect(viewStateSync, pages.size) {
        val bus = viewStateSync ?: return@LaunchedEffect
        bus.remoteState.collect { remote ->
            if (remote == null) return@collect
            val targetPage = if (pages.isEmpty()) remote.page
            else remote.page.coerceIn(0, pages.size - 1)
            lastAppliedRemote = Triple(targetPage, remote.scale, remote.pageScrollOffsetPx)
            if (currentScalePercent != remote.scale) setScalePercentUnified(remote.scale)
            if (firstVisiblePage != targetPage ||
                currentPageOffsetPx != remote.pageScrollOffsetPx
            ) {
                scrollToPageUnified(targetPage, remote.pageScrollOffsetPx)
            }
        }
    }

    // Host side: stream the open PDF to the tablet as soon as a peer pairs,
    // then push the current viewport so the tablet opens at the same page/zoom.
    LaunchedEffect(peerServer, filePath, viewStateSync) {
        val server = peerServer ?: return@LaunchedEffect
        server.state.collect { st ->
            if (st is PairingState.Connected) {
                val transferId = "tx-${Random.nextLong().toString(16)}"
                runCatching {
                    WebSocketFileTransfer(server = server)
                        .send(sourcePath = filePath, transferId = transferId)
                        .collect { /* progress ignored on host */ }
                }.onFailure { e ->
                    logger.warn { "Failed to stream PDF to peer: ${e::class.simpleName}" }
                }
                viewStateSync?.publish(
                    page = firstVisiblePage,
                    scale = currentScalePercent,
                    pageScrollOffsetPx = currentPageOffsetPx,
                )
            }
        }
    }

    // Host side: react to a SaveRequest from the tablet and persist locally.
    val performHostSave: suspend () -> Result<Unit> = {
        val annotations = drawingStates.mapValues { (_, state) -> state.currentPaths.toList() }
        annotationRepository.save(
            pdfPath = filePath,
            annotations = annotations,
            scale = currentScalePercent,
            pen = penSettings,
            marker = markerSettings,
            eraser = eraserSettings,
            currentPage = firstVisiblePage,
            currentPageOffset = currentPageOffsetPx,
        )
    }

    LaunchedEffect(peerServer) {
        val server = peerServer ?: return@LaunchedEffect
        server.incomingMessages.filterIsInstance<NetworkMessage.SaveRequest>().collect { req ->
            val result = performHostSave()
            server.send(
                NetworkMessage.SaveResult(
                    requestId = req.requestId,
                    success = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.message,
                ),
            )
        }
    }

    // Host side: on tablet's snapshot request, send all current strokes.
    // Backfills missing strokeIds in-place so future erase/sync operations
    // can target the same stroke by id.
    LaunchedEffect(peerServer, syncEngine) {
        val server = peerServer ?: return@LaunchedEffect
        server.incomingMessages
            .filterIsInstance<NetworkMessage.AnnotationSnapshotRequest>()
            .collect {
                val deviceId = syncEngine?.deviceId ?: "host"
                val collected = mutableListOf<StrokeDelta.Added>()
                drawingStates.forEach { (pageIndex, state) ->
                    val paths = state.currentPaths
                    for (i in paths.indices) {
                        val original = paths[i]
                        val id = original.strokeId.ifEmpty {
                            syncEngine?.newStrokeId() ?: "$deviceId#legacy-$pageIndex-$i"
                        }
                        if (original.strokeId.isEmpty()) {
                            paths[i] = original.copy(strokeId = id)
                        }
                        collected.add(
                            StrokeDelta.Added(
                                strokeId = id,
                                pageIndex = pageIndex,
                                authorDeviceId = deviceId,
                                clock = 0,
                                path = paths[i].toDto(id),
                            ),
                        )
                    }
                }
                logger.info { "Sending annotation snapshot: ${collected.size} strokes" }
                server.send(NetworkMessage.AnnotationSnapshot(strokes = collected))
            }
    }

    // Tablet side: after the local bundle load, ask the host for its snapshot
    // and merge it into drawingStates (dedup by strokeId).
    LaunchedEffect(peerClient, filePath) {
        val client = peerClient ?: return@LaunchedEffect
        // Give the LaunchedEffect(filePath) annotation-load a chance to populate
        // drawingStates before requesting — the dedup later filters duplicates,
        // so order is not strictly required, but this keeps logs cleaner.
        runCatching { client.send(NetworkMessage.AnnotationSnapshotRequest) }
            .onFailure { e ->
                logger.warn { "Failed to request annotation snapshot: ${e::class.simpleName}" }
            }
        client.incomingMessages
            .filterIsInstance<NetworkMessage.AnnotationSnapshot>()
            .collect { snapshot ->
                logger.info { "Received annotation snapshot: ${snapshot.strokes.size} strokes" }
                snapshot.strokes.forEach { added ->
                    val state = drawingStates.getOrPut(added.pageIndex) { PdfDrawingState() }
                    if (state.currentPaths.none { it.strokeId == added.strokeId }) {
                        state.currentPaths.add(added.path.toDomain())
                        state.markHistoryChanged()
                    }
                }
            }
    }

    // Tablet side: surface connection loss.
    var showLostConnectionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(peerClient) {
        val client = peerClient ?: return@LaunchedEffect
        client.state.collect { st ->
            showLostConnectionDialog = st is PairingState.LostConnection
        }
    }

    LaunchedEffect(filePath) {
        pdfDocument?.close()
        pdfDocument = try {
            loader.load(filePath)
        } catch (e: Exception) {
            logger.warn { "Failed to open PDF: ${e::class.simpleName}" }
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose { pdfDocument?.close() }
    }

    LaunchedEffect(filePath) {
        annotationRepository.load(filePath).getOrNull()?.let { bundle ->
            if (isJvmDesktop) {
                pdfViewerState.applyInitialState(
                    scalePercent = bundle.scale,
                    pageIndex = bundle.currentPage,
                    pageOffsetPx = bundle.currentPageOffset,
                )
            } else {
                committedScale = bundle.scale
            }
            penSettings = bundle.pen
            markerSettings = bundle.marker
            eraserSettings = bundle.eraser
            bundle.pages.forEach { (pageIndex, paths) ->
                val state = drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
                state.currentPaths.addAll(paths)
                state.markHistoryChanged()
            }
            if (!isJvmDesktop && pages.isNotEmpty() &&
                (bundle.currentPage > 0 || bundle.currentPageOffset > 0)
            ) {
                lazyListState.scrollToItem(
                    index = bundle.currentPage.coerceIn(0, pages.size - 1),
                    scrollOffset = bundle.currentPageOffset,
                )
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusTarget()
            .onKeyEvent { e ->
                val isShift = e.key == Key.ShiftLeft || e.key == Key.ShiftRight
                if (isShift) {
                    shiftHeld = e.type == KeyEventType.KeyDown
                    false
                } else if (e.type == KeyEventType.KeyDown && e.key == Key.Z && e.isCtrlPressed && !shiftHeld) {
                    if (globalUndoStack.isNotEmpty()) {
                        val (pageIndex, snapshot) = globalUndoStack.removeLast()
                        val current = drawingStates[pageIndex]?.currentPaths?.toList() ?: emptyList()
                        globalRedoStack.addLast(pageIndex to current)
                        drawingStates[pageIndex]?.restoreSnapshot(snapshot)
                    }
                    true
                } else if (e.type == KeyEventType.KeyDown && e.key == Key.Z && e.isCtrlPressed && shiftHeld) {
                    if (globalRedoStack.isNotEmpty()) {
                        val (pageIndex, snapshot) = globalRedoStack.removeLast()
                        val current = drawingStates[pageIndex]?.currentPaths?.toList() ?: emptyList()
                        globalUndoStack.addLast(pageIndex to snapshot)
                        drawingStates[pageIndex]?.restoreSnapshot(snapshot)
                    }
                    true
                } else false
            },
    ) {

        // Cursor-anchored zoom для Android-ветки (ScrollablePdfColumn).
        // Desktop использует [PdfDesktopPagesViewer], где zoom-якорь
        // математически встроен в [PdfViewerState.zoomTo].
        val applyTransform: (Offset, Offset, Float) -> Unit = { centroid, pan, zoom ->
            val result = computeCursorAnchoredZoom(
                centroid = centroid,
                pan = pan,
                zoom = zoom,
                committedScale = committedScale,
                gestureScale = gestureScale,
                panOffset = panOffset,
            )
            panOffset = result.panOffset
            gestureScale = result.gestureScale
        }

        // Bake gestureScale → committedScale (только Android-путь). Desktop
        // не использует двухуровневую модель — там один `zoom`.
        LaunchedEffect(gestureScale) {
            if (isJvmDesktop) return@LaunchedEffect
            if (gestureScale == 1f) return@LaunchedEffect
            delay(150)
            val cOld = committedScale
            val gOld = gestureScale
            val cNew = (cOld * gOld).roundToInt().coerceIn(MIN_SCALE, MAX_SCALE)
            // Skip the bake once a page at the new scale would exceed
            // viewport width. `Modifier.size` on the page Box gets capped to
            // LazyColumn's max width — the item then stops growing while
            // `committedScale` (notionally) still grew, which breaks the
            // `panAdjustX` derivation below (it assumes item width grew by
            // exactly `gOld`) and causes the focal point to jump. Leaving
            // `gestureScale` un-baked keeps `graphicsLayer` doing the visual
            // zoom uniformly, and the gesture-time cursor anchor stays
            // exact. The cost is bitmap-stretch blur at extreme zoom — an
            // acceptable trade for not losing the focal point.
            val viewportWidth = windowSizeInPx.width.toFloat()
            val pageBaseWidth = viewportWidth * 2f / 3f
            val itemWidthAtNew = pageBaseWidth * cNew / 100f
            if (itemWidthAtNew > viewportWidth) return@LaunchedEffect
            // X: items re-centre at V/2 in layout when committedScale grows;
            // graphicsLayer scale drops back to 1. Compensate so the
            // centreline stays where it was visually.
            val panAdjustX = viewportWidth / 2f * (gOld - 1f)
            // Y: native LazyColumn scroll. firstVisibleItem's scrollOffset is
            // a raw-pixel value that does NOT auto-scale when items grow, so
            // we have to scrollBy `soOld * (gOld - 1)` to keep the same
            // fraction-within-item. Then add `-panOffset.y` to bake the
            // graphicsLayer Y-translation (the centroid-tracking component
            // from the gesture) into native scroll.
            val soOld = lazyListState.firstVisibleItemScrollOffset.toFloat()
            val scrollDeltaY = soOld * (gOld - 1f) - panOffset.y

            committedScale = cNew
            gestureScale = 1f
            panOffset = Offset(panOffset.x + panAdjustX, 0f)
            if (scrollDeltaY != 0f) {
                runCatching { lazyListState.scrollBy(scrollDeltaY) }
            }
        }

        if (isJvmDesktop) {
            PdfDesktopPagesViewer(
                state = pdfViewerState,
                pdfDocument = pdfDocument,
                pages = pages,
                renderer = renderer,
                modifier = Modifier.fillMaxSize(),
            ) {
                val bm = bitmap
                Box(modifier = Modifier.size(visualWidth, visualHeight)) {
                    if (bm != null) {
                        val pdfDrawingState = remember(pageIndex) {
                            drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
                        }
                        DrawablePdfPage(
                            bitmap = bm,
                            pdfDrawingState = pdfDrawingState,
                            toolMode = effectiveToolMode,
                            penSettings = penSettings,
                            markerSettings = markerSettings,
                            eraserSettings = eraserSettings,
                            eraserOverride = { eraserOverride },
                            onGestureStart = { snapshot ->
                                globalUndoStack.addLast(pageIndex to snapshot)
                                globalRedoStack.clear()
                            },
                            onEraseFinished = { before, _ ->
                                handleEraseFinished(
                                    pdfDrawingState = pdfDrawingState,
                                    pageIndex = pageIndex,
                                    before = before,
                                    engine = syncEngine,
                                )
                            },
                            onStrokeFinished = { path ->
                                handleStrokeFinished(
                                    pdfDrawingState = pdfDrawingState,
                                    pageIndex = pageIndex,
                                    path = path,
                                    engine = syncEngine,
                                )
                            },
                            modifier = Modifier.size(visualWidth, visualHeight),
                        )
                    } else {
                        Text("Loading")
                    }
                }
            }
        } else ScrollablePdfColumn(
            state = lazyListState,
            gestureScale = gestureScale,
            panOffset = panOffset,
            modifier = Modifier.fillMaxSize(),
            onTransform = applyTransform,
        ) {
            items(items = pages, key = { it.pageIndex }) { page ->
                val screenWidthPx = windowSizeInPx.width
                // Box size scales with the full committed scale — pages can
                // (and should) exceed the viewport at high zoom; horizontal
                // pan brings off-screen detail back into view.
                val targetWidthPx = ((screenWidthPx * 2 / 3) * (committedScale / 100f)).toInt()
                val aspectRatio = page.aspectRatio
                val targetHeightPx = (targetWidthPx / aspectRatio).toInt()

                // Render the bitmap at a capped pixel count and let
                // `Image.fillMaxSize` stretch it. Pixel-count cap is
                // aspect-ratio-aware: a tall narrow page gets a similarly
                // bounded buffer to a wide short one, so even at 4–8× zoom we
                // never trip Android's per-bitmap draw ceiling (~100 MB).
                val targetPixels = targetWidthPx.toLong() * targetHeightPx.toLong()
                val downscale = if (targetPixels > MAX_RENDER_PIXELS) {
                    kotlin.math.sqrt(MAX_RENDER_PIXELS.toFloat() / targetPixels.toFloat())
                } else 1f
                val renderWidthPx = (targetWidthPx * downscale).toInt().coerceAtLeast(1)
                val renderHeightPx = (targetHeightPx * downscale).toInt().coerceAtLeast(1)

                val width = with(LocalDensity.current) { targetWidthPx.toDp() }
                val height = with(LocalDensity.current) { targetHeightPx.toDp() }

                Box(modifier = Modifier.size(width, height)) {
                    val pageIndex = page.pageIndex
                    // Seed from the persistent cache so a slot composed for
                    // the first time (e.g. scrolled into view at bake) shows
                    // the previously-rendered bitmap immediately, before the
                    // LaunchedEffect's re-render at the new resolution
                    // completes.
                    var imageBitmap by remember {
                        mutableStateOf<ImageBitmap?>(pagesCache[pageIndex])
                    }
                    LaunchedEffect(pdfDocument, committedScale) {
                        val doc = pdfDocument ?: return@LaunchedEffect
                        val cached = pagesCache[pageIndex]
                        // Keep the old bitmap on screen while the new one
                        // renders — `Image.fillMaxSize` stretches it to the
                        // new Box size; visually identical to the gestureScale
                        // path, no flash.
                        if (cached != null) imageBitmap = cached
                        val matchesTarget = cached != null &&
                            cached.width == renderWidthPx &&
                            cached.height == renderHeightPx
                        if (matchesTarget) return@LaunchedEffect
                        val bitmap = renderer.renderPage(
                            document = doc,
                            pageIndex = pageIndex,
                            widthPx = renderWidthPx,
                            heightPx = renderHeightPx,
                        ).toImageBitmap()
                        pagesCache[pageIndex] = bitmap
                        imageBitmap = bitmap
                    }

                    val bm = imageBitmap
                    if (bm != null) {
                        val pdfDrawingState = remember(pageIndex) {
                            drawingStates.getOrPut(pageIndex) { PdfDrawingState() }
                        }
                        DrawablePdfPage(
                            bitmap = bm,
                            pdfDrawingState = pdfDrawingState,
                            toolMode = effectiveToolMode,
                            penSettings = penSettings,
                            markerSettings = markerSettings,
                            eraserSettings = eraserSettings,
                            eraserOverride = { eraserOverride },
                            onGestureStart = { snapshot ->
                                globalUndoStack.addLast(pageIndex to snapshot)
                                globalRedoStack.clear()
                            },
                            onEraseFinished = { before, _ ->
                                val engine = syncEngine ?: return@DrawablePdfPage
                                val beforeIds = before
                                    .mapNotNull { it.strokeId.ifEmpty { null } }
                                    .toSet()
                                val beforeById = before.associateBy { it.strokeId }
                                val intactIds = mutableSetOf<String>()
                                for (p in pdfDrawingState.currentPaths) {
                                    val orig = beforeById[p.strokeId] ?: continue
                                    if (orig.points == p.points && p.strokeId.isNotEmpty()) {
                                        intactIds.add(p.strokeId)
                                    }
                                }
                                val removedOrModified = beforeIds - intactIds
                                if (removedOrModified.isEmpty()) return@DrawablePdfPage
                                val newAdded = mutableListOf<DrawingPath>()
                                for (i in pdfDrawingState.currentPaths.indices) {
                                    val p = pdfDrawingState.currentPaths[i]
                                    val needsId = p.strokeId.isEmpty() ||
                                        p.strokeId in removedOrModified
                                    if (needsId) {
                                        val newId = engine.newStrokeId()
                                        val stamped = p.copy(strokeId = newId)
                                        pdfDrawingState.currentPaths[i] = stamped
                                        newAdded.add(stamped)
                                    }
                                }
                                // Single ordered burst — see SyncEngine.applyLocalBatch KDoc
                                // for why this matters with many deltas at once.
                                val batch = buildList<StrokeDelta> {
                                    for (id in removedOrModified) add(
                                        StrokeDelta.Removed(
                                            strokeId = id,
                                            pageIndex = pageIndex,
                                            authorDeviceId = engine.deviceId,
                                            clock = 0,
                                        ),
                                    )
                                    for (p in newAdded) add(
                                        StrokeDelta.Added(
                                            strokeId = p.strokeId,
                                            pageIndex = pageIndex,
                                            authorDeviceId = engine.deviceId,
                                            clock = 0,
                                            path = p.toDto(p.strokeId),
                                        ),
                                    )
                                }
                                engine.applyLocalBatch(batch)
                            },
                            onStrokeFinished = { path ->
                                val engine = syncEngine ?: return@DrawablePdfPage
                                val id = engine.newStrokeId()
                                val stamped = path.copy(strokeId = id)
                                val idx = pdfDrawingState.currentPaths.lastIndex
                                if (idx >= 0) {
                                    pdfDrawingState.currentPaths[idx] = stamped
                                    pdfDrawingState.markHistoryChanged()
                                }
                                engine.applyLocal(
                                    StrokeDelta.Added(
                                        strokeId = id,
                                        pageIndex = pageIndex,
                                        authorDeviceId = engine.deviceId,
                                        clock = 0,
                                        path = stamped.toDto(id),
                                    ),
                                )
                            },
                            modifier = Modifier.size(width, height),
                        )
                    } else {
                        Text("Loading")
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(SCROLL_BOTTOM_EXTRA))
            }
        }

        AnimatedVisibility(
            visible = showThumbnails && pages.isNotEmpty(),
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            PageThumbnailsSidebar(
                pages = pages,
                pdfDocument = pdfDocument,
                renderer = renderer,
                currentPage = firstVisiblePage,
                onPageClick = { pageIndex ->
                    coroutineScope.launch { scrollToPageUnified(pageIndex, 0) }
                },
            )
        }

        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            PdfFloatingToolbar(
                toolMode = toolMode,
                onToolModeChange = { toolMode = it },
                hasAnnotations = hasAnnotations,
                isSaving = isSaving,
                isExporting = isExporting,
                showThumbnails = showThumbnails,
                onToggleThumbnails = { showThumbnails = !showThumbnails },
                onSave = {
                    isSaving = true
                    coroutineScope.launch {
                        val message = if (peerClient != null) {
                            val requestId = "save-${Random.nextLong().toString(16)}"
                            peerClient.send(NetworkMessage.SaveRequest(requestId))
                            val reply = withTimeoutOrNull(5.seconds) {
                                peerClient.incomingMessages
                                    .filterIsInstance<NetworkMessage.SaveResult>()
                                    .filter { it.requestId == requestId }
                                    .first()
                            }
                            when {
                                reply == null -> "Нет ответа от ПК"
                                reply.success -> "Сохранено на ПК"
                                else -> "Ошибка на ПК: ${reply.errorMessage.orEmpty()}"
                            }
                        } else {
                            val annotations = drawingStates.mapValues { (_, state) ->
                                state.currentPaths.toList()
                            }
                            val result = annotationRepository.save(
                                pdfPath = filePath,
                                annotations = annotations,
                                scale = currentScalePercent,
                                pen = penSettings,
                                marker = markerSettings,
                                eraser = eraserSettings,
                                currentPage = firstVisiblePage,
                                currentPageOffset = currentPageOffsetPx,
                            )
                            if (result.isSuccess) "Аннотации сохранены" else "Ошибка сохранения"
                        }
                        isSaving = false
                        snackbarHostState.showSnackbar(message)
                    }
                },
                onExport = {
                    isExporting = true
                    coroutineScope.launch {
                        val annotations = drawingStates.mapValues { (_, state) ->
                            state.currentPaths.toList()
                        }
                        val outputPath = filePath.removeSuffix(".pdf") + "_annotated.pdf"
                        val result = pdfExporter.export(
                            sourcePdfPath = filePath,
                            annotations = annotations,
                            outputPath = outputPath,
                        )
                        isExporting = false
                        val message = if (result.isSuccess) {
                            "Экспорт завершён: $outputPath"
                        } else {
                            "Ошибка экспорта"
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                },
                scale = currentScalePercent,
                onZoomIn = {
                    val centre = Offset(
                        windowSizeInPx.width / 2f,
                        windowSizeInPx.height / 2f,
                    )
                    if (isJvmDesktop) pdfViewerState.zoomBy(TOOLBAR_ZOOM_STEP_IN, centre)
                    else applyTransform(centre, Offset.Zero, TOOLBAR_ZOOM_STEP_IN)
                },
                onZoomOut = {
                    val centre = Offset(
                        windowSizeInPx.width / 2f,
                        windowSizeInPx.height / 2f,
                    )
                    if (isJvmDesktop) pdfViewerState.zoomBy(TOOLBAR_ZOOM_STEP_OUT, centre)
                    else applyTransform(centre, Offset.Zero, TOOLBAR_ZOOM_STEP_OUT)
                },
            )
        }

        val panelMaxWidth = with(LocalDensity.current) { windowSizeInPx.width.toDp() - 32.dp }
        ToolSettingsFloatingPanel(
            toolMode = toolMode,
            penSettings = penSettings,
            onPenSettingsChange = { penSettings = it },
            markerSettings = markerSettings,
            onMarkerSettingsChange = { markerSettings = it },
            eraserSettings = eraserSettings,
            onEraserSettingsChange = { eraserSettings = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = panelMaxWidth),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        AnimatedVisibility(
            visible = pages.isNotEmpty(),
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        ) {
            PageIndicatorAirbar(
                currentPage = firstVisiblePage + 1,
                totalPages = pages.size,
            )
        }

        if (showLostConnectionDialog) {
            AlertDialog(
                onDismissRequest = { showLostConnectionDialog = false },
                title = { Text("Соединение потеряно") },
                text = { Text("Не удалось переподключиться к ПК за 10 секунд. Сохранить аннотации локально?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLostConnectionDialog = false
                        coroutineScope.launch {
                            val annotations = drawingStates.mapValues { (_, state) ->
                                state.currentPaths.toList()
                            }
                            val result = annotationRepository.save(
                                pdfPath = filePath,
                                annotations = annotations,
                                scale = currentScalePercent,
                                pen = penSettings,
                                marker = markerSettings,
                                eraser = eraserSettings,
                                currentPage = firstVisiblePage,
                                currentPageOffset = currentPageOffsetPx,
                            )
                            val msg = if (result.isSuccess) "Сохранено локально" else "Ошибка локального сохранения"
                            snackbarHostState.showSnackbar(msg)
                        }
                    }) { Text("Сохранить локально") }
                },
                dismissButton = {
                    TextButton(onClick = { showLostConnectionDialog = false }) { Text("Отмена") }
                },
            )
        }

        IconButton(
            onClick = {
                coroutineScope.launch {
                    val annotations = drawingStates.mapValues { (_, state) ->
                        state.currentPaths.toList()
                    }
                    annotationRepository.save(
                        pdfPath = filePath,
                        annotations = annotations,
                        scale = currentScalePercent,
                        pen = penSettings,
                        marker = markerSettings,
                        eraser = eraserSettings,
                        currentPage = firstVisiblePage,
                        currentPageOffset = currentPageOffsetPx,
                    ).onFailure { e ->
                        logger.warn { "Auto-save on back failed: ${e::class.simpleName}" }
                    }
                    component.saveLastPageIndex(firstVisiblePage)
                    component.onBack()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = BACK_CONTENT_DESCRIPTION,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Общая логика sync-уведомления после удаления / частичного стирания
 * штрихов. Используется как desktop, так и Android-ветками viewer'а.
 */
private fun handleEraseFinished(
    pdfDrawingState: PdfDrawingState,
    pageIndex: Int,
    before: List<DrawingPath>,
    engine: SyncEngine?,
) {
    if (engine == null) return
    val beforeIds = before.mapNotNull { it.strokeId.ifEmpty { null } }.toSet()
    val beforeById = before.associateBy { it.strokeId }
    val intactIds = mutableSetOf<String>()
    for (p in pdfDrawingState.currentPaths) {
        val orig = beforeById[p.strokeId] ?: continue
        if (orig.points == p.points && p.strokeId.isNotEmpty()) {
            intactIds.add(p.strokeId)
        }
    }
    val removedOrModified = beforeIds - intactIds
    if (removedOrModified.isEmpty()) return
    val newAdded = mutableListOf<DrawingPath>()
    for (i in pdfDrawingState.currentPaths.indices) {
        val p = pdfDrawingState.currentPaths[i]
        val needsId = p.strokeId.isEmpty() || p.strokeId in removedOrModified
        if (needsId) {
            val newId = engine.newStrokeId()
            val stamped = p.copy(strokeId = newId)
            pdfDrawingState.currentPaths[i] = stamped
            newAdded.add(stamped)
        }
    }
    val batch = buildList<StrokeDelta> {
        for (id in removedOrModified) add(
            StrokeDelta.Removed(
                strokeId = id,
                pageIndex = pageIndex,
                authorDeviceId = engine.deviceId,
                clock = 0,
            ),
        )
        for (p in newAdded) add(
            StrokeDelta.Added(
                strokeId = p.strokeId,
                pageIndex = pageIndex,
                authorDeviceId = engine.deviceId,
                clock = 0,
                path = p.toDto(p.strokeId),
            ),
        )
    }
    engine.applyLocalBatch(batch)
}

/**
 * Общая логика проштамповки strokeId и публикации нового штриха в sync.
 */
private fun handleStrokeFinished(
    pdfDrawingState: PdfDrawingState,
    pageIndex: Int,
    path: DrawingPath,
    engine: SyncEngine?,
) {
    if (engine == null) return
    val id = engine.newStrokeId()
    val stamped = path.copy(strokeId = id)
    val idx = pdfDrawingState.currentPaths.lastIndex
    if (idx >= 0) {
        pdfDrawingState.currentPaths[idx] = stamped
        pdfDrawingState.markHistoryChanged()
    }
    engine.applyLocal(
        StrokeDelta.Added(
            strokeId = id,
            pageIndex = pageIndex,
            authorDeviceId = engine.deviceId,
            clock = 0,
            path = stamped.toDto(id),
        ),
    )
}
