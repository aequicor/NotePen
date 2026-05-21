package ru.kyamshanov.notepen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One entry in a [ToolPresetStrip].
 *
 * @property id Stable preset id, echoed back by [ToolPresetStrip] callbacks.
 * @property colorArgb Packed ARGB swatch colour for ink tools; `null` for tools
 *   that have no meaningful colour (e.g. the eraser), which fall back to [icon].
 * @property icon Icon shown when [colorArgb] is `null`.
 * @property deletable Whether the user may delete this preset (built-ins are not).
 * @property selected Whether the preset matches the tool's current settings.
 */
class ToolPresetItem(
    val id: String,
    val colorArgb: Long?,
    val icon: ImageVector?,
    val deletable: Boolean,
    val selected: Boolean,
)

/**
 * A separate "presets" zone for the active tool: a strip of tappable presets
 * followed by an add button, laid out along [orientation].
 *
 * Tapping a preset calls [onApply]. Tapping the trailing add button ([addIcon])
 * calls [onAdd] to capture the current settings. A deletable preset opens a
 * delete menu on long-press (touch) or secondary click (desktop), invoking
 * [onDelete].
 */
@Composable
public fun ToolPresetStrip(
    items: List<ToolPresetItem>,
    addIcon: ImageVector,
    onApply: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    orientation: RailOrientation,
) {
    val content: @Composable () -> Unit = {
        items.forEach { item ->
            PresetItemView(
                item = item,
                onApply = { onApply(item.id) },
                onDelete = { onDelete(item.id) },
            )
        }
        AddPresetButton(icon = addIcon, onClick = onAdd)
    }
    when (orientation) {
        RailOrientation.HORIZONTAL -> Row(
            horizontalArrangement = Arrangement.spacedBy(PRESET_STRIP_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
        RailOrientation.VERTICAL -> Column(
            verticalArrangement = Arrangement.spacedBy(PRESET_STRIP_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetItemView(
    item: ToolPresetItem,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val borderColor = if (item.selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (item.selected) PRESET_BORDER_SELECTED_W else PRESET_BORDER_DEFAULT_W

    val openMenu = { if (item.deletable) menuOpen = true }
    var itemModifier = Modifier
        .size(PRESET_ITEM_SIZE)
        .clip(CircleShape)
        .border(borderWidth, borderColor, CircleShape)
        .combinedClickable(onClick = onApply, onLongClick = openMenu)
    if (item.deletable) {
        itemModifier = itemModifier.secondaryClickModifier { menuOpen = true }
    }

    Box(contentAlignment = Alignment.Center) {
        Box(modifier = itemModifier, contentAlignment = Alignment.Center) {
            when {
                item.colorArgb != null -> Box(
                    Modifier.size(PRESET_ITEM_SIZE).clip(CircleShape)
                        .background(Color(item.colorArgb.toInt())),
                )
                item.icon != null -> Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(PRESET_ICON_SIZE),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Удалить") },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun AddPresetButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(PRESET_ITEM_SIZE)) {
        Icon(
            imageVector = icon,
            contentDescription = "Добавить пресет",
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(PRESET_ICON_SIZE),
        )
    }
}

/**
 * Platform hook for a mouse secondary-click (right-click). On desktop this opens
 * the delete menu; on touch platforms it is a no-op (long-press is used instead).
 */
internal expect fun Modifier.secondaryClickModifier(onSecondaryClick: () -> Unit): Modifier

private val PRESET_STRIP_GAP = 8.dp
private val PRESET_ITEM_SIZE = 28.dp
private val PRESET_ICON_SIZE = 18.dp
private val PRESET_BORDER_DEFAULT_W = 1.dp
private val PRESET_BORDER_SELECTED_W = 2.dp
