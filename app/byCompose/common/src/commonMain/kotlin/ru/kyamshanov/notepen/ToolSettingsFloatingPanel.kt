package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.applyAlpha
import ru.kyamshanov.notepen.annotation.domain.model.applyMode
import ru.kyamshanov.notepen.annotation.domain.model.applyPreset
import ru.kyamshanov.notepen.annotation.domain.model.applyShape
import ru.kyamshanov.notepen.annotation.domain.model.applySize
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth
import ru.kyamshanov.notepen.ui.glass.GlassSurface
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
 * Floating "glass" settings panel for the active tool.
 *
 * Always renders a [CollapsibleSettingsRail] inside a [GlassSurface]:
 *  - `vertical = true`  → vertical rail (landscape left side); expansion extends
 *    DOWN along the rail axis and the expanded slot uses a vertical slider /
 *    vertical color column.
 *  - `vertical = false` → horizontal rail (portrait, centered); expansion
 *    extends to the RIGHT along the rail axis with a horizontal slider /
 *    horizontal color row.
 *
 * Visibility — driven by [toolMode]: any of PEN/MARKER/ERASER shows the panel,
 * NONE hides it with a slide+fade exit.
 *
 * [applyInsets] — when true (default) the panel pads itself around system bars
 * (status or navigation depending on [atTop]); when false, the caller is
 * responsible for positioning.
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
    vertical: Boolean = false,
    applyInsets: Boolean = true,
) {
    val visible = toolMode == ToolMode.PEN || toolMode == ToolMode.MARKER || toolMode == ToolMode.ERASER

    val finalModifier = when {
        !applyInsets || vertical -> modifier
        atTop -> modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = PANEL_OUTER_PADDING)
        else -> modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = PANEL_OUTER_PADDING)
    }

    val slots: List<SlotItem> = when (toolMode) {
        ToolMode.PEN -> penSlots(penSettings, onPenSettingsChange)
        ToolMode.MARKER -> markerSlots(markerSettings, onMarkerSettingsChange)
        ToolMode.ERASER -> eraserSlots(eraserSettings, onEraserSettingsChange)
        ToolMode.NONE -> emptyList()
    }

    AnimatedVisibility(
        visible = visible,
        enter = if (vertical) slideInHorizontally { -it } + fadeIn()
                else slideInVertically(initialOffsetY = { if (atTop) -it else it }) + fadeIn(),
        exit = if (vertical) slideOutHorizontally { -it } + fadeOut()
               else slideOutVertically(targetOffsetY = { if (atTop) -it else it }) + fadeOut(),
        modifier = finalModifier,
    ) {
        GlassSurface(tint = MaterialTheme.colorScheme.secondaryContainer) {
            CollapsibleSettingsRail(
                slots = slots,
                orientation = if (vertical) RailOrientation.VERTICAL else RailOrientation.HORIZONTAL,
                resetKey = toolMode,
            )
        }
    }
}

private enum class RailOrientation { VERTICAL, HORIZONTAL }

/**
 * Inline tool settings without outer surface decoration — for embedding into
 * an existing container (e.g. [PortraitTopAirbar]).
 *
 * Icon buttons are always laid out horizontally. When [expandDownward] is true
 * the expansion panel opens below the icons; otherwise it opens to the right.
 */
@Composable
fun ToolSettingsContent(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    expandDownward: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val slots: List<SlotItem> = when (toolMode) {
        ToolMode.PEN -> penSlots(penSettings, onPenSettingsChange)
        ToolMode.MARKER -> markerSlots(markerSettings, onMarkerSettingsChange)
        ToolMode.ERASER -> eraserSlots(eraserSettings, onEraserSettingsChange)
        ToolMode.NONE -> return
    }
    var expandedIndex by remember(toolMode) { mutableStateOf<Int?>(null) }

    val iconStrip: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RAIL_ITEM_GAP),
            modifier = Modifier.padding(
                horizontal = RAIL_STRIP_PADDING_H,
                vertical = RAIL_STRIP_PADDING_V,
            ),
        ) {
            slots.forEachIndexed { i, slot ->
                SlotIconButton(slot, expandedIndex == i) {
                    expandedIndex = if (expandedIndex == i) null else i
                }
            }
        }
    }

    val expansionContent: @Composable () -> Unit = {
        val idx = expandedIndex
        if (idx != null && idx in slots.indices) {
            Box(
                modifier = Modifier.padding(
                    horizontal = PANEL_EXPANSION_PADDING_H,
                    vertical = PANEL_EXPANSION_PADDING_V,
                ),
            ) {
                val orientation = if (expandDownward) RailOrientation.VERTICAL else RailOrientation.HORIZONTAL
                slots[idx].content(orientation)
            }
        }
    }

    if (expandDownward) {
        Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            iconStrip()
            AnimatedVisibility(
                visible = expandedIndex != null,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) { expansionContent() }
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            iconStrip()
            AnimatedVisibility(
                visible = expandedIndex != null,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
            ) { expansionContent() }
        }
    }
}

private class SlotItem(
    val icon: ImageVector,
    val contentDescription: String,
    /** Renders the expanded content for the requested [RailOrientation]. */
    val content: @Composable (RailOrientation) -> Unit,
)

/**
 * Rail of icon buttons (one per slot). Tapping a button toggles an expansion
 * panel that extends along the rail axis — vertical rail → expand down with
 * vertical slider; horizontal rail → expand right with horizontal slider.
 *
 * Only one slot is expanded at a time. Tapping the active slot's icon collapses
 * the panel; switching to a different slot swaps the content. [resetKey]
 * collapses the panel when it changes — caller should pass the tool identity
 * so switching tools always closes any open expansion. We deliberately do NOT
 * key on [slots] because settings changes (slider drag) rebuild the slot list
 * and would otherwise snap the panel shut mid-interaction.
 */
@Composable
private fun CollapsibleSettingsRail(
    slots: List<SlotItem>,
    orientation: RailOrientation,
    resetKey: Any?,
) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(resetKey) { expandedIndex = null }

    val stripModifier = Modifier.padding(
        horizontal = RAIL_STRIP_PADDING_H,
        vertical = RAIL_STRIP_PADDING_V,
    )

    val iconStrip: @Composable () -> Unit = {
        val onToggle: (Int) -> Unit = { i ->
            expandedIndex = if (expandedIndex == i) null else i
        }
        when (orientation) {
            RailOrientation.VERTICAL -> Column(
                modifier = stripModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(RAIL_ITEM_GAP),
            ) {
                slots.forEachIndexed { i, slot ->
                    SlotIconButton(slot, expandedIndex == i) { onToggle(i) }
                }
            }
            RailOrientation.HORIZONTAL -> Row(
                modifier = stripModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RAIL_ITEM_GAP),
            ) {
                slots.forEachIndexed { i, slot ->
                    SlotIconButton(slot, expandedIndex == i) { onToggle(i) }
                }
            }
        }
    }

    val expansionContent: @Composable () -> Unit = {
        val idx = expandedIndex
        if (idx != null && idx in slots.indices) {
            Box(
                modifier = Modifier.padding(
                    horizontal = PANEL_EXPANSION_PADDING_H,
                    vertical = PANEL_EXPANSION_PADDING_V,
                ),
            ) {
                slots[idx].content(orientation)
            }
        }
    }

    when (orientation) {
        RailOrientation.VERTICAL -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            iconStrip()
            AnimatedVisibility(
                visible = expandedIndex != null,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) { expansionContent() }
        }
        RailOrientation.HORIZONTAL -> Row(verticalAlignment = Alignment.CenterVertically) {
            iconStrip()
            AnimatedVisibility(
                visible = expandedIndex != null,
                enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
            ) { expansionContent() }
        }
    }
}

@Composable
private fun SlotIconButton(
    slot: SlotItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.size(RAIL_ICON_BUTTON_SIZE),
    ) {
        Icon(
            imageVector = slot.icon,
            contentDescription = slot.contentDescription,
            tint = if (isExpanded) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/* ---------------- slot factories ---------------- */

private fun penSlots(
    settings: PenSettings,
    onChange: (PenSettings) -> Unit,
): List<SlotItem> = listOf(
    SlotItem(
        icon = Icons.Default.Palette,
        contentDescription = "Цвет",
        content = { orientation ->
            ColorPresets(
                presets = PenSettings.PRESET_COLORS,
                isSelected = { (it and 0x00FFFFFFL) == (settings.colorArgb and 0x00FFFFFFL) },
                onPick = { onChange(settings.applyPreset(it)) },
                orientation = orientation,
            )
        },
    ),
    SlotItem(
        icon = Icons.Default.LineWeight,
        contentDescription = "Толщина",
        content = { orientation ->
            OrientedSlider(
                orientation = orientation,
                value = settings.strokeWidth,
                onValueChange = { onChange(settings.applyStrokeWidth(it)) },
                valueRange = PenSettings.MIN_STROKE_WIDTH..PenSettings.MAX_STROKE_WIDTH,
                formatDisplay = { it.roundToInt().toString() },
                parseInput = { it.trim().toIntOrNull()?.toFloat() },
                suffix = " dp",
            )
        },
    ),
    SlotItem(
        icon = Icons.Default.Opacity,
        contentDescription = "Прозрачность",
        content = { orientation ->
            OrientedSlider(
                orientation = orientation,
                value = settings.alpha,
                onValueChange = { onChange(settings.applyAlpha(it)) },
                valueRange = 0f..1f,
                formatDisplay = { (it * 100f).roundToInt().toString() },
                parseInput = { it.trim().trimEnd('%').trim().toIntOrNull()?.div(100f) },
                suffix = "%",
            )
        },
    ),
)

private fun markerSlots(
    settings: MarkerSettings,
    onChange: (MarkerSettings) -> Unit,
): List<SlotItem> = listOf(
    SlotItem(
        icon = Icons.Default.Palette,
        contentDescription = "Цвет",
        content = { orientation ->
            ColorPresets(
                presets = MarkerSettings.PRESET_COLORS,
                isSelected = { it == settings.colorArgb },
                onPick = { onChange(settings.applyPreset(it)) },
                orientation = orientation,
            )
        },
    ),
    SlotItem(
        icon = Icons.Default.LineWeight,
        contentDescription = "Толщина",
        content = { orientation ->
            OrientedSlider(
                orientation = orientation,
                value = settings.strokeWidth,
                onValueChange = { onChange(settings.applyStrokeWidth(it)) },
                valueRange = MarkerSettings.MIN_STROKE_WIDTH..MarkerSettings.MAX_STROKE_WIDTH,
                formatDisplay = { it.roundToInt().toString() },
                parseInput = { it.trim().toIntOrNull()?.toFloat() },
                suffix = " dp",
            )
        },
    ),
)

private fun eraserSlots(
    settings: EraserSettings,
    onChange: (EraserSettings) -> Unit,
): List<SlotItem> = listOf(
    SlotItem(
        icon = Icons.Default.Category,
        contentDescription = "Режим",
        content = { orientation ->
            OrientedChipGroup(orientation = orientation) {
                EraserChip(
                    selected = settings.mode == EraserMode.POINT,
                    label = "Точки",
                    onClick = { onChange(settings.applyMode(EraserMode.POINT)) },
                )
                EraserChip(
                    selected = settings.mode == EraserMode.OBJECT,
                    label = "Штрих",
                    onClick = { onChange(settings.applyMode(EraserMode.OBJECT)) },
                )
            }
        },
    ),
    SlotItem(
        icon = Icons.Default.RadioButtonUnchecked,
        contentDescription = "Форма",
        content = { orientation ->
            OrientedChipGroup(orientation = orientation) {
                EraserChip(
                    selected = settings.shape == EraserShape.CIRCLE,
                    label = "Круг",
                    onClick = { onChange(settings.applyShape(EraserShape.CIRCLE)) },
                )
                EraserChip(
                    selected = settings.shape == EraserShape.SQUARE,
                    label = "Квадрат",
                    onClick = { onChange(settings.applyShape(EraserShape.SQUARE)) },
                )
            }
        },
    ),
    SlotItem(
        icon = Icons.Default.PhotoSizeSelectLarge,
        contentDescription = "Размер",
        content = { orientation ->
            OrientedSlider(
                orientation = orientation,
                value = settings.sizeNormalized,
                onValueChange = { onChange(settings.applySize(it)) },
                valueRange = EraserSettings.MIN_SIZE_NORMALIZED..EraserSettings.MAX_SIZE_NORMALIZED,
                formatDisplay = { (it * 100f).roundToInt().toString() },
                parseInput = { it.trim().trimEnd('%').trim().toIntOrNull()?.div(100f) },
                suffix = "%",
            )
        },
    ),
)

/* ---------------- oriented expanded-content widgets ---------------- */

@Composable
private fun ColorPresets(
    presets: List<Long>,
    isSelected: (Long) -> Boolean,
    onPick: (Long) -> Unit,
    orientation: RailOrientation,
) {
    when (orientation) {
        RailOrientation.HORIZONTAL -> Row(horizontalArrangement = Arrangement.spacedBy(PRESET_GAP)) {
            presets.forEach { ColorPresetDot(it, isSelected(it)) { onPick(it) } }
        }
        RailOrientation.VERTICAL -> Column(
            verticalArrangement = Arrangement.spacedBy(PRESET_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            presets.forEach { ColorPresetDot(it, isSelected(it)) { onPick(it) } }
        }
    }
}

@Composable
private fun ColorPresetDot(
    presetArgb: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.outlineVariant
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
private fun OrientedChipGroup(
    orientation: RailOrientation,
    content: @Composable () -> Unit,
) {
    when (orientation) {
        RailOrientation.HORIZONTAL -> Row(
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
        RailOrientation.VERTICAL -> Column(
            verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun EraserChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun OrientedSlider(
    orientation: RailOrientation,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    formatDisplay: (Float) -> String,
    parseInput: (String) -> Float?,
    suffix: String,
) {
    when (orientation) {
        RailOrientation.HORIZONTAL -> Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalAdjustableSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                width = SLIDER_LENGTH,
            )
            Spacer(Modifier.width(VALUE_FIELD_INLINE_GAP))
            ValueField(value, onValueChange, formatDisplay, parseInput, suffix)
        }
        RailOrientation.VERTICAL -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(VALUE_FIELD_INLINE_GAP),
        ) {
            VerticalAdjustableSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                length = SLIDER_LENGTH,
            )
            ValueField(value, onValueChange, formatDisplay, parseInput, suffix)
        }
    }
}

/**
 * Standard horizontal Material 3 slider with the inactive track always visible
 * end-to-end (Material 3 1.8.x leaves a gap between thumb and track at min/max
 * extremes by default).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorizontalAdjustableSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    width: Dp,
) {
    val colors = SliderDefaults.colors(
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.width(width),
        colors = colors,
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                colors = colors,
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
            )
        },
    )
}

/**
 * Vertical slider built by rotating Material 3 [Slider] -90° around its
 * top-left corner and swapping width/height in a custom [layout] modifier so
 * the parent sees a [VERT_SLIDER_TRACK_BREADTH]-wide × [length]-tall node.
 * Bottom = min, top = max — natural "more = up" mental model. Material 3 has
 * no first-class vertical slider; this rotation pattern is the standard
 * workaround until one ships.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerticalAdjustableSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    length: Dp,
) {
    val colors = SliderDefaults.colors(
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
    )
    Box(
        modifier = Modifier.size(width = VERT_SLIDER_TRACK_BREADTH, height = length),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = colors,
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                        ),
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(-placeable.width, 0)
                    }
                },
        )
    }
}

/**
 * Compact editable value field. Width pinned to [VALUE_FIELD_WIDTH] so the
 * vertical and horizontal layouts have a predictable, identical visual size.
 * Font size = line height (no extra leading) so the suffix Text aligns
 * pixel-perfectly with the BasicTextField across platforms.
 */
@Composable
private fun ValueField(
    value: Float,
    onValueChange: (Float) -> Unit,
    formatDisplay: (Float) -> String,
    parseInput: (String) -> Float?,
    suffix: String,
) {
    val displayed = formatDisplay(value)
    var text by remember { mutableStateOf(displayed) }
    LaunchedEffect(displayed) { if (text != displayed) text = displayed }
    val commit: () -> Unit = commit@{
        val parsed = parseInput(text) ?: run {
            text = displayed
            return@commit
        }
        onValueChange(parsed)
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outlineVariant
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
            Text(suffix, style = textStyle, modifier = Modifier.padding(start = 2.dp))
        }
    }
}

private val PANEL_OUTER_PADDING = 16.dp
private val PANEL_EXPANSION_PADDING_H = 12.dp
private val PANEL_EXPANSION_PADDING_V = 8.dp
private val RAIL_STRIP_PADDING_H = 6.dp
private val RAIL_STRIP_PADDING_V = 6.dp
private val RAIL_ITEM_GAP = 4.dp
private val RAIL_ICON_BUTTON_SIZE = 40.dp
private val SLIDER_LENGTH = 140.dp
private val VERT_SLIDER_TRACK_BREADTH = 40.dp
private val PRESET_GAP = 8.dp
private val PRESET_SIZE = 28.dp
private val PRESET_BORDER_DEFAULT = 1.dp
private val PRESET_BORDER_SELECTED = 2.dp
private val CHIP_GAP = 6.dp
private val VALUE_FIELD_WIDTH = 52.dp
private val VALUE_FIELD_CORNER_RADIUS = 6.dp
private val VALUE_FIELD_BORDER_WIDTH = 1.dp
private val VALUE_FIELD_PADDING_H = 6.dp
private val VALUE_FIELD_PADDING_V = 2.dp
private val VALUE_FIELD_INLINE_GAP = 8.dp
private val VALUE_FIELD_FONT_SIZE = 12.sp
