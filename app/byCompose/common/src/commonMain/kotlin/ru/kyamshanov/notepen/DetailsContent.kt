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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlin.math.roundToInt
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
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
import kotlinx.coroutines.launch
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
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import ru.kyamshanov.notepen.sync.domain.model.toDto

private val logger = KotlinLogging.logger {}

internal const val BACK_CONTENT_DESCRIPTION = "Назад"
private val SCROLL_BOTTOM_EXTRA = 240.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailsContent(
    component: DetailsComponent,
    loader: PdfDocumentLoader,
    renderer: PdfPageRenderer,
    syncEngine: SyncEngine? = null,
    modifier: Modifier = Modifier,
) {
    val localWindowInfo = LocalWindowInfo.current
    val windowSizeInPx = localWindowInfo.containerSize
    val model by component.model.subscribeAsState()
    val filePath = remember(model.title) { model.title }

    var pdfDocument by remember(filePath) { mutableStateOf<PdfDocument?>(null) }
    val pages by remember { derivedStateOf { pdfDocument?.info?.pages ?: emptyList() } }

    var scale by remember { mutableStateOf(100) }
    val pagesCache = remember(pdfDocument, scale) { LruCache<Int, ImageBitmap>(maxSize = 8) }

    var toolMode by remember { mutableStateOf(ToolMode.NONE) }
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
    val firstVisiblePage by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
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
            scale = bundle.scale
            penSettings = bundle.pen
            markerSettings = bundle.marker
            eraserSettings = bundle.eraser
            bundle.pages.forEach { (pageIndex, paths) ->
                drawingStates.getOrPut(pageIndex) { PdfDrawingState() }.currentPaths.addAll(paths)
            }
            if (pages.isNotEmpty() && (bundle.currentPage > 0 || bundle.currentPageOffset > 0)) {
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

        ScrollablePdfColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            onScale = { factor ->
                scale = (scale * factor).roundToInt().coerceIn(MIN_SCALE, MAX_SCALE)
            },
        ) {
            items(items = pages, key = { it.pageIndex }) { page ->
                val screenWidthPx = windowSizeInPx.width
                val maxTargetWidthPx = screenWidthPx * 4 / 5
                val targetWidthPx = ((screenWidthPx * 2 / 3) * (scale / 100f)).toInt()

                val aspectRatio = page.aspectRatio
                val targetHeightPx = (targetWidthPx / aspectRatio).toInt()
                val maxTargetHeightPx = (maxTargetWidthPx / aspectRatio).toInt()

                val width = with(LocalDensity.current) {
                    minOf(targetWidthPx, maxTargetWidthPx).toDp()
                }
                val height = with(LocalDensity.current) {
                    minOf(targetHeightPx, maxTargetHeightPx).toDp()
                }

                Box(modifier = Modifier.size(width, height)) {
                    val pageIndex = page.pageIndex
                    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(pdfDocument, scale) {
                        val doc = pdfDocument ?: return@LaunchedEffect
                        val bitmap = pagesCache[pageIndex] ?: renderer.renderPage(
                            document = doc,
                            pageIndex = pageIndex,
                            widthPx = targetWidthPx,
                            heightPx = targetHeightPx,
                        ).toImageBitmap().also { pagesCache[pageIndex] = it }
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
                            toolMode = toolMode,
                            penSettings = penSettings,
                            markerSettings = markerSettings,
                            eraserSettings = eraserSettings,
                            onGestureStart = { snapshot ->
                                globalUndoStack.addLast(pageIndex to snapshot)
                                globalRedoStack.clear()
                            },
                            onStrokeFinished = { path ->
                                val engine = syncEngine ?: return@DrawablePdfPage
                                val id = engine.newStrokeId()
                                val stamped = path.copy(strokeId = id)
                                val idx = pdfDrawingState.currentPaths.lastIndex
                                if (idx >= 0) pdfDrawingState.currentPaths[idx] = stamped
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
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(pageIndex)
                    }
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
                        val annotations = drawingStates.mapValues { (_, state) ->
                            state.currentPaths.toList()
                        }
                        val result = annotationRepository.save(
                            pdfPath = filePath,
                            annotations = annotations,
                            scale = scale,
                            pen = penSettings,
                            marker = markerSettings,
                            eraser = eraserSettings,
                            currentPage = lazyListState.firstVisibleItemIndex,
                            currentPageOffset = lazyListState.firstVisibleItemScrollOffset,
                        )
                        isSaving = false
                        val message = if (result.isSuccess) "Аннотации сохранены" else "Ошибка сохранения"
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
                scale = scale,
                onZoomIn = { scale = minOf(MAX_SCALE, scale + 10) },
                onZoomOut = { scale = maxOf(MIN_SCALE, scale - 10) },
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

        IconButton(
            onClick = {
                coroutineScope.launch {
                    val annotations = drawingStates.mapValues { (_, state) ->
                        state.currentPaths.toList()
                    }
                    annotationRepository.save(
                        pdfPath = filePath,
                        annotations = annotations,
                        scale = scale,
                        pen = penSettings,
                        marker = markerSettings,
                        eraser = eraserSettings,
                        currentPage = lazyListState.firstVisibleItemIndex,
                        currentPageOffset = lazyListState.firstVisibleItemScrollOffset,
                    ).onFailure { e ->
                        logger.warn { "Auto-save on back failed: ${e::class.simpleName}" }
                    }
                    component.saveLastPageIndex(lazyListState.firstVisibleItemIndex)
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
