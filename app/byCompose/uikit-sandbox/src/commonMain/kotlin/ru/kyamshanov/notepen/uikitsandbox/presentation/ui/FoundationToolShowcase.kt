package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.BooleanToggle
import ru.kyamshanov.notepen.ColorPresets
import ru.kyamshanov.notepen.CustomButton
import ru.kyamshanov.notepen.EraserSettings
import ru.kyamshanov.notepen.EraserSettingsPanel
import ru.kyamshanov.notepen.GlassmorphismBackButton
import ru.kyamshanov.notepen.GlassmorphismIconButton
import ru.kyamshanov.notepen.LiquidGlassDropdownMenu
import ru.kyamshanov.notepen.NotePenIcons
import ru.kyamshanov.notepen.OrientedSlider
import ru.kyamshanov.notepen.RailOrientation
import ru.kyamshanov.notepen.StrokeWidthSlider
import ru.kyamshanov.notepen.ToolPresetItem
import ru.kyamshanov.notepen.Tooltip
import ru.kyamshanov.notepen.WheelEntry
import ru.kyamshanov.notepen.WheelStrip
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.ui.component.StatusBadge
import ru.kyamshanov.notepen.toolPresetWheelEntries

/**
 * Секция базовых UIKit-поверхностей и кнопок проекта.
 *
 * @param strings локализованные подписи и сообщения.
 * @param onMessage callback для snackbar-сообщений sandbox-приложения.
 */
@Composable
internal fun FoundationShowcase(
    strings: SandboxStrings,
    onMessage: (String) -> Unit,
) {
    SectionBlock(title = strings.foundationTitle, icon = Icons.Default.Widgets) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomButton(onClick = { onMessage(strings.primaryActionMessage) }) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.primaryAction)
            }
            OutlinedButton(onClick = { onMessage(strings.secondaryActionMessage) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(strings.secondaryAction)
            }
            Tooltip(text = strings.tooltipText) {
                IconButton(onClick = { onMessage(strings.tooltipActionMessage) }) {
                    Icon(Icons.Default.Settings, contentDescription = strings.tooltipText)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassmorphismBackButton(onClick = { onMessage(strings.backMessage) })
            GlassmorphismIconButton(
                icon = Icons.Default.Brush,
                contentDescription = strings.glassButton,
                onClick = { onMessage(strings.glassMessage) },
                tint = MaterialTheme.colorScheme.primary,
            )
            AssistChip(
                onClick = { onMessage(strings.chipMessage) },
                label = { Text(strings.statusReady) },
                leadingIcon = {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            StatusBadge(status = AvailabilityStatus.NOT_FOUND)
        }
        MenuPreview(strings = strings, onMessage = onMessage)
    }
}

@Composable
private fun MenuPreview(
    strings: SandboxStrings,
    onMessage: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = strings.menu)
        }
        LiquidGlassDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(strings.menuOpen) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onMessage(strings.menuOpenMessage)
                },
            )
            DropdownMenuItem(
                text = { Text(strings.menuDelete) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onMessage(strings.menuDeleteMessage)
                },
            )
        }
    }
}

/**
 * Секция инструментов рукописного ввода и связанных controls.
 *
 * @param strings локализованные подписи инструментов.
 */
@Composable
internal fun ToolShowcase(strings: SandboxStrings) {
    var selectedColor by remember { mutableStateOf(PenSettings.PRESET_COLORS[2]) }
    var strokeWidth by remember { mutableStateOf(PenSettings.DEFAULT_STROKE_WIDTH) }
    var sliderValue by remember { mutableStateOf(0.35f) }
    var toggle by remember { mutableStateOf(true) }
    var eraserSettings by remember { mutableStateOf(EraserSettings()) }
    SectionBlock(title = strings.toolsTitle, icon = Icons.Default.Tune) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorPresets(
                presets = PenSettings.PRESET_COLORS,
                isSelected = { it == selectedColor },
                onPick = { selectedColor = it },
                orientation = RailOrientation.HORIZONTAL,
            )
            BooleanToggle(enabled = toggle, onToggle = { toggle = it })
            Icon(NotePenIcons.ColorSwatch, contentDescription = null, tint = Color(selectedColor.toInt()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            StrokeWidthSlider(
                orientation = RailOrientation.HORIZONTAL,
                strokeWidth = strokeWidth,
                min = PenSettings.MIN_STROKE_WIDTH,
                max = PenSettings.MAX_STROKE_WIDTH,
                onWidthChange = { strokeWidth = it },
            )
            OrientedSlider(
                orientation = RailOrientation.VERTICAL,
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 0f..1f,
            )
        }
        EraserSettingsPanel(
            settings = eraserSettings,
            onChange = { eraserSettings = it },
            modifier = Modifier.fillMaxWidth(),
        )
        ToolWheelPreview(strings = strings, selectedColor = selectedColor)
    }
}

@Composable
private fun ToolWheelPreview(
    strings: SandboxStrings,
    selectedColor: Long,
) {
    var selectedKey by remember { mutableStateOf<Any>("pen") }
    val entries =
        listOf(
            toolEntry("pen", NotePenIcons.Brush, strings.pen) { selectedKey = "pen" },
            toolEntry("marker", NotePenIcons.Highlighter, strings.marker) { selectedKey = "marker" },
            toolEntry("eraser", NotePenIcons.Eraser, strings.eraser) { selectedKey = "eraser" },
        ) +
            toolPresetWheelEntries(
                items =
                    listOf(
                        ToolPresetItem(
                            id = "blue",
                            deletable = false,
                            selected = selectedKey == "blue",
                            preview = { PresetDot(Color(selectedColor.toInt())) },
                        ),
                        ToolPresetItem(
                            id = "marker-yellow",
                            deletable = true,
                            selected = selectedKey == "marker-yellow",
                            preview = { PresetStroke(Color(MarkerSettings.PRESET_COLORS[0].toInt())) },
                        ),
                    ),
                addIcon = Icons.Default.Add,
                onApply = { selectedKey = it },
                onAdd = { selectedKey = "new" },
                onDelete = { selectedKey = "pen" },
            )
    WheelStrip(
        entries = entries,
        orientation = RailOrientation.HORIZONTAL,
        selectedKey = selectedKey,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun toolEntry(
    key: Any,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
): WheelEntry =
    WheelEntry(key) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label)
        }
    }

@Composable
private fun PresetDot(color: Color) {
    Box(
        modifier =
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
private fun PresetStroke(color: Color) {
    Canvas(modifier = Modifier.size(22.dp, 14.dp)) {
        drawLine(
            color = color,
            start = Offset(size.width * 0.1f, size.height * 0.72f),
            end = Offset(size.width * 0.9f, size.height * 0.28f),
            strokeWidth = size.height * 0.45f,
        )
    }
}
