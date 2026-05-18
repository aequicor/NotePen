package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.applyAlpha
import ru.kyamshanov.notepen.annotation.domain.model.applyMode
import ru.kyamshanov.notepen.annotation.domain.model.applyPreset
import ru.kyamshanov.notepen.annotation.domain.model.applyShape
import ru.kyamshanov.notepen.annotation.domain.model.applySize
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Greedy left-to-right layout decision for adaptive settings rows.
 *
 * For each slot, determines whether it fits inline (true) or must collapse to
 * an icon-button (false). Budget starts at `maxWidth - paddingPx` and is
 * consumed slot by slot; a gap is counted before each slot except the first.
 * A collapsing slot consumes only `iconButtonWidthPx` instead of its natural width.
 */
internal fun greedyFit(
    naturalWidths: List<Int>,
    maxWidth: Int,
    gapPx: Int,
    paddingPx: Int,
    iconButtonWidthPx: Int,
): BooleanArray {
    val fits = BooleanArray(naturalWidths.size)
    var budget = maxWidth - paddingPx
    naturalWidths.forEachIndexed { i, w ->
        val gap = if (i > 0) gapPx else 0
        if (budget >= w + gap) {
            fits[i] = true
            budget -= w + gap
        } else {
            fits[i] = false
            budget -= iconButtonWidthPx + gap
        }
    }
    return fits
}

/**
 * The inner settings controls row — dispatches to the appropriate settings row
 * composable based on [toolMode]. Has no container (no Surface, no animation);
 * used both by [ToolSettingsFloatingPanel] (landscape) and [PortraitTopAirbar].
 */
@Composable
internal fun ToolSettingsContent(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    expandDownward: Boolean,
) {
    when (toolMode) {
        ToolMode.PEN -> PenSettingsRow(
            settings = penSettings,
            onChange = onPenSettingsChange,
            expandDownward = expandDownward,
        )
        ToolMode.MARKER -> MarkerSettingsRow(
            settings = markerSettings,
            onChange = onMarkerSettingsChange,
            expandDownward = expandDownward,
        )
        ToolMode.ERASER -> EraserSettingsRow(
            settings = eraserSettings,
            onChange = onEraserSettingsChange,
            expandDownward = expandDownward,
        )
        ToolMode.NONE -> Unit
    }
}

/**
 * Floating "glass" settings panel docked at the bottom-center of the screen.
 *
 * Visibility — driven by [toolMode]:
 *  - [ToolMode.PEN]    → horizontal row of pen controls (thickness slider, alpha
 *                         slider, color presets) is shown.
 *  - [ToolMode.ERASER] → horizontal row of eraser controls (shape chips, size
 *                         slider) is shown.
 *  - [ToolMode.NONE]   → the entire panel slides out / fades out.
 *
 * Animation — `slideInVertically + fadeIn` on appear, `slideOutVertically +
 * fadeOut` on hide. The panel slides from below (positive initial offset).
 *
 * Glass effect — semi-transparent `surface` with a subtle outline border and
 * tonal elevation. Compose-MP common surface has no portable blur primitive;
 * the alpha + outline reads as glass on both Android and JVM/Desktop.
 *
 * Verifies the rework requested at Step 6 5.6 CHECKPOINT (separate floating
 * panel, BottomCenter, single horizontal strip).
 */
@Composable
fun ToolSettingsFloatingPanel(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    modifier: Modifier = Modifier,
    atTop: Boolean = false,
) {
    val visible = toolMode == ToolMode.PEN || toolMode == ToolMode.MARKER || toolMode == ToolMode.ERASER

    val insetsModifier = if (atTop) {
        Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = PANEL_BOTTOM_PADDING)
    } else {
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = PANEL_BOTTOM_PADDING)
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { if (atTop) -it else it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { if (atTop) -it else it }) + fadeOut(),
        modifier = modifier.then(insetsModifier),
    ) {
        Surface(
            shape = RoundedCornerShape(PANEL_CORNER_RADIUS),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = PANEL_GLASS_ALPHA),
            tonalElevation = PANEL_TONAL_ELEVATION,
            border = BorderStroke(
                width = PANEL_BORDER_WIDTH,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = PANEL_BORDER_ALPHA),
            ),
        ) {
            // The Surface stays mounted while AnimatedVisibility plays the exit
            // animation; ToolSettingsContent renders nothing for NONE (brief fadeOut).
            ToolSettingsContent(
                toolMode = toolMode,
                penSettings = penSettings,
                onPenSettingsChange = onPenSettingsChange,
                markerSettings = markerSettings,
                onMarkerSettingsChange = onMarkerSettingsChange,
                eraserSettings = eraserSettings,
                onEraserSettingsChange = onEraserSettingsChange,
                expandDownward = atTop,
            )
        }
    }
}

@Composable
private fun PenSettingsRow(
    settings: PenSettings,
    onChange: (PenSettings) -> Unit,
    expandDownward: Boolean,
) {
    AdaptiveSettingsRow(
        expandDownward = expandDownward,
        slots = listOf(
            SlotItem(
                icon = Icons.Default.LineWeight,
                contentDescription = "Толщина",
                label = "Толщина",
                content = { sliderWidth ->
                    // Defect D: thumbTrackGapSize=0 keeps track visible at min/max.
                    // Defect E: BasicTextField lets user type the value directly.
                    SliderWithValueField(
                        value = settings.strokeWidth,
                        onValueChange = { onChange(settings.applyStrokeWidth(it)) },
                        valueRange = PenSettings.MIN_STROKE_WIDTH..PenSettings.MAX_STROKE_WIDTH,
                        formatDisplay = { it.roundToInt().toString() },
                        parseInput = { it.trim().toIntOrNull()?.toFloat() },
                        suffix = " dp",
                        sliderWidth = sliderWidth,
                    )
                },
            ),
            SlotItem(
                icon = Icons.Default.Opacity,
                contentDescription = "Прозрачность",
                label = "Прозрачность",
                content = { sliderWidth ->
                    SliderWithValueField(
                        value = settings.alpha,
                        onValueChange = { onChange(settings.applyAlpha(it)) },
                        valueRange = 0f..1f,
                        formatDisplay = { (it * 100f).roundToInt().toString() },
                        parseInput = { it.trim().trimEnd('%').trim().toIntOrNull()?.div(100f) },
                        suffix = "%",
                        sliderWidth = sliderWidth,
                    )
                },
            ),
            SlotItem(
                icon = Icons.Default.Palette,
                contentDescription = "Цвет",
                label = null,
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(PRESET_GAP)) {
                        PenSettings.PRESET_COLORS.forEach { presetArgb ->
                            ColorPresetDot(
                                presetArgb = presetArgb,
                                selected = (presetArgb and 0x00FFFFFFL) == (settings.colorArgb and 0x00FFFFFFL),
                                onClick = { onChange(settings.applyPreset(presetArgb)) },
                            )
                        }
                    }
                },
            ),
        ),
    )
}

@Composable
private fun ColorPresetDot(
    presetArgb: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Spacer(
        modifier = Modifier
            .size(PRESET_SIZE)
            .clip(CircleShape)
            .background(Color(presetArgb.toInt()))
            .border(
                width = if (selected) PRESET_BORDER_SELECTED else PRESET_BORDER_DEFAULT,
                color = borderColor,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun MarkerSettingsRow(
    settings: MarkerSettings,
    onChange: (MarkerSettings) -> Unit,
    expandDownward: Boolean,
) {
    AdaptiveSettingsRow(
        expandDownward = expandDownward,
        slots = listOf(
            SlotItem(
                icon = Icons.Default.LineWeight,
                contentDescription = "Толщина",
                label = "Толщина",
                content = { sliderWidth ->
                    SliderWithValueField(
                        value = settings.strokeWidth,
                        onValueChange = { onChange(settings.applyStrokeWidth(it)) },
                        valueRange = MarkerSettings.MIN_STROKE_WIDTH..MarkerSettings.MAX_STROKE_WIDTH,
                        formatDisplay = { it.roundToInt().toString() },
                        parseInput = { it.trim().toIntOrNull()?.toFloat() },
                        suffix = " dp",
                        sliderWidth = sliderWidth,
                    )
                },
            ),
            SlotItem(
                icon = Icons.Default.Palette,
                contentDescription = "Цвет",
                label = null,
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(PRESET_GAP)) {
                        MarkerSettings.PRESET_COLORS.forEach { presetArgb ->
                            ColorPresetDot(
                                presetArgb = presetArgb,
                                selected = presetArgb == settings.colorArgb,
                                onClick = { onChange(settings.applyPreset(presetArgb)) },
                            )
                        }
                    }
                },
            ),
        ),
    )
}

@Composable
private fun EraserSettingsRow(
    settings: EraserSettings,
    onChange: (EraserSettings) -> Unit,
    expandDownward: Boolean,
) {
    AdaptiveSettingsRow(
        expandDownward = expandDownward,
        slots = listOf(
            SlotItem(
                icon = Icons.Default.Category,
                contentDescription = "Режим",
                label = "Режим",
                content = {
                    FilterChip(
                        selected = settings.mode == EraserMode.POINT,
                        onClick = { onChange(settings.applyMode(EraserMode.POINT)) },
                        label = { Text("Точки") },
                        shape = CircleShape,
                        colors = chipColors(),
                    )
                    FilterChip(
                        selected = settings.mode == EraserMode.OBJECT,
                        onClick = { onChange(settings.applyMode(EraserMode.OBJECT)) },
                        label = { Text("Штрих") },
                        shape = CircleShape,
                        colors = chipColors(),
                    )
                },
            ),
            SlotItem(
                icon = Icons.Default.Category,
                contentDescription = "Форма",
                label = "Форма",
                content = {
                    FilterChip(
                        selected = settings.shape == EraserShape.CIRCLE,
                        onClick = { onChange(settings.applyShape(EraserShape.CIRCLE)) },
                        label = { Text("Круг") },
                        shape = CircleShape,
                        colors = chipColors(),
                    )
                    FilterChip(
                        selected = settings.shape == EraserShape.SQUARE,
                        onClick = { onChange(settings.applyShape(EraserShape.SQUARE)) },
                        label = { Text("Квадрат") },
                        shape = CircleShape,
                        colors = chipColors(),
                    )
                },
            ),
            SlotItem(
                icon = Icons.Default.PhotoSizeSelectLarge,
                contentDescription = "Размер",
                label = "Размер",
                content = { sliderWidth ->
                    // sizeNormalized ∈ [0.01..0.20] displayed as integer percent (1..20).
                    SliderWithValueField(
                        value = settings.sizeNormalized,
                        onValueChange = { onChange(settings.applySize(it)) },
                        valueRange = EraserSettings.MIN_SIZE_NORMALIZED..EraserSettings.MAX_SIZE_NORMALIZED,
                        formatDisplay = { (it * 100f).roundToInt().toString() },
                        parseInput = { it.trim().trimEnd('%').trim().toIntOrNull()?.div(100f) },
                        suffix = "%",
                        sliderWidth = sliderWidth,
                    )
                },
            ),
        ),
    )
}

// SlotItem.content receives the resolved slider width so SliderWithValueField
// can be rendered at the correct size in both the inline and vertical-expand paths.
private class SlotItem(
    val icon: ImageVector,
    val contentDescription: String,
    val label: String?,
    val content: @Composable (sliderWidth: Dp) -> Unit,
)

// Wrapping in Row is required so the whole slot is a SINGLE layout node.
// SubcomposeLayout's measure pass calls .first() on the subcomposed measurables;
// without the Row wrapper, label Text and slot.content() would be separate nodes
// and only the first (label) would be measured, producing incorrect natural widths.
@Composable
private fun FullSlotContent(slot: SlotItem, sliderWidth: Dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PANEL_INNER_GAP),
    ) {
        if (slot.label != null) {
            Text(
                text = slot.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        slot.content(sliderWidth)
    }
}

@Composable
private fun CollapsedSlotContent(
    slot: SlotItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.size(ADAPTIVE_ICON_BUTTON_SIZE),
    ) {
        Icon(
            imageVector = slot.icon,
            contentDescription = slot.contentDescription,
            tint = if (isExpanded)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * Adaptive horizontal row that uses [SubcomposeLayout] to measure each slot's
 * natural width in two passes, then uses [greedyFit] to decide rendering mode:
 *
 * - **Full**: slot fits at [SLIDER_WIDTH] → label + content inline.
 * - **Compressed**: slot fits at [ADAPTIVE_MIN_SLIDER_WIDTH] but not at full
 *   width → label + content inline with a narrower slider.
 * - **Collapsed**: slot doesn't fit even compressed → [IconButton]. Tapping
 *   opens the slot content **vertically above** the main row (panel grows
 *   upward because it is anchored at BottomCenter); only one slot at a time.
 */
@Composable
private fun AdaptiveSettingsRow(
    slots: List<SlotItem>,
    expandDownward: Boolean,
    modifier: Modifier = Modifier,
) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    val expansionAlignment = if (expandDownward) Alignment.Top else Alignment.Bottom
    val expansionPanel: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = expandedIndex != null,
            enter = expandVertically(expandFrom = expansionAlignment) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = expansionAlignment) + fadeOut(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PANEL_INNER_GAP),
                modifier = Modifier.padding(
                    horizontal = PANEL_HORIZONTAL_PADDING,
                    vertical = PANEL_VERTICAL_PADDING,
                ),
            ) {
                val idx = expandedIndex
                if (idx != null) slots[idx].content(SLIDER_WIDTH)
            }
        }
    }

    val mainRow: @Composable () -> Unit = {
        SubcomposeLayout { constraints ->
            val gapPx = PANEL_INNER_GAP.roundToPx()
            val paddingPx = (PANEL_HORIZONTAL_PADDING * 2).roundToPx()
            val iconButtonWidthPx = ADAPTIVE_ICON_BUTTON_SIZE.roundToPx()

            // Measure every slot at both widths up-front.
            val naturalWidths = slots.indices.map { i ->
                subcompose("measure_nat_$i") { FullSlotContent(slots[i], SLIDER_WIDTH) }
                    .first().measure(Constraints()).width
            }
            val compressedWidths = slots.indices.map { i ->
                subcompose("measure_min_$i") { FullSlotContent(slots[i], ADAPTIVE_MIN_SLIDER_WIDTH) }
                    .first().measure(Constraints()).width
            }
            // Baseline: all slots start as icon-buttons — always fits regardless of width.
            // Budget is the space left after reserving one icon-button per slot + gaps.
            // Phase 1: promote icon → compressed inline (80 dp slider) left-to-right.
            // Phase 2: promote compressed → full inline (140 dp slider) left-to-right.
            // Icons are NEVER collapsed — only sliders are demoted.
            val gapCount = (slots.size - 1).coerceAtLeast(0)
            var budget = constraints.maxWidth - paddingPx - slots.size * iconButtonWidthPx - gapCount * gapPx
            val resolvedArr = arrayOfNulls<Dp>(slots.size)
            for (i in slots.indices) {
                val extra = compressedWidths[i] - iconButtonWidthPx
                if (budget >= extra) { resolvedArr[i] = ADAPTIVE_MIN_SLIDER_WIDTH; budget -= extra }
            }
            for (i in slots.indices) {
                if (resolvedArr[i] == ADAPTIVE_MIN_SLIDER_WIDTH) {
                    val extra = naturalWidths[i] - compressedWidths[i]
                    if (budget >= extra) { resolvedArr[i] = SLIDER_WIDTH; budget -= extra }
                }
            }
            val resolvedWidths: List<Dp?> = resolvedArr.toList()

            val placeable = subcompose("row") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PANEL_INNER_GAP),
                    modifier = Modifier.padding(
                        horizontal = PANEL_HORIZONTAL_PADDING,
                        vertical = PANEL_VERTICAL_PADDING,
                    ),
                ) {
                    slots.forEachIndexed { i, slot ->
                        val sw = resolvedWidths[i]
                        if (sw != null) {
                            FullSlotContent(slot, sw)
                        } else {
                            CollapsedSlotContent(
                                slot = slot,
                                isExpanded = expandedIndex == i,
                                onToggle = {
                                    expandedIndex = if (expandedIndex == i) null else i
                                },
                            )
                        }
                    }
                }
            }.first().measure(constraints.copy(minWidth = 0, minHeight = 0))

            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }
    }

    Column(modifier) {
        if (expandDownward) {
            mainRow()
            expansionPanel()
        } else {
            expansionPanel()
            mainRow()
        }
    }
}

/**
 * Slider + compact editable value field on the same horizontal strip.
 *
 * Track-rendering fix (Defect D): uses the slot overload of [Slider] with
 * `track = SliderDefaults.Track(state, thumbTrackGapSize = 0.dp,
 * drawStopIndicator = null)` so the inactive track runs the full width
 * regardless of thumb position. Material 3 1.8.x default leaves a small gap
 * between thumb and track which makes the track invisible when the thumb is
 * docked at min or max.
 *
 * Editable-value fix (Defect E): right of the slider sits a [BasicTextField]
 * (~52dp wide, [MaterialTheme.typography.bodySmall], thin outline). The user
 * may type a number; on Done / Enter the input is parsed via [parseInput] and
 * fed back through [onValueChange] — clamping is the caller's helper job
 * (`applyStrokeWidth` / `applyAlpha` / `applySize` all coerce in range).
 *
 * The text shown while the user has not edited follows [value] via a
 * `LaunchedEffect(value)`; once the user starts typing the field is the
 * source of truth until commit / focus loss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderWithValueField(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    formatDisplay: (Float) -> String,
    parseInput: (String) -> Float?,
    suffix: String,
    sliderWidth: Dp = SLIDER_WIDTH,
) {
    val sliderColors = SliderDefaults.colors(
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.width(sliderWidth),
        colors = sliderColors,
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = sliderColors,
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
    )

    val displayed = formatDisplay(value)
    var text by remember { mutableStateOf(displayed) }
    // Sync the text field back to `value` whenever the slider drives a change
    // from outside the field's own typing. Comparing against the formatted
    // representation prevents fights between rounding and the user's literal
    // input (e.g. "8" vs "8.000000001").
    LaunchedEffect(displayed) {
        if (text != displayed) text = displayed
    }

    val commit: () -> Unit = commit@{
        val parsed = parseInput(text) ?: run {
            // Reject unparseable input — restore the displayed value.
            text = displayed
            return@commit
        }
        onValueChange(parsed)
        // The caller's clamp may produce a different value; refresh the
        // text on the next recomposition via the LaunchedEffect above.
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
    // Defect F: BasicTextField's inner editable slot and the trailing suffix
    // Text were rendering with different baselines/line-heights, making the
    // "dp"/"%" suffix look like a misaligned superscript next to the digits.
    // Pin both to identical TextStyle with explicit fontSize == lineHeight
    // (zero extra leading) so the Row's CenterVertically lines them up
    // pixel-equal regardless of bodySmall theme tweaks across platforms.
    val textStyle = MaterialTheme.typography.bodySmall.copy(
        color = onSurface,
        fontSize = VALUE_FIELD_FONT_SIZE,
        lineHeight = VALUE_FIELD_FONT_SIZE,
        textAlign = TextAlign.Center,
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(VALUE_FIELD_WIDTH)
                .background(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(VALUE_FIELD_CORNER_RADIUS),
                )
                .border(
                    width = VALUE_FIELD_BORDER_WIDTH,
                    color = outline,
                    shape = RoundedCornerShape(VALUE_FIELD_CORNER_RADIUS),
                )
                .padding(horizontal = VALUE_FIELD_PADDING_H, vertical = VALUE_FIELD_PADDING_V),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = textStyle,
                cursorBrush = SolidColor(onSurface),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
            )
        }
        if (suffix.isNotEmpty()) {
            Text(
                text = suffix,
                style = textStyle,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedContainerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

private val ADAPTIVE_ICON_BUTTON_SIZE = 40.dp
private val ADAPTIVE_MIN_SLIDER_WIDTH = 80.dp
private val PANEL_CORNER_RADIUS = 24.dp
private val PANEL_TONAL_ELEVATION = 0.dp
private val PANEL_SHADOW_ELEVATION = 4.dp
private val PANEL_BORDER_WIDTH = 1.dp
private val PANEL_BOTTOM_PADDING = 16.dp
private val PANEL_HORIZONTAL_PADDING = 16.dp
private val PANEL_VERTICAL_PADDING = 4.dp
private val PANEL_INNER_GAP = 12.dp
private val SLIDER_WIDTH = 140.dp
private val PRESET_GAP = 8.dp
private val PRESET_SIZE = 28.dp
private val PRESET_BORDER_DEFAULT = 1.dp
private val PRESET_BORDER_SELECTED = 2.dp
private val VALUE_FIELD_WIDTH = 52.dp
private val VALUE_FIELD_CORNER_RADIUS = 6.dp
private val VALUE_FIELD_BORDER_WIDTH = 1.dp
private val VALUE_FIELD_PADDING_H = 6.dp
private val VALUE_FIELD_PADDING_V = 2.dp
private val VALUE_FIELD_FONT_SIZE = 12.sp
private const val PANEL_GLASS_ALPHA = 0.88f
private const val PANEL_BORDER_ALPHA = 0.5f
