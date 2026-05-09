package ru.kyamshanov.notepen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Описание кнопки «Назад» для экранных чтецов (accessibility — AC-4). */
internal const val BACK_CONTENT_DESCRIPTION = "Назад"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailsContent(component: DetailsComponent, modifier: Modifier = Modifier) {
    val localWindowInfo = LocalWindowInfo.current
    val windowSizeInPx = rememberSaveable { localWindowInfo.containerSize }
    val model by component.model.subscribeAsState()
    val filePath = remember(model.title) { model.title }
    val pdfManager = remember(filePath) { PdfManager(filePath) }
    val metadata = remember(pdfManager.metadata) { pdfManager.metadata }
    val pages = remember(metadata.pages) { metadata.pages }
    var scale by remember { mutableStateOf(100) }
    val pagesCache = remember(pages, scale) { mutableMapOf<Int, ImageBitmap>() }

    var isDrawingEnabled by remember { mutableStateOf(false) }
    // Временный wiring до Шага 7: toolMode выводится из isDrawingEnabled (PEN при true,
    // NONE при false). penSettings/eraserSettings — дефолтные. Полноценный wiring
    // (отдельный toolMode, persistence pen/eraser) добавляется в Шагах 6–7.
    val toolMode = if (isDrawingEnabled) ToolMode.PEN else ToolMode.NONE
    val penSettings = remember { PenSettings() }
    val eraserSettings = remember { EraserSettings() }
    var isSaving by remember { mutableStateOf(false) }
    val drawingStates = remember { mutableStateMapOf<Int, PdfDrawingState>() }
    val hasAnnotations by remember {
        derivedStateOf { drawingStates.values.any { it.currentPaths.isNotEmpty() } }
    }
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val annotationRepository = remember { createAnnotationRepository() }

    DisposableEffect(pdfManager) {
        onDispose {
            pdfManager.close()
        }
    }

    LaunchedEffect(filePath) {
        annotationRepository.load(filePath).getOrNull()?.let { bundle ->
            scale = bundle.scale
            bundle.pages.forEach { (pageIndex, paths) ->
                drawingStates.getOrPut(pageIndex) { PdfDrawingState() }.currentPaths.addAll(paths)
            }
        }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        ScrollablePdfColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
            items(items = pages, { it.pageNumber }) { page ->
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
                    val pageIndex = page.pageNumber
                    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(scale) {
                        val newBitmap = withContext(Dispatchers.IO) {
                            pagesCache[pageIndex] ?: pdfManager.renderPage(
                                pageIndex,
                                IntSize(targetWidthPx, targetHeightPx)
                            )?.also { pagesCache[pageIndex] = it }
                        }
                        if (newBitmap != null) imageBitmap = newBitmap
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
                            eraserSettings = eraserSettings,
                            modifier = Modifier.size(width, height),
                        )
                    } else {
                        Text("Loading")
                    }
                }
            }
        }

        PdfFloatingToolbar(
            isDrawingEnabled = isDrawingEnabled,
            onToggleDrawing = { isDrawingEnabled = !isDrawingEnabled },
            hasAnnotations = hasAnnotations,
            isSaving = isSaving,
            onSave = {
                isSaving = true
                coroutineScope.launch {
                    val annotations = drawingStates.mapValues { (_, state) ->
                        state.currentPaths.toList()
                    }
                    val result = annotationRepository.save(filePath, annotations, scale)
                    isSaving = false
                    val message = if (result.isSuccess) "Аннотации сохранены" else "Ошибка сохранения"
                    snackbarHostState.showSnackbar(message)
                }
            },
            scale = scale,
            onZoomIn = { scale = minOf(MAX_SCALE, scale + 10) },
            onZoomOut = { scale = maxOf(MIN_SCALE, scale - 10) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        IconButton(
            onClick = { component.onBack() },
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
