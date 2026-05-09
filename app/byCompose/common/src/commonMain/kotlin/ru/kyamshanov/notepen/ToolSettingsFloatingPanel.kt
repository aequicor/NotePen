package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
    eraserSettings: EraserSettings,
    onEraserSettingsChange: (EraserSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = toolMode == ToolMode.PEN || toolMode == ToolMode.ERASER

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = PANEL_BOTTOM_PADDING),
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
            // animation; we still render the body matching the *latest non-NONE*
            // toolMode to avoid a flicker on the way out.
            when (toolMode) {
                ToolMode.PEN -> PenSettingsRow(
                    settings = penSettings,
                    onChange = onPenSettingsChange,
                )

                ToolMode.ERASER -> EraserSettingsRow(
                    settings = eraserSettings,
                    onChange = onEraserSettingsChange,
                )

                ToolMode.NONE -> {
                    // Rendered briefly during exit animation; show the last meaningful
                    // body would require remembering the previous toolMode. Empty
                    // padding keeps the chip-shape consistent during fadeOut.
                }
            }
        }
    }
}

@Composable
private fun PenSettingsRow(
    settings: PenSettings,
    onChange: (PenSettings) -> Unit,
) {
    AdaptiveSettingsRow(
        slots = listOf(
            SlotItem(
                icon = Icons.Default.LineWeight,
                contentDescription = "Толщина",
                label = "Толщина",
                content = {
                    // Defect D: thumbTrackGapSize=0 keeps track visible at min/max.
                    // Defect E: BasicTextField lets user type the value directly.
                    SliderWithValueField(
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
                label = "Прозрачность",
                content = {
                    SliderWithValueField(
                        value = settings.alpha,
                        onValueChange = { onChange(settings.applyAlpha(it)) },
                        valueRange = 0f..1f,
                        formatDisplay = { (it * 100f).roundToInt().toString() },
                        parseInput = { it.trim().trimEnd('%').trim().toIntOrNull()?.div(100f) },
                        suffix = "%",
                    )
                },
            ),
            SlotItem(
                icon = Icons.Default.Palette,
                contentDescription = "Цвет",
                label = null,
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(PRESET_GAP)) {
                        PenSettings.PRESET_COLORS.forEach { preset ->
                            ColorPresetDot(
                                preset = preset,
                                selected = preset.value == settings.color.copy(alpha = 1f).value,
                                onClick = { onChange(settings.applyPreset(preset)) },
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
    preset: Color,
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
            .background(preset)
            .border(
                width = if (selected) PRESET_BORDER_SELECTED else PRESET_BORDER_DEFAULT,
                color = borderColor,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun EraserSettingsRow(
    settings: EraserSettings,
    onChange: (EraserSettings) -> Unit,
) {
    AdaptiveSettingsRow(
        slots = listOf(
            SlotItem(
                icon = Icons.Default.Category,
                contentDescription = "Форма",
                label = "Форма",
                content = {
                    // shape=CircleShape — pill chip; avoids the square ripple of
                    // Material 3 FilterChipDefaults.shape (RoundedCornerShape(8dp)).
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
                content = {
                    // sizeNormalized ∈ [0.01..0.20] displayed as integer percent (1..20).
                    SliderWithValueField(
                        value = settings.sizeNormalized,
                        onValueChange = { onChange(settings.applySize(it)) },
                        valueRange = EraserSettings.MIN_SIZE_NORMALIZED..EraserSettings.MAX_SIZE_NORMALIZED,
                        formatDisplay = { (it * 100f).roundToInt().toString() },
                        parseInput = { it.trim().trimEnd('%').trim().toIntOrNull()?.div(100f) },
                        suffix = "%",
                    )
                },
            ),
        ),
    )
}

private class SlotItem(
    val icon: ImageVector,
    val contentDescription: String,
    val label: String?,
    val content: @Composable () -> Unit,
)

@Composable
private fun FullSlotContent(slot: SlotItem) {
    if (slot.label != null) {
        Text(
            text = slot.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    slot.content()
}

@Composable
private fun CollapsedSlotContent(
    slot: SlotItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
        ) {
            slot.content()
        }
    }
}

/**
 * Adaptive horizontal row that uses [SubcomposeLayout] to measure each slot's
 * natural width in a first pass, then uses [greedyFit] to decide which slots
 * render inline (label + content) and which collapse to an [IconButton].
 *
 * Tapping a collapsed icon expands its content with [expandHorizontally] +
 * [fadeIn]; only one slot can be expanded at a time.
 */
@Composable
private fun AdaptiveSettingsRow(
    slots: List<SlotItem>,
    modifier: Modifier = Modifier,
) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }

    SubcomposeLayout(modifier) { constraints ->
        val gapPx = PANEL_INNER_GAP.roundToPx()
        val paddingPx = (PANEL_HORIZONTAL_PADDING * 2).roundToPx()
        val iconButtonWidthPx = ADAPTIVE_ICON_BUTTON_SIZE.roundToPx()

        val naturalWidths = slots.indices.map { i ->
            subcompose("measure_$i") { FullSlotContent(slots[i]) }
                .first()
                .measure(Constraints())
                .width
        }

        val fits = greedyFit(naturalWidths, constraints.maxWidth, gapPx, paddingPx, iconButtonWidthPx)

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
                    if (fits[i]) {
                        FullSlotContent(slot)
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
) {
    val sliderColors = SliderDefaults.colors(
        inactiveTrackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = Modifier.width(SLIDER_WIDTH),
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
private val PANEL_CORNER_RADIUS = 24.dp
private val PANEL_TONAL_ELEVATION = 0.dp
private val PANEL_SHADOW_ELEVATION = 4.dp
private val PANEL_BORDER_WIDTH = 1.dp
private val PANEL_BOTTOM_PADDING = 16.dp
private val PANEL_HORIZONTAL_PADDING = 16.dp
private val PANEL_VERTICAL_PADDING = 8.dp
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
