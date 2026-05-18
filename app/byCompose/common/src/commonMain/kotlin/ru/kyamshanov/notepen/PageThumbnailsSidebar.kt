package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdf.domain.model.PdfPageInfo
import ru.kyamshanov.notepen.pdf.domain.port.PdfPageRenderer
import ru.kyamshanov.notepen.pdf.presentation.toImageBitmap

/** Filter modes shown in the sidebar header. Picked via dropdown on the chip. */
enum class PageFilterMode { ALL, FAVORITES, ANNOTATED }

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
 *   used by the [PageFilterMode.ANNOTATED] filter and shown as the count next to the chip.
 * @param favoritePageIndices indices of pages the user marked with the star.
 * @param onToggleFavorite invoked when the star button on a thumbnail is tapped.
 * @param pagePaths per-page stroke snapshot, drawn on top of the bitmap so the user
 *   can see at a glance what is on each page.
 */
@Composable
fun PageThumbnailsSidebar(
    pages: List<PdfPageInfo>,
    pdfDocument: PdfDocument?,
    renderer: PdfPageRenderer,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    annotatedPageIndices: Set<Int> = emptySet(),
    favoritePageIndices: Set<Int> = emptySet(),
    onToggleFavorite: (Int) -> Unit = {},
    pagePaths: (Int) -> List<DrawingPath> = { emptyList() },
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var filterMode by rememberSaveable { mutableStateOf(PageFilterMode.ALL) }
    // Кэш отрендеренных миниатюр живёт на уровне сайдбара, а не отдельного
    // ThumbnailItem'а: LazyColumn утилизирует ушедшие за экран элементы и
    // при возврате создаёт их заново, теряя локальный bitmap. Это давало
    // визуальное мигание превью верхних страниц при автоскролле сайдбара
    // (двойной scrollToItem для выравнивания по нижнему краю). Ключ — по
    // pdfDocument, чтобы при смене документа кэш сбрасывался.
    val bitmapCache = remember(pdfDocument) { mutableStateMapOf<Int, ImageBitmap>() }
    // Глобальный для сайдбара mutex, через который проходят ВСЕ запросы
    // renderer.renderPage из миниатюр. Так максимум одна миниатюра в
    // момент времени держит лок Android-овского PdfRenderer'а: если
    // пользователь начнёт скролл сразу после короткой паузы, ему придётся
    // подождать максимум один рендер (~50-150 мс), а не очередь из 5+.
    val thumbRenderMutex = remember { kotlinx.coroutines.sync.Mutex() }

    val visiblePages = when (filterMode) {
        PageFilterMode.ALL -> pages
        PageFilterMode.FAVORITES -> pages.filter { it.pageIndex in favoritePageIndices }
        PageFilterMode.ANNOTATED -> pages.filter { it.pageIndex in annotatedPageIndices }
    }

    // Сайдбар работает как скользящее окно: пока текущая страница в нём
    // полностью видна — не двигаемся (клик по миниатюре только меняет
    // выделение). Если PDF проскроллили так, что страница выпала снизу —
    // прижимаем её к нижнему краю вьюпорта так, чтобы следующая миниатюра
    // не торчала «огрызком». Назад — симметрично, по верхнему краю.
    //
    // Дебаунс через snapshotFlow + collectLatest: во время непрерывного
    // скролла PDF currentPage меняется десятки раз в секунду, и каждый
    // scrollToItem поднимает новые ThumbnailItem'ы в композицию, дёргая
    // тяжёлый renderer.renderPage — это лагало основной просмотр PDF.
    // Теперь авто-скролл сайдбара срабатывает только после паузы в
    // SIDEBAR_AUTOSCROLL_DEBOUNCE_MS. Подсветка активной страницы
    // (isCurrentPage) при этом обновляется мгновенно — она идёт через
    // обычную рекомпозицию, минуя этот эффект.
    // currentPage — обычный Int-параметр, не State; чтобы snapshotFlow
    // мог наблюдать его смены из LaunchedEffect-корутины, заворачиваем в
    // rememberUpdatedState. Иначе корутина держит захваченное при первом
    // запуске значение и авто-скролл сайдбара перестаёт реагировать.
    val currentPageState = rememberUpdatedState(currentPage)
    val visiblePagesState = rememberUpdatedState(visiblePages)
    // Подсветка миниатюры тоже идёт через дебаунс, иначе при непрерывном
    // скролле PDF рамка моргает между десятками миниатюр в секунду. После
    // паузы — встаёт один раз на актуальную страницу.
    var displayedCurrentPage by remember { mutableStateOf(currentPage) }
    // Сигнал «PDF простаивает», по которому ThumbnailItem решает, можно
    // ли сейчас дёрнуть renderer.renderPage. Android PdfRenderer
    // однопоточный (synchronized в AndroidPdfPageRenderer), так что рендер
    // миниатюры во время скролла основного viewer'а блокирует основной
    // поток рендера и вызывает видимый лаг — особенно на скролле вниз,
    // где каждая новая страница тянет рендер.
    val pdfIdle = remember { mutableStateOf(true) }
    LaunchedEffect(filterMode) {
        snapshotFlow { currentPageState.value }.collectLatest { page ->
            pdfIdle.value = false
            // Дебаунс: во время непрерывного PDF-скролла currentPage меняется
            // десятки раз в секунду; каждый scrollToItem подтягивал в
            // композицию новые ThumbnailItem'ы → дёргался тяжёлый
            // renderer.renderPage и лагал основной просмотр. Скроллим
            // сайдбар (и обновляем подсветку) только после паузы.
            delay(SIDEBAR_AUTOSCROLL_DEBOUNCE_MS)
            displayedCurrentPage = page
            pdfIdle.value = true
            val currentVisiblePages = visiblePagesState.value
            if (currentVisiblePages.isEmpty()) return@collectLatest
            val targetIndex = currentVisiblePages.indexOfFirst { it.pageIndex == page }
                .coerceAtLeast(0)
                .coerceAtMost(currentVisiblePages.size - 1)
            val info = listState.layoutInfo
            val fullyVisible = info.visibleItemsInfo.filter { item ->
                item.offset >= info.viewportStartOffset &&
                    item.offset + item.size <= info.viewportEndOffset
            }
            val firstFull = fullyVisible.firstOrNull()?.index
            val lastFull = fullyVisible.lastOrNull()?.index
            when {
                firstFull == null || lastFull == null -> listState.scrollToItem(targetIndex)
                targetIndex in firstFull..lastFull -> Unit
                targetIndex > lastFull -> {
                    // Выравнивание по нижнему краю одним scrollToItem с
                    // отрицательным scrollOffset — без промежуточного снапа.
                    val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
                    val targetVisible = info.visibleItemsInfo
                        .firstOrNull { it.index == targetIndex }
                    if (targetVisible != null && targetVisible.size < viewportHeight) {
                        listState.scrollToItem(targetIndex, targetVisible.size - viewportHeight)
                    } else {
                        listState.scrollToItem(targetIndex)
                        val info2 = listState.layoutInfo
                        val targetItem = info2.visibleItemsInfo
                            .firstOrNull { it.index == targetIndex }
                        val viewportHeight2 = info2.viewportEndOffset - info2.viewportStartOffset
                        if (targetItem != null && targetItem.size < viewportHeight2) {
                            listState.scrollToItem(targetIndex, targetItem.size - viewportHeight2)
                        }
                    }
                }
                else -> listState.scrollToItem(targetIndex)
            }
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
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
            FilterModeChip(
                mode = filterMode,
                totalCount = pages.size,
                favoriteCount = favoritePageIndices.size,
                annotatedCount = annotatedPageIndices.size,
                onModeChange = { filterMode = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
            if (visiblePages.isEmpty() && filterMode != PageFilterMode.ALL) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 24.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        text = "Нет страниц",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    itemsIndexed(
                        items = visiblePages,
                        key = { _, page -> page.pageIndex },
                    ) { _, page ->
                        ThumbnailItem(
                            page = page,
                            pdfDocument = pdfDocument,
                            renderer = renderer,
                            pageNumber = page.pageIndex + 1,
                            isCurrentPage = page.pageIndex == displayedCurrentPage,
                            isFavorite = page.pageIndex in favoritePageIndices,
                            paths = pagePaths(page.pageIndex),
                            cachedBitmap = bitmapCache[page.pageIndex],
                            onBitmapRendered = { bm -> bitmapCache[page.pageIndex] = bm },
                            pdfIdle = pdfIdle,
                            renderMutex = thumbRenderMutex,
                            onClick = { onPageClick(page.pageIndex) },
                            onToggleFavorite = { onToggleFavorite(page.pageIndex) },
                        )
                        Spacer(Modifier.height(THUMBNAIL_SPACING))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterModeChip(
    mode: PageFilterMode,
    totalCount: Int,
    favoriteCount: Int,
    annotatedCount: Int,
    onModeChange: (PageFilterMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = mode != PageFilterMode.ALL

    val targetBackground = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val targetContent = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val targetBorder = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val background by animateColorAsState(targetBackground, label = "filter-chip-bg")
    val contentColor by animateColorAsState(targetContent, label = "filter-chip-fg")
    val borderColor by animateColorAsState(targetBorder, label = "filter-chip-border")

    val currentChip = chipContentFor(mode, totalCount, favoriteCount, annotatedCount)
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            color = background,
            contentColor = contentColor,
            shape = RoundedCornerShape(FILTER_CHIP_CORNER),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            tonalElevation = if (active) 2.dp else 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = currentChip.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center),
                )
                Icon(
                    imageVector = currentChip.icon,
                    contentDescription = "Фильтр: ${currentChip.label}",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(16.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            PageFilterMode.entries.forEach { option ->
                val item = chipContentFor(option, totalCount, favoriteCount, annotatedCount)
                DropdownMenuItem(
                    text = { Text("${item.label} · ${item.count}") },
                    leadingIcon = {
                        Icon(imageVector = item.icon, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        if (option != mode) onModeChange(option)
                    },
                )
            }
        }
    }
}

private data class ChipContent(
    val icon: ImageVector,
    val label: String,
    val count: Int,
)

private fun chipContentFor(
    mode: PageFilterMode,
    totalCount: Int,
    favoriteCount: Int,
    annotatedCount: Int,
): ChipContent = when (mode) {
    PageFilterMode.ALL -> ChipContent(Icons.Filled.FilterList, "Все", totalCount)
    PageFilterMode.FAVORITES -> ChipContent(Icons.Filled.Star, "Избр.", favoriteCount)
    PageFilterMode.ANNOTATED -> ChipContent(Icons.Filled.Edit, "Надписи", annotatedCount)
}

@Composable
private fun ThumbnailItem(
    page: PdfPageInfo,
    pdfDocument: PdfDocument?,
    renderer: PdfPageRenderer,
    pageNumber: Int,
    isCurrentPage: Boolean,
    isFavorite: Boolean,
    paths: List<DrawingPath>,
    cachedBitmap: ImageBitmap?,
    onBitmapRendered: (ImageBitmap) -> Unit,
    pdfIdle: State<Boolean>,
    renderMutex: kotlinx.coroutines.sync.Mutex,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val density = LocalDensity.current
    val thumbWidthPx = with(density) { THUMBNAIL_WIDTH.roundToPx() }
    val thumbHeightPx = (thumbWidthPx / page.aspectRatio).toInt()

    LaunchedEffect(page.pageIndex, pdfDocument, cachedBitmap == null) {
        if (cachedBitmap != null) return@LaunchedEffect
        val doc = pdfDocument ?: return@LaunchedEffect
        // Гейтим рендер миниатюры дважды: ждём «PDF простаивает», берём
        // глобальный mutex сайдбара (один рендер в момент) и ещё раз
        // перепроверяем pdfIdle под локом. Если за время ожидания
        // пользователь снова начал скроллить — отпускаем mutex и идём на
        // следующий круг ожидания pdfIdle. Это не даёт миниатюрам отбирать
        // лок Android PdfRenderer'а у основного viewer'а на скролле.
        while (true) {
            snapshotFlow { pdfIdle.value }.first { it }
            val rendered: ImageBitmap? = renderMutex.lock().let {
                try {
                    if (!pdfIdle.value) null
                    else renderer.renderPage(
                        document = doc,
                        pageIndex = page.pageIndex,
                        widthPx = thumbWidthPx,
                        heightPx = thumbHeightPx,
                    ).toImageBitmap()
                } finally {
                    renderMutex.unlock()
                }
            }
            if (rendered != null) {
                onBitmapRendered(rendered)
                return@LaunchedEffect
            }
        }
    }

    val borderColor = if (isCurrentPage) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth: Dp = if (isCurrentPage) 2.dp else 1.dp
    val strokeScratch = remember { Path() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(THUMBNAIL_WIDTH)
                .height(with(LocalDensity.current) { thumbHeightPx.toDp() })
                .background(MaterialTheme.colorScheme.surface)
                .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
                .clickable(onClick = onClick),
        ) {
            val bm = cachedBitmap
            if (bm != null) {
                Image(
                    bitmap = bm,
                    contentDescription = "Страница $pageNumber",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize(),
                )
            }
            if (paths.isNotEmpty()) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    paths.forEach { path ->
                        drawStrokeWithPressure(path, size.width, size.height, strokeScratch)
                    }
                }
            }
            FavoriteStar(
                isFavorite = isFavorite,
                onToggle = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
            )
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

@Composable
private fun FavoriteStar(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (isFavorite) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    }
    Box(
        modifier = modifier
            .size(22.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                shape = CircleShape,
            )
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (isFavorite) "Убрать из избранного" else "Добавить в избранное",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

internal val SIDEBAR_WIDTH = 100.dp
private val THUMBNAIL_WIDTH = 80.dp
private val THUMBNAIL_SPACING = 8.dp
private val SIDEBAR_CORNER = 12.dp
private val FILTER_CHIP_CORNER = 8.dp
private const val SIDEBAR_ALPHA = 0.92f
private const val SIDEBAR_AUTOSCROLL_DEBOUNCE_MS = 120L
