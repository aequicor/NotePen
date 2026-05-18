package ru.kyamshanov.notepen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.pdf.presentation.toImageBitmap

/**
 * Vertical sidebar showing page thumbnails for quick navigation.
 *
 * Thumbnails are rendered lazily at [THUMBNAIL_WIDTH] wide (the height is computed from each
 * page's aspect ratio). Only visible items trigger a render call; off-screen thumbnails are
 * discarded by [LazyColumn] recomposition and re-rendered on scroll-back.
 *
 * [currentPage] drives an auto-scroll so the sidebar follows the main view.
 *
 * @param annotatedPageIndices indices of pages that have at least one annotation stroke;
 *   drives the filter badge counter and hides unannotated pages when the filter is active.
 */
@Composable
fun PageThumbnailsSidebar(
    pages: List<PdfPageInfo>,
    pdfDocument: PdfDocument?,
    renderer: PdfPageRenderer,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    annotatedPageIndices: Set<Int> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var showOnlyAnnotated by rememberSaveable { mutableStateOf(false) }

    val visiblePages = if (showOnlyAnnotated && annotatedPageIndices.isNotEmpty()) {
        pages.filter { it.pageIndex in annotatedPageIndices }
    } else {
        pages
    }

    LaunchedEffect(currentPage, showOnlyAnnotated, pages.size) {
        if (visiblePages.isNotEmpty()) {
            val targetIndex = visiblePages.indexOfFirst { it.pageIndex == currentPage }
                .coerceAtLeast(0)
                .coerceAtMost(visiblePages.size - 1)
            listState.animateScrollToItem(targetIndex)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(SIDEBAR_WIDTH),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SIDEBAR_ALPHA),
        shape = RoundedCornerShape(topEnd = SIDEBAR_CORNER, bottomEnd = SIDEBAR_CORNER),
        tonalElevation = 2.dp,
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
            ) {
                Text(
                    text = "${annotatedPageIndices.size}/${pages.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { showOnlyAnnotated = !showOnlyAnnotated },
                    enabled = annotatedPageIndices.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = "Только страницы с пометками",
                        tint = if (showOnlyAnnotated) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                itemsIndexed(visiblePages) { _, page ->
                    ThumbnailItem(
                        page = page,
                        pdfDocument = pdfDocument,
                        renderer = renderer,
                        pageNumber = page.pageIndex + 1,
                        isCurrentPage = page.pageIndex == currentPage,
                        onClick = { onPageClick(page.pageIndex) },
                    )
                    Spacer(Modifier.height(THUMBNAIL_SPACING))
                }
            }
        }
    }
}

@Composable
private fun ThumbnailItem(
    page: PdfPageInfo,
    pdfDocument: PdfDocument?,
    renderer: PdfPageRenderer,
    pageNumber: Int,
    isCurrentPage: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val thumbWidthPx = with(density) { THUMBNAIL_WIDTH.roundToPx() }
    val thumbHeightPx = (thumbWidthPx / page.aspectRatio).toInt()

    var bitmap by remember(page.pageIndex, pdfDocument) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(page.pageIndex, pdfDocument) {
        val doc = pdfDocument ?: return@LaunchedEffect
        bitmap = renderer.renderPage(
            document = doc,
            pageIndex = page.pageIndex,
            widthPx = thumbWidthPx,
            heightPx = thumbHeightPx,
        ).toImageBitmap()
    }

    val borderColor = if (isCurrentPage) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth: Dp = if (isCurrentPage) 2.dp else 1.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(THUMBNAIL_WIDTH)
                .height(with(LocalDensity.current) { thumbHeightPx.toDp() })
                .background(MaterialTheme.colorScheme.surface)
                .border(borderWidth, borderColor, RoundedCornerShape(4.dp)),
        ) {
            val bm = bitmap
            if (bm != null) {
                Image(
                    bitmap = bm,
                    contentDescription = "Страница $pageNumber",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Text(
            text = "$pageNumber",
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrentPage) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private val SIDEBAR_WIDTH = 100.dp
private val THUMBNAIL_WIDTH = 80.dp
private val THUMBNAIL_SPACING = 8.dp
private val SIDEBAR_CORNER = 12.dp
private const val SIDEBAR_ALPHA = 0.92f
