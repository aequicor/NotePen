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
import androidx.compose.ui.layout.layout
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
import kotlin.math.roundToInt

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
 * `graphicsLayer`/`layout` пересчитывает трансформу и след на лету.
 *
 * Уменьшается не только рисунок (`graphicsLayer`), но и СЛЕД элемента по главной
 * оси (`layout`): слоту в Lazy-списке докладывается `mainAxisSize * scale`, а
 * ребёнок центрируется в нём. Поэтому крайние элементы не оставляют пустого
 * полноразмерного зазора — `LazyRow`/`LazyColumn` подтягивает соседей внутрь, и
 * рисунок ровно заполняет свой ужатый слот (тот же `scale` из [wheelItemFalloff]
 * управляет и следом, и визуальным масштабом, так что в покое они совпадают).
 * Затухание сосредоточено в узкой кромочной полосе ([edgeBand]) — в середине
 * элементы полноразмерны и зазоры не «гуляют».
 *
 * @param index индекс ЭТОГО элемента в [listState] (в т.ч. trailing-кнопки).
 * Затухание привязано к возможности скролла: элемент тускнеет/уменьшается только
 * если в его сторону ЕЩЁ есть что прокручивать. У границы прокрутки (или когда
 * всё помещается и скролл невозможен) крайний элемент остаётся полного размера —
 * как у настоящего барабана, чьи концы упираются в стоп.
 *
 * @param orientation ось прокрутки списка: вдоль неё ужимается след элемента.
 *   Поперечная ось не меняется. `CollapsedPill`/горизонтальные полосы — [RailOrientation.HORIZONTAL].
 * @param falloffPx расстояние (px) от центра, на котором эффект достигает максимума.
 *   `<= 0` → половина видимого размера вьюпорта (крайние у кромки).
 * @param minAlpha непрозрачность элемента на краю. По умолчанию `0` — уезжающий
 *   за кромку элемент исчезает полностью, без обрезанного «половинчатого» тайла.
 * @param edgeBand доля [falloffPx] у самой кромки, на которой эффект нарастает
 *   `0 → 1`; до неё элемент полноразмерный. Меньше → резче и ближе к краю.
 * @param naturalSizes общий на полосу кэш «индекс → натуральный (до ужатия) размер
 *   элемента по главной оси, px». Заполняется здесь же при измерении и читается
 *   [wheelItemFalloff], чтобы хвостовой запас прокрутки считался от НАТУРАЛЬНОЙ
 *   длины контента, а не от ужатой. Без этого ужатие следа меняло бы измеренную
 *   длину → пересчёт затухания → новое ужатие: петля, из-за которой крайние
 *   элементы дёргались при любой форсированной перелейаутизации (движение мыши над
 *   контентом). Передавайте один `remember { mutableMapOf() }` на полосу; `null` —
 *   старое поведение (хвостовой запас от ужатой длины, с риском дрожания).
 */
public fun Modifier.wheelItem(
    listState: LazyListState,
    index: Int,
    orientation: RailOrientation = RailOrientation.HORIZONTAL,
    falloffPx: Float = 0f,
    minScale: Float = 0.6f,
    minAlpha: Float = 0f,
    edgeBand: Float = 0.35f,
    naturalSizes: MutableMap<Int, Int>? = null,
): Modifier =
    this
        // След по главной оси ужимается тем же `scale`, что и визуальный масштаб
        // ниже: слоту докладывается уменьшенный размер, ребёнок центрируется —
        // соседи подтягиваются, пустого полноразмерного зазора у кромки нет.
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val naturalMain = if (orientation == RailOrientation.VERTICAL) placeable.height else placeable.width
            naturalSizes?.put(index, naturalMain)
            val t = wheelItemFalloff(listState.layoutInfo, index, falloffPx, edgeBand, naturalSizes) ?: 0f
            val scale = lerp(1f, minScale, t)
            if (orientation == RailOrientation.VERTICAL) {
                val h = (placeable.height * scale).roundToInt()
                layout(placeable.width, h) {
                    placeable.placeRelative(0, (h - placeable.height) / 2)
                }
            } else {
                val w = (placeable.width * scale).roundToInt()
                layout(w, placeable.height) {
                    placeable.placeRelative((w - placeable.width) / 2, 0)
                }
            }
        }.graphicsLayer {
            val t = wheelItemFalloff(listState.layoutInfo, index, falloffPx, edgeBand, naturalSizes) ?: return@graphicsLayer
            val scale = lerp(1f, minScale, t)
            scaleX = scale
            scaleY = scale
            alpha = lerp(1f, minAlpha, t)
        }

/**
 * Доля «отдаления» элемента `[0..1]`: `0` пока его центр в середине вьюпорта
 * (полный размер/непрозрачность), плавно → `1` по мере ухода центра в кромочную
 * полосу [edgeBand] со стороны, в которую ещё есть запас прокрутки. Чистая
 * функция от [layoutInfo] — общий источник для масштаба и альфы [wheelItem].
 *
 * `null`, если элемент не виден или вьюпорт вырожден (трансформацию не применяем).
 *
 * @param falloffPx см. [wheelItem]; `<= 0` → половина видимого размера вьюпорта.
 * @param edgeBand см. [wheelItem]: ширина кромочной полосы (доля [falloffPx]),
 *   только внутри которой геометрическая дистанция переводится в эффект.
 * @param naturalSizes см. [wheelItem]: кэш натуральных размеров элементов. Когда
 *   передан, хвостовой запас прокрутки считается от натуральной длины контента
 *   (масштаб сокращается тождественно), поэтому он НЕ зависит от ужатия следа —
 *   тем и разрывается петля «ужатие → длина → затухание → ужатие».
 */
internal fun wheelItemFalloff(
    layoutInfo: LazyListLayoutInfo,
    index: Int,
    falloffPx: Float,
    edgeBand: Float = 0.35f,
    naturalSizes: Map<Int, Int>? = null,
): Float? {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
    // Первый и последний элементы — это сами стопы прокрутки: им НЕ даём ужиматься.
    // Если крайний элемент ужимает след, то, пристыковавшись к краю, он доразжимается,
    // удлиняет контент и переоткрывает прокрутку — `canScrollForward` начинает мигать,
    // и у самого конца лента входит в автоколебание (наблюдаемое «дёрганье» при доезде
    // стрелкой). Полноразмерный стоп стабилен: длина контента у края не меняется.
    if (index == 0 || index == layoutInfo.totalItemsCount - 1) return 0f
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    // Центр элемента берём в НАТУРАЛЬНЫХ координатах (до ужатия): замеренный offset
    // включает ужатие всех предыдущих слотов, и на хвостовой стороне эти ужатия
    // накапливаются (вся кромочная полоса сжата) → сдвиг центра → пересчёт масштаба
    // → новый сдвиг: петля с усилением >1, из-за которой дёргается ИМЕННО правый
    // край (левый привязан к якорю прокрутки, ужатие уходит от активной кромки и
    // затухает). Восстанавливаем натуральный центр: `offset + ужатие предыдущих
    // видимых слотов + натуральный размер/2` — он от применяемого масштаба не зависит.
    val naturalThis = (naturalSizes?.get(index) ?: item.size).toFloat()
    val itemCenter = item.offset + shrinkBefore(layoutInfo, naturalSizes, index) + naturalThis / 2f
    val falloff = if (falloffPx > 0f) falloffPx else (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f
    if (falloff <= 0f) return null
    val raw = (abs(itemCenter - viewportCenter) / falloff).coerceIn(0f, 1f)

    // Эффект только в кромочной полосе: до неё `raw` отображается в 0 (полный
    // размер), внутри — нарастает 0→1 со сглаживанием (smoothstep), чтобы не было
    // резкого скачка масштаба на въезде в полосу.
    val band = edgeBand.coerceIn(0.01f, 1f)
    val inBand = ((raw - (1f - band)) / band).coerceIn(0f, 1f)
    val eased = inBand * inBand * (3f - 2f * inBand)

    // Сколько контента скрыто за кромкой со стороны этого элемента — это и есть
    // запас прокрутки в его сторону. Нет запаса → не затемняем (элемент «упёрся»).
    //
    // Хвостовой конец сдвигается влево на суммарное ужатие всех видимых слотов, так
    // что замеренный (ужатый) запас зависит от приме′няемого масштаба — это и была
    // петля. `scaledHidden + totalShrink` равно НАТУРАЛЬНОМУ запасу (масштаб
    // сокращается), поэтому хвостовой `sideFactor` от ужатия больше не зависит.
    // Лидирующий конец привязан к якорю прокрутки (offset элемента 0), его ужатие
    // не двигает — там поправка не нужна.
    val hiddenOnSide =
        if (itemCenter >= viewportCenter) {
            layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == layoutInfo.totalItemsCount - 1 }
                ?.let {
                    val scaledHidden = (it.offset + it.size - layoutInfo.viewportEndOffset).toFloat()
                    (scaledHidden + totalShrink(layoutInfo, naturalSizes)).coerceAtLeast(0f)
                }
                ?: falloff
        } else {
            layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == 0 }
                ?.let { (layoutInfo.viewportStartOffset - it.offset).coerceAtLeast(0).toFloat() }
                ?: falloff
        }
    val sideFactor = (hiddenOnSide / falloff).coerceIn(0f, 1f)
    return eased * sideFactor
}

/**
 * Суммарное ужатие следов по главной оси (px, `Σ(натуральный − текущий)`) видимых
 * элементов с индексом строго меньше [beforeIndex]. На столько правее лежал бы
 * элемент [beforeIndex] при натуральных размерах: прибавив это к его замеренному
 * `offset`, получаем натуральный (не зависящий от масштаба) центр. `0`, если
 * [naturalSizes] не передан или предыдущие слоты ещё не измерены.
 */
private fun shrinkBefore(
    layoutInfo: LazyListLayoutInfo,
    naturalSizes: Map<Int, Int>?,
    beforeIndex: Int,
): Float {
    if (naturalSizes == null) return 0f
    var sum = 0f
    layoutInfo.visibleItemsInfo.forEach { info ->
        if (info.index < beforeIndex) {
            val natural = naturalSizes[info.index] ?: info.size
            sum += (natural - info.size).coerceAtLeast(0)
        }
    }
    return sum
}

/**
 * Суммарное ужатие следов ВСЕХ видимых элементов по главной оси, px. Прибавленная
 * к замеренному хвостовому запасу прокрутки, восстанавливает натуральную длину
 * контента, делая затухание независимым от масштаба (см. [wheelItemFalloff]).
 */
private fun totalShrink(
    layoutInfo: LazyListLayoutInfo,
    naturalSizes: Map<Int, Int>?,
): Float = shrinkBefore(layoutInfo, naturalSizes, Int.MAX_VALUE)

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
    // Кэш натуральных размеров слотов: хвостовой запас прокрутки считается от
    // натуральной длины, чтобы ужатие следа не запускало петлю дрожания (см. wheelItem).
    val naturalSizes = remember { mutableMapOf<Int, Int>() }
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
                    // Масштаб/альфа + ужатый след по главной оси (соседи
                    // подтягиваются). report-модификатор последним: индикатор
                    // отслеживает фактические (уже ужатые) границы элемента.
                    .wheelItem(state, index, orientation, falloffPx, minScale, minAlpha, edgeBand, naturalSizes)
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
