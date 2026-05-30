package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * «Барабан»/wheel-эффект как в пикере часов: чем дальше центр элемента от центра
 * вьюпорта вдоль оси прокрутки, тем сильнее он уменьшается (`1 → [minScale]`),
 * приглушается (`1 → [minAlpha]`) И ПОДТЯГИВАЕТСЯ к центру, «слипаясь» с соседом.
 * Центральный элемент — полноразмерный; уезжающие к краю — мельче, бледнее и
 * прижаты друг к другу, без зияющих пустот между ними.
 *
 * **Только paint, без изменения следа (footprint).** Масштаб/альфа/сдвиг живут
 * целиком в `graphicsLayer`; слот элемента в layout остаётся натуральным. Это
 * принципиально: если ужимать сам след, измеренная длина контента начинает
 * зависеть от затухания → `canScrollForward` дёргается кадр-в-кадр → ужатие
 * крайних слотов входит в предельный цикл (мерцание тул-рейла на планшете в
 * ландшафте). При неизменном следе длина контента постоянна, и петля
 * замкнуться не может — отсюда отсутствие дёрганья в любом положении ленты.
 *
 * **Эффект — снимок [LazyListState.layoutInfo]** (а НЕ `onGloballyPositioned`):
 * прокрутка в Compose реализована сдвигом слоя, поэтому позиции детей при скролле
 * не пересчитываются, и эффект был бы статичным. `layoutInfo` — snapshot-state,
 * обновляется каждый кадр прокрутки, так что чтение его внутри `graphicsLayer`
 * пересчитывает трансформу на лету.
 *
 * **«Слипание» (подтяжка к центру).** Уменьшаясь на месте, элемент оставил бы
 * пустой зазор. Поэтому каждый слот дополнительно сдвигается к центру на
 * накопленное «ужатие» всех слотов между ним и центром — так видимый зазор между
 * соседями остаётся равным исходному, а уезжающие элементы визуально прижимаются
 * друг к другу. Сдвиг — тоже только в `graphicsLayer`, на измерение не влияет.
 *
 * @param index индекс ЭТОГО элемента в [listState] (в т.ч. trailing-кнопки).
 *   Затухание привязано к возможности скролла: элемент тускнеет/уменьшается только
 *   если в его сторону ЕЩЁ есть что прокручивать. У границы прокрутки (или когда
 *   всё помещается и скролл невозможен) крайний элемент остаётся полного размера —
 *   как у настоящего барабана, чьи концы упираются в стоп.
 * @param orientation ось прокрутки списка. Горизонтальные полосы —
 *   [RailOrientation.HORIZONTAL].
 * @param falloffPx расстояние (px) от центра, на котором эффект достигает максимума.
 *   `<= 0` → половина видимого размера вьюпорта (крайние у кромки).
 * @param minAlpha непрозрачность элемента на краю. `0` — уезжающий за кромку
 *   элемент исчезает полностью; небольшое ненулевое значение оставляет у кромки
 *   бледный «след» следующего элемента (подсказка «дальше есть ещё»).
 * @param edgeBand доля [falloffPx] у самой кромки, на которой эффект нарастает
 *   `0 → 1`; до неё элемент полноразмерный. Меньше → резче и ближе к краю.
 */
public fun Modifier.wheelItem(
    listState: LazyListState,
    index: Int,
    orientation: RailOrientation = RailOrientation.HORIZONTAL,
    falloffPx: Float = 0f,
    minScale: Float = 0.6f,
    minAlpha: Float = 0f,
    edgeBand: Float = 0.35f,
): Modifier =
    this.graphicsLayer {
        val t = wheelTransform(listState.layoutInfo, index, falloffPx, edgeBand, minScale) ?: return@graphicsLayer
        scaleX = t.scale
        scaleY = t.scale
        alpha = lerp(1f, minAlpha, t.falloff)
        if (orientation == RailOrientation.VERTICAL) translationY = t.pull else translationX = t.pull
    }

/** Результат [wheelTransform]: масштаб слота, подтяжка к центру (px) и доля затухания. */
internal class WheelTransform(
    val scale: Float,
    val pull: Float,
    val falloff: Float,
)

/**
 * Считает трансформу барабана для элемента [index] из снимка [layoutInfo]:
 * масштаб `1 → [minScale]`, подтяжку к центру (px, «слипание» с соседом) и долю
 * затухания `[0..1]` (для альфы). `null`, если элемент не виден или вьюпорт
 * вырожден — трансформацию тогда не применяем.
 *
 * Все величины считаются от НАТУРАЛЬНЫХ `offset`/`size` слотов (след не ужимаем),
 * поэтому длина контента и хвостовой запас прокрутки от применяемого масштаба не
 * зависят — петля «ужатие → длина → затухание» не возникает (см. [wheelItem]).
 *
 * @param falloffPx см. [wheelItem]; `<= 0` → половина видимого размера вьюпорта.
 * @param edgeBand ширина кромочной полосы (доля [falloffPx]), внутри которой
 *   геометрическая дистанция переводится в эффект.
 */
internal fun wheelTransform(
    layoutInfo: LazyListLayoutInfo,
    index: Int,
    falloffPx: Float,
    edgeBand: Float,
    minScale: Float,
): WheelTransform? {
    val visible = layoutInfo.visibleItemsInfo
    val item = visible.firstOrNull { it.index == index }
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val half = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
    // Элемент не виден или вьюпорт вырожден — трансформацию не применяем.
    if (item == null || half <= 0f) return null
    // `falloff > 0` гарантирован: либо явный [falloffPx] > 0, либо `half` (> 0 выше).
    val falloff = if (falloffPx > 0f) falloffPx else half
    val band = edgeBand.coerceIn(0.01f, 1f)

    // Запас прокрутки в каждую сторону = контент, скрытый за соответствующей
    // кромкой. От натуральных offset'ов (след не ужат) — стабилен под скроллом.
    val hiddenStart =
        visible.firstOrNull { it.index == 0 }
            ?.let { (layoutInfo.viewportStartOffset - it.offset).coerceAtLeast(0).toFloat() } ?: falloff
    val hiddenEnd =
        visible.firstOrNull { it.index == layoutInfo.totalItemsCount - 1 }
            ?.let { (it.offset + it.size - layoutInfo.viewportEndOffset).coerceAtLeast(0).toFloat() } ?: falloff

    val distance = item.offset + item.size / 2f - viewportCenter
    val t = wheelFalloff(item, viewportCenter, falloff, band, hiddenStart, hiddenEnd)
    val scale = lerp(1f, minScale, t)

    // Подтяжка к центру: идём по видимым слотам от центра наружу до текущего и
    // копим `(ужатие_прошлого + ужатие_текущего)/2`. Это ровно тот сдвиг, при
    // котором видимый зазор между соседями остаётся равным исходному, — уезжающие
    // элементы «слипаются», а не разъезжаются пустотами.
    var pull = 0f
    var prevShrink = 0f
    visible
        .filter {
            val d = it.offset + it.size / 2f - viewportCenter
            if (distance >= 0f) d in 0f..distance else d in distance..0f
        }
        .sortedBy { abs(it.offset + it.size / 2f - viewportCenter) }
        .forEach { slot ->
            val st = wheelFalloff(slot, viewportCenter, falloff, band, hiddenStart, hiddenEnd)
            val shrink = slot.size * st * (1f - minScale)
            pull += (prevShrink + shrink) / 2f
            prevShrink = shrink
        }
    return WheelTransform(scale = scale, pull = if (distance >= 0f) -pull else pull, falloff = t)
}

/**
 * Доля «отдаления» слота `[0..1]`: `0` пока его центр в середине вьюпорта (полный
 * размер), плавно → `1` по мере ухода центра в кромочную полосу [band] со стороны,
 * в которую ещё есть запас прокрутки ([hiddenStart]/[hiddenEnd]). Сглаживание —
 * smootherstep `6t⁵−15t⁴+10t³` (C² на обоих концах): нулевая производная у входа в
 * полосу убирает скачок масштаба, ровный финиш — дёрганье у самой кромки.
 */
private fun wheelFalloff(
    item: LazyListItemInfo,
    viewportCenter: Float,
    falloff: Float,
    band: Float,
    hiddenStart: Float,
    hiddenEnd: Float,
): Float {
    val distance = item.offset + item.size / 2f - viewportCenter
    val raw = (abs(distance) / falloff).coerceIn(0f, 1f)
    val inBand = ((raw - (1f - band)) / band).coerceIn(0f, 1f)
    val eased = inBand * inBand * inBand * (inBand * (inBand * 6f - 15f) + 10f)
    val hiddenOnSide = if (distance >= 0f) hiddenEnd else hiddenStart
    val sideFactor = (hiddenOnSide / falloff).coerceIn(0f, 1f)
    return eased * sideFactor
}

/**
 * Draws [content] with its leading and trailing [edgeWidth]-px bands faded to
 * fully transparent along [orientation]'s scroll axis, so items scrolling past
 * either end of a [LazyRow]/[LazyColumn] dissolve smoothly instead of being
 * hard-clipped to half-tiles at the viewport edge.
 *
 * Implemented as an offscreen layer masked by a `DstIn` gradient: the content is
 * rendered into its own buffer ([CompositingStrategy.Offscreen]) and then
 * multiplied by an alpha gradient that is `0` at each edge and `1` across the
 * middle. `DstIn` keeps the destination (content) only where the source (the
 * gradient) is opaque, fading the edges to nothing. The offscreen layer is
 * required: without it `DstIn` would blend against whatever is behind the strip
 * and erase it instead of just the content.
 *
 * The cross-axis is untouched. A `0` or negative [edgeWidth] is a no-op.
 *
 * @param fadeStart fade the leading edge — pass the list's `canScrollBackward`
 *   so a strip scrolled to its very start doesn't fade its first item.
 * @param fadeEnd fade the trailing edge — pass the list's `canScrollForward`.
 */
public fun Modifier.fadingEdges(
    orientation: RailOrientation,
    edgeWidth: Dp,
    fadeStart: Boolean = true,
    fadeEnd: Boolean = true,
): Modifier =
    if (edgeWidth <= 0.dp || (!fadeStart && !fadeEnd)) {
        this
    } else {
        this
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val edge = edgeWidth.toPx()
                val mainAxis = if (orientation == RailOrientation.VERTICAL) size.height else size.width
                // Доля главной оси, занятая каждой кромкой; на коротких полосах
                // ограничиваем половиной, чтобы кромки не перекрылись.
                val fraction = (edge / mainAxis).coerceIn(0f, 0.5f)
                // The edge that can no longer scroll (the drum hit its stop) is not
                // faded — its end item stays fully shown instead of being shadowed.
                val startColor = if (fadeStart) Color.Transparent else Color.Black
                val endColor = if (fadeEnd) Color.Transparent else Color.Black
                val brush =
                    if (orientation == RailOrientation.VERTICAL) {
                        Brush.verticalGradient(
                            0f to startColor,
                            fraction to Color.Black,
                            (1f - fraction) to Color.Black,
                            1f to endColor,
                        )
                    } else {
                        Brush.horizontalGradient(
                            0f to startColor,
                            fraction to Color.Black,
                            (1f - fraction) to Color.Black,
                            1f to endColor,
                        )
                    }
                drawRect(brush = brush, blendMode = BlendMode.DstIn)
            }
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
 * scrolls. Entry slots stay full-size with even gaps — only the rendered icon
 * scales/fades near the ends ([wheelItem]) and a fading-edge mask
 * ([fadingEdges]) dissolves whatever crosses either end, so no half-clipped
 * tile is ever shown.
 *
 * @param crossAxisSize Fixed size across the scroll axis (height for a
 *   horizontal strip, width for a vertical one). Pinned so the strip's thickness
 *   stays constant regardless of which entries are currently on-screen — a lazy
 *   list otherwise measures only visible items and the bar would resize (and
 *   shift neighbours) as the tallest entry scrolls out of view.
 * @param minAlpha edge opacity of the [wheelItem] falloff; `0` (default) lets an
 *   item fully vanish before it would clip at the viewport edge.
 * @param fadeEdgeWidth width of the leading/trailing fade band applied to the
 *   whole strip (see [fadingEdges]); defaults to one [crossAxisSize] (≈ one
 *   tile). `0.dp` disables the mask.
 * @param selectedKey key of the entry to mark as selected. A single circular
 *   indicator is drawn behind that entry and physically slides to its position
 *   when the selection changes (and follows the entry while scrolling). Pass
 *   `null` for no selection — the indicator fades out.
 * @param contentAlignment where the (content-sized) strip sits inside the space
 *   the caller gives it. Defaults to [Alignment.Center]. Pass e.g.
 *   [Alignment.CenterEnd] to pin a horizontal strip to the right edge while keeping
 *   the strip's own width stable — important when the caller hands it a fixed
 *   ([Modifier.weight]) box, so the strip width can't animate and wobble the
 *   [wheelItem] falloff on outer recompositions.
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
    minAlpha: Float = 0f,
    fadeEdgeWidth: Dp = crossAxisSize,
    selectedKey: Any? = null,
    indicatorColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorSize: Dp = 40.dp,
    contentAlignment: Alignment = Alignment.Center,
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
    val indicatorModifier =
        Modifier.drawBehind {
            if (indicatorAlpha <= 0f) return@drawBehind
            val c = animCenter.value
            val center =
                if (orientation == RailOrientation.VERTICAL) {
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

    // Items start shrinking close to the centre on both axes: a short rail with
    // only a few items visible otherwise reads as a static strip. The drum tapers
    // toward whichever end still has hidden items, so an unscrollable rail stays
    // full-size — the taper only appears when there is actually somewhere to scroll.
    val edgeBand = WHEEL_EDGE_BAND_WIDE
    BoxWithConstraints(modifier, contentAlignment = contentAlignment) {
        val itemBox: @Composable LazyItemScope.(Int, WheelEntry) -> Unit = { index, entry ->
            val reportModifier =
                if (index == selectedIndex) {
                    Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        selectedCenterRootMain =
                            if (orientation == RailOrientation.VERTICAL) {
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
                    // Масштаб/альфа + подтяжка к центру (соседи слипаются).
                    // report-модификатор последним: индикатор отслеживает
                    // фактический (уже сдвинутый) центр элемента.
                    .wheelItem(state, index, orientation, falloffPx, minScale, minAlpha, edgeBand)
                    .then(reportModifier),
            ) { entry.content() }
        }
        when (orientation) {
            RailOrientation.HORIZONTAL ->
                LazyRow(
                    modifier =
                        Modifier
                            .height(crossAxisSize)
                            .width(naturalLength.coerceAtMost(maxWidth))
                            .fadingEdges(orientation, fadeEdgeWidth, fadeStart = state.canScrollBackward, fadeEnd = state.canScrollForward)
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
            RailOrientation.VERTICAL ->
                LazyColumn(
                    modifier =
                        Modifier
                            .width(crossAxisSize)
                            .height(naturalLength.coerceAtMost(maxHeight))
                            .fadingEdges(orientation, fadeEdgeWidth, fadeStart = state.canScrollBackward, fadeEnd = state.canScrollForward)
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
        if (orientation == RailOrientation.HORIZONTAL) {
            WheelScrollButtons(
                state = state,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                background = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

/**
 * Leading/trailing scroll chevrons overlaid on a horizontally scrolling wheel,
 * shown only on desktop ([isDesktopPlatform]). A horizontal scroll gesture
 * (trackpad / shift-wheel) isn't available to every PC user, so these give an
 * explicit way to advance the rail; on touch the drag gesture is the affordance and
 * this composes nothing. Each chevron is shown only while [state] can still scroll
 * that way (and fades with that ability), so a rail resting against either stop
 * shows only the button that does something. Tapping advances the list by one item.
 *
 * Call inside the wheel's [Box] after the list, so the buttons align to its
 * start/end edges and sit above the items. [tint]/[background] come from the
 * surrounding surface so the buttons match the wheel's palette.
 */
@Composable
public fun BoxScope.WheelScrollButtons(
    state: LazyListState,
    tint: Color,
    background: Color,
) {
    if (!isDesktopPlatform) return
    val scope = rememberCoroutineScope()
    // Каждое нажатие сдвигает ленту ровно на один элемент. Сдвигаем фиксированной
    // дельтой (animateScrollBy), а НЕ animateScrollToItem: wheelItem ужимает след
    // крайних элементов, поэтому их измеренные offset «плывут», и доводка по индексу
    // дёргала правый край после остановки. Шаг = расстояние между началами соседних
    // слотов у центра (там они полноразмерны) = натуральный размер + зазор. Доезд до
    // самого стопа стабилен, потому что крайние элементы (idx 0 и последний) не ужимают
    // СЛЕД (см. [wheelItemFalloff]) — пристыковавшись, они не удлиняют контент и не
    // переоткрывают прокрутку, иначе `canScrollForward` мигал бы, дёргая ленту.
    val nudge: (Int) -> Unit = { direction ->
        scope.launch {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isNotEmpty()) {
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                val pivot = visible.minByOrNull { abs(it.offset + it.size / 2 - center) } ?: visible.first()
                val neighbour =
                    visible.firstOrNull { it.index == pivot.index + 1 }
                        ?: visible.firstOrNull { it.index == pivot.index - 1 }
                val step = neighbour?.let { abs(it.offset - pivot.offset).toFloat() } ?: pivot.size.toFloat()
                state.animateScrollBy(direction * step)
            }
        }
    }
    AnimatedVisibility(
        visible = state.canScrollBackward,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterStart),
    ) {
        ChevronButton(pointsForward = false, tint = tint, background = background, onClick = { nudge(-1) })
    }
    AnimatedVisibility(
        visible = state.canScrollForward,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        ChevronButton(pointsForward = true, tint = tint, background = background, onClick = { nudge(1) })
    }
}

/**
 * Reserves a horizontal gutter on each side of a scrolling rail so [WheelScrollButtons]
 * sit beside its items instead of over them — without it the chevrons overlay the edge
 * items and the end item can't be scrolled clear of the arrow. Apply to the list itself.
 * No-op on touch (no buttons) and when [state] can't scroll (no buttons shown), so a
 * rail that already fits keeps its full width.
 */
public fun Modifier.wheelScrollButtonGutter(state: LazyListState): Modifier =
    if (isDesktopPlatform && (state.canScrollForward || state.canScrollBackward)) {
        padding(horizontal = WHEEL_SCROLL_BUTTON_SIZE)
    } else {
        this
    }

/** A round, tinted scroll button drawing a chevron pointing forward (end) or back (start). */
@Composable
private fun ChevronButton(
    pointsForward: Boolean,
    tint: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(WHEEL_SCROLL_BUTTON_SIZE)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (pointsForward) WHEEL_SCROLL_FORWARD_LABEL else WHEEL_SCROLL_BACK_LABEL
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(WHEEL_SCROLL_BUTTON_SIZE * CHEVRON_FRACTION)) { drawChevron(tint, pointsForward) }
    }
}

/** Draws a `>` (or mirrored `<`) chevron filling [DrawScope.size], tinted [tint]. */
private fun DrawScope.drawChevron(
    tint: Color,
    pointsForward: Boolean,
) {
    val stroke = (size.minDimension * CHEVRON_STROKE_FRACTION).coerceAtLeast(1f)
    val near = size.width * 0.34f
    val far = size.width * 0.66f
    val mid = size.height * 0.5f
    val tipX = if (pointsForward) far else near
    val baseX = if (pointsForward) near else far
    drawLine(tint, Offset(baseX, size.height * 0.22f), Offset(tipX, mid), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(tint, Offset(baseX, size.height * 0.78f), Offset(tipX, mid), strokeWidth = stroke, cap = StrokeCap.Round)
}

/** True on desktop (JVM), false on touch — gates the on-screen [WheelScrollButtons]. */
internal expect val isDesktopPlatform: Boolean

/**
 * [wheelItem] edge band for [WheelStrip] (both orientations): the flat full-size
 * centre shrinks to almost nothing, so items begin scaling down close to the
 * centre. On a short rail showing only a few items this is what makes the drum read
 * as rotating rather than a static strip.
 */
public const val WHEEL_EDGE_BAND_WIDE: Float = 0.85f

private val WHEEL_SCROLL_BUTTON_SIZE = 22.dp
private const val CHEVRON_FRACTION = 0.46f
private const val CHEVRON_STROKE_FRACTION = 0.18f
private const val WHEEL_SCROLL_FORWARD_LABEL = "Прокрутить вперёд"
private const val WHEEL_SCROLL_BACK_LABEL = "Прокрутить назад"
