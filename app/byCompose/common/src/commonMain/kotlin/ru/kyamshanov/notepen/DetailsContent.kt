package ru.kyamshanov.notepen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailsContent(component: DetailsComponent, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.LightGray)) {
        val localWindowInfo = LocalWindowInfo.current
        val windowSizeInPx = rememberSaveable { localWindowInfo.containerSize }
        val model by component.model.subscribeAsState()
        val filePath = remember(model.title) { model.title }
        val pdfManager = remember(filePath) { PdfManager(filePath) }
        val metadata = remember(pdfManager.metadata) { pdfManager.metadata }
        val pages = remember(metadata.pages) { metadata.pages }
        var scale by remember { mutableStateOf(100) }
        val pagesCache = remember(pages, scale) { mutableMapOf<Int, ImageBitmap>() }

        DisposableEffect(pdfManager) {
            onDispose {
                pdfManager.close()
            }
        }


        LazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
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
                    var imageBitmap by remember(scale) { mutableStateOf<ImageBitmap?>(null) }
                    LaunchedEffect(scale) {
                        withContext(Dispatchers.IO) {
                            imageBitmap = pagesCache[pageIndex] ?: pdfManager.renderPage(
                                pageIndex,
                                IntSize(targetWidthPx, targetHeightPx)
                            )
                                ?.also { pagesCache[pageIndex] = it }
                        }
                    }

                    val bm = imageBitmap
                    if (bm != null) {
                        val pdfDrawingState = remember(bm) { PdfDrawingState() }

                        DrawablePdfPage(
                            bitmap = bm,
                            pdfDrawingState = pdfDrawingState,
                            modifier = Modifier.size(width, height)
                        )
                    } else {
                        Text("Loading")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Button(
                onClick = {
                    scale = minOf(200, scale + 10)
                }) {
                Text("+")
            }
            Text(scale.toString())
            Button(
                onClick = {
                    scale = maxOf(0, scale - 10)
                }) {
                Text("-")
            }
        }

        SmallFloatingActionButton(
            onClick = { component.onBack() },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
            )
        }
    }
}
