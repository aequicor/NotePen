package ru.kyamshanov.notepen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.abs

/**
 * «Барабан»/wheel-эффект как в пикере часов: масштабирует и притеняет элемент
 * Lazy-списка тем сильнее, чем дальше его центр от центра вьюпорта вдоль оси
 * прокрутки. Центральный элемент — в полную величину, крайние — уменьшены до
 * [minScale] и приглушены до [minAlpha].
 *
 * Считается от [LazyListState.layoutInfo] (а НЕ от `onGloballyPositioned`):
 * прокрутка в Compose реализована сдвигом слоя, поэтому `onGloballyPositioned`
 * детей при скролле не дёргается, и эффект был бы статичным. `layoutInfo` —
 * snapshot-state, обновляется каждый кадр прокрутки, так что чтение его внутри
 * `graphicsLayer` пересчитывает трансформу на лету.
 *
 * Эффект чисто отрисовочный (`graphicsLayer`) — layout/замеры/снап не меняются.
 *
 * @param index индекс ЭТОГО элемента в [listState] (в т.ч. trailing-кнопки).
 * Затухание привязано к возможности скролла: элемент тускнеет/уменьшается только
 * если в его сторону ЕЩЁ есть что прокручивать. У границы прокрутки (или когда
 * всё помещается и скролл невозможен) крайний элемент остаётся полного размера —
 * как у настоящего барабана, чьи концы упираются в стоп.
 *
 * @param falloffPx расстояние (px) от центра, на котором масштаб достигает
 *   [minScale]. `<= 0` → половина видимого размера вьюпорта (крайние у кромки).
 */
public fun Modifier.wheelItem(
    listState: LazyListState,
    index: Int,
    falloffPx: Float = 0f,
    minScale: Float = 0.6f,
    minAlpha: Float = 0.4f,
): Modifier = this.graphicsLayer {
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val itemCenter = item.offset + item.size / 2f
    val falloff = (if (falloffPx > 0f) falloffPx else (info.viewportEndOffset - info.viewportStartOffset) / 2f)
    if (falloff <= 0f) return@graphicsLayer
    val raw = (abs(itemCenter - viewportCenter) / falloff).coerceIn(0f, 1f)

    // Сколько контента скрыто за кромкой со стороны этого элемента — это и есть
    // запас прокрутки в его сторону. Нет запаса → не затемняем (элемент «упёрся»).
    val hiddenOnSide = if (itemCenter >= viewportCenter) {
        info.visibleItemsInfo.firstOrNull { it.index == info.totalItemsCount - 1 }
            ?.let { (it.offset + it.size - info.viewportEndOffset).coerceAtLeast(0).toFloat() }
            ?: falloff
    } else {
        info.visibleItemsInfo.firstOrNull { it.index == 0 }
            ?.let { (info.viewportStartOffset - it.offset).coerceAtLeast(0).toFloat() }
            ?: falloff
    }
    val sideFactor = (hiddenOnSide / falloff).coerceIn(0f, 1f)

    val t = raw * sideFactor
    val scale = lerp(1f, minScale, t)
    scaleX = scale
    scaleY = scale
    alpha = lerp(1f, minAlpha, t)
}

/**
 * One entry in a [WheelStrip]: a stable [key] and the composable [content] drawn
 * for it. The content owns its own click handling — the wheel only scrolls and
 * applies the visual [wheelItem] transform.
 *
 * @property mainAxisSize the entry's size along the scroll axis. Used only to
 *   estimate the strip's natural length so it wraps its content tightly; pass an
 *   accurate value for short entries (dividers, labels, small chips) or the strip
 *   over-reserves space and shows large internal padding.
 */
public class WheelEntry(
    public val key: Any,
    public val mainAxisSize: Dp = 40.dp,
    public val content: @Composable () -> Unit,
)

/**
 * A single "drum"/wheel of [entries] laid out along [orientation]: a
 * [LazyRow]/[LazyColumn] where entries fade and shrink the closer they sit to
 * the strip's edges (see [wheelItem]), full size in the middle.
 *
 * Heterogeneous entries (tool toggles, settings slots, preset chips) share one
 * scroll axis so everything fits on a phone where a fixed strip would overflow.
 * The strip sizes itself to its content and is centred in the available space;
 * once the content would exceed it, the strip caps at the available size and
 * scrolls. The first/last entries therefore sit at the dimmed edges by design.
 *
 * @param crossAxisSize Fixed size across the scroll axis (height for a
 *   horizontal strip, width for a vertical one). Pinned so the strip's thickness
 *   stays constant regardless of which entries are currently on-screen — a lazy
 *   list otherwise measures only visible items and the bar would resize (and
 *   shift neighbours) as the tallest entry scrolls out of view.
 * @param selectedKey key of the entry to mark as selected. A single circular
 *   indicator is drawn behind that entry and physically slides to its position
 *   when the selection changes (and follows the entry while scrolling). Pass
 *   `null` for no selection — the indicator fades out.
 */
@Composable
public fun WheelStrip(
    entries: List<WheelEntry>,
    orientation: RailOrientation,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    crossAxisSize: Dp = 40.dp,
    gap: Dp = 2.dp,
    edgePadding: Dp = 4.dp,
    falloff: Dp = 0.dp,
    minScale: Float = 0.6f,
    minAlpha: Float = 0.4f,
    selectedKey: Any? = null,
    indicatorColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorSize: Dp = 40.dp,
) {
    val falloffPx = if (falloff > 0.dp) with(LocalDensity.current) { falloff.toPx() } else 0f
    val count = entries.size
    val naturalLength =
        edgePadding * 2 + entries.fold(0.dp) { acc, e -> acc + e.mainAxisSize } + gap * (count - 1).coerceAtLeast(0)

    // Single sliding selection indicator. Its center is taken from the selected
    // entry's ACTUAL rendered bounds (via onGloballyPositioned, in root coords so
    // it also tracks scrolling) — deriving it from layoutInfo offsets misaligned
    // with the icon because of content padding / min-interactive button sizing.
    // The center is animated, so a selection change slides instead of jumping.
    val selectedIndex = entries.indexOfFirst { it.key == selectedKey }
    val indicatorRadiusPx = with(LocalDensity.current) { indicatorSize.toPx() } / 2f
    var listOriginMain by remember { mutableStateOf(0f) }
    var selectedCenterRootMain by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(selectedIndex) { if (selectedIndex < 0) selectedCenterRootMain = null }
    val selectedCenter = selectedCenterRootMain?.let { it - listOriginMain }

    val animCenter = remember { Animatable(0f) }
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(selectedCenter) {
        val t = selectedCenter ?: return@LaunchedEffect
        if (positioned) {
            animCenter.animateTo(t, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        } else {
            animCenter.snapTo(t)
            positioned = true
        }
    }
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (selectedCenter != null) 1f else 0f,
        label = "wheelIndicatorAlpha",
    )
    val indicatorModifier = Modifier.drawBehind {
        if (indicatorAlpha <= 0f) return@drawBehind
        val c = animCenter.value
        val center = if (orientation == RailOrientation.VERTICAL) {
            Offset(size.width / 2f, c)
        } else {
            Offset(c, size.height / 2f)
        }
        drawCircle(color = indicatorColor, radius = indicatorRadiusPx, center = center, alpha = indicatorAlpha)
    }
    val onListPositioned: (LayoutCoordinates) -> Unit = { coords ->
        val origin = coords.positionInRoot()
        listOriginMain = if (orientation == RailOrientation.VERTICAL) origin.y else origin.x
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val itemBox: @Composable LazyItemScope.(Int, WheelEntry) -> Unit = { index, entry ->
            val reportModifier = if (index == selectedIndex) {
                Modifier.onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    selectedCenterRootMain = if (orientation == RailOrientation.VERTICAL) {
                        pos.y + coords.size.height / 2f
                    } else {
                        pos.x + coords.size.width / 2f
                    }
                }
            } else {
                Modifier
            }
            Box(
                Modifier
                    .animateItem()
                    .wheelItem(state, index, falloffPx, minScale, minAlpha)
                    .then(reportModifier),
            ) { entry.content() }
        }
        when (orientation) {
            RailOrientation.HORIZONTAL -> LazyRow(
                modifier = Modifier
                    .height(crossAxisSize)
                    .width(naturalLength.coerceAtMost(maxWidth))
                    .then(indicatorModifier)
                    .animateContentSize()
                    .onGloballyPositioned(onListPositioned),
                state = state,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = edgePadding),
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { index, entry -> itemBox(index, entry) }
            }
            RailOrientation.VERTICAL -> LazyColumn(
                modifier = Modifier
                    .width(crossAxisSize)
                    .height(naturalLength.coerceAtMost(maxHeight))
                    .then(indicatorModifier)
                    .animateContentSize()
                    .onGloballyPositioned(onListPositioned),
                state = state,
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(vertical = edgePadding),
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { index, entry -> itemBox(index, entry) }
            }
        }
    }
}
