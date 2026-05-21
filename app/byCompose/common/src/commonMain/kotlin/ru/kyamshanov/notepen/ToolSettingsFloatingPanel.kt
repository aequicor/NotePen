package ru.kyamshanov.notepen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size as CanvasSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.applyAlpha
import ru.kyamshanov.notepen.annotation.domain.model.applyMode
import ru.kyamshanov.notepen.annotation.domain.model.applyPreset
import ru.kyamshanov.notepen.annotation.domain.model.applyShape
import ru.kyamshanov.notepen.annotation.domain.model.applySize
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth
import ru.kyamshanov.notepen.annotation.domain.model.sliderPositionToStrokeWidth
import ru.kyamshanov.notepen.annotation.domain.model.strokeWidthToSliderPosition

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

internal enum class RailOrientation { VERTICAL, HORIZONTAL }

private class SlotItem(
    val icon: ImageVector,
    val contentDescription: String,
    /** Renders the expanded content for the requested [RailOrientation]. */
    val content: @Composable (RailOrientation) -> Unit,
)

@Composable
private fun SlotIconButton(
    slot: SlotItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(RAIL_ICON_BUTTON_SIZE),
    ) {
        Icon(
            imageVector = slot.icon,
            contentDescription = slot.contentDescription,
            tint = if (isExpanded) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Builds the settings slots for [toolMode] (empty list for [ToolMode.NONE]). */
private fun slotsFor(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
): List<SlotItem> = when (toolMode) {
    ToolMode.PEN -> penSlots(penSettings, onPenSettingsChange)
    ToolMode.MARKER -> markerSlots(markerSettings, onMarkerSettingsChange)
    ToolMode.ERASER -> eraserSlots(eraserSettings, onEraserSettingsChange)
    ToolMode.NONE -> emptyList()
}

/**
 * Compact strip of slot icon-buttons for the active tool's settings.
 *
 * Icons are laid out along the rail axis ([orientation]); the [expandedIndex]
 * slot is highlighted. Tapping a slot calls [onToggle] with its index — the
 * caller owns the expanded state so the expansion content can live in a sibling
 * surface (the landscape "budding" side rail, or the portrait below-bar strip).
 */
@Composable
internal fun ToolSettingsIconStrip(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    orientation: RailOrientation,
    expandedIndex: Int?,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
    expandedButtonModifier: Modifier = Modifier,
) {
    val slots = slotsFor(
        toolMode,
        penSettings, onPenSettingsChange,
        markerSettings, onMarkerSettingsChange,
        eraserSettings, onEraserSettingsChange,
    )
    val buttons: @Composable () -> Unit = {
        slots.forEachIndexed { i, slot ->
            SlotIconButton(
                slot = slot,
                isExpanded = expandedIndex == i,
                onToggle = { onToggle(i) },
                modifier = if (expandedIndex == i) expandedButtonModifier else Modifier,
            )
        }
    }
    when (orientation) {
        RailOrientation.VERTICAL -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RAIL_ITEM_GAP),
        ) { buttons() }
        RailOrientation.HORIZONTAL -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RAIL_ITEM_GAP),
        ) { buttons() }
    }
}

/**
 * Renders the expanded content (color picker / slider / chips) for the slot at
 * [index] of the active tool. No-op when [index] is out of range.
 */
@Composable
internal fun ToolSettingsExpansionContent(
    toolMode: ToolMode,
    penSettings: PenSettings,
    onPenSettingsChange: (PenSettings) -> Unit,
    markerSettings: MarkerSettings,
    onMarkerSettingsChange: (MarkerSettings) -> Unit,
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    orientation: RailOrientation,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val slots = slotsFor(
        toolMode,
        penSettings, onPenSettingsChange,
        markerSettings, onMarkerSettingsChange,
        eraserSettings, onEraserSettingsChange,
    )
    if (index in slots.indices) {
        Box(
            modifier = modifier.padding(
                horizontal = PANEL_EXPANSION_PADDING_H,
                vertical = PANEL_EXPANSION_PADDING_V,
            ),
        ) {
            slots[index].content(orientation)
        }
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
            StrokeWidthSlider(
                orientation = orientation,
                strokeWidth = settings.strokeWidth,
                min = PenSettings.MIN_STROKE_WIDTH,
                max = PenSettings.MAX_STROKE_WIDTH,
                onWidthChange = { onChange(settings.applyStrokeWidth(it)) },
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
            StrokeWidthSlider(
                orientation = orientation,
                strokeWidth = settings.strokeWidth,
                min = MarkerSettings.MIN_STROKE_WIDTH,
                max = MarkerSettings.MAX_STROKE_WIDTH,
                onWidthChange = { onChange(settings.applyStrokeWidth(it)) },
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

/**
 * Slider for stroke width that:
 * - operates on a perceptually-uniform log scale (equal-feeling steps at every position);
 * - reports/accepts width as a fraction of page width;
 * - shows mm in the value field (A4-width reference, 210 mm).
 *
 * Display & parsing in mm-on-A4 is purely UI sugar — the underlying value is the
 * page-width fraction, so it stays correct on any actual page format.
 */
@Composable
private fun StrokeWidthSlider(
    orientation: RailOrientation,
    strokeWidth: Float,
    min: Float,
    max: Float,
    onWidthChange: (Float) -> Unit,
) {
    OrientedSlider(
        orientation = orientation,
        value = strokeWidthToSliderPosition(strokeWidth, min, max),
        onValueChange = { t -> onWidthChange(sliderPositionToStrokeWidth(t, min, max)) },
        valueRange = 0f..1f,
    )
}

@Composable
private fun OrientedSlider(
    orientation: RailOrientation,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    when (orientation) {
        RailOrientation.HORIZONTAL -> HorizontalAdjustableSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            width = SLIDER_LENGTH,
        )
        RailOrientation.VERTICAL -> VerticalAdjustableSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            length = SLIDER_LENGTH,
        )
    }
}

/**
 * Standard horizontal Material 3 slider with the inactive track always visible
 * end-to-end.
 *
 * We use a custom track instead of [SliderDefaults.Track] because Material 3 renders
 * the active and inactive segments separately: at min/max the zero-length segment
 * simply disappears (a round-cap on a zero-length stroke draws nothing). Drawing a
 * full-width background first and then the active portion on top keeps both ends
 * visible at all positions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HorizontalAdjustableSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    width: Dp,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.width(width),
        colors = SliderDefaults.colors(
            thumbColor = activeColor,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
        ),
        track = { state ->
            val fraction = ((state.value - state.valueRange.start) /
                (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxWidth().height(SLIDER_TRACK_HEIGHT)) {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(color = inactiveColor, cornerRadius = r)
                if (fraction > 0f) {
                    drawRoundRect(
                        color = activeColor,
                        size = CanvasSize(size.width * fraction, size.height),
                        cornerRadius = r,
                    )
                }
            }
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
 *
 * Uses the same full-background custom track as [HorizontalAdjustableSlider]
 * so the inactive portion stays visible when the slider is at min or max.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerticalAdjustableSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    length: Dp,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
    Box(
        modifier = Modifier.size(width = VERT_SLIDER_TRACK_BREADTH, height = length),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = activeColor,
                activeTrackColor = activeColor,
                inactiveTrackColor = inactiveColor,
            ),
            track = { state ->
                val fraction = ((state.value - state.valueRange.start) /
                    (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(SLIDER_TRACK_HEIGHT)) {
                    val r = CornerRadius(size.height / 2f)
                    drawRoundRect(color = inactiveColor, cornerRadius = r)
                    if (fraction > 0f) {
                        drawRoundRect(
                            color = activeColor,
                            size = CanvasSize(size.width * fraction, size.height),
                            cornerRadius = r,
                        )
                    }
                }
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

private val PANEL_EXPANSION_PADDING_H = 12.dp
private val PANEL_EXPANSION_PADDING_V = 8.dp
private val RAIL_ITEM_GAP = 4.dp
private val RAIL_ICON_BUTTON_SIZE = 40.dp
private val SLIDER_LENGTH = 140.dp
private val SLIDER_TRACK_HEIGHT = 12.dp
private val VERT_SLIDER_TRACK_BREADTH = 40.dp
private val PRESET_GAP = 8.dp
private val PRESET_SIZE = 28.dp
private val PRESET_BORDER_DEFAULT = 1.dp
private val PRESET_BORDER_SELECTED = 2.dp
private val CHIP_GAP = 6.dp
