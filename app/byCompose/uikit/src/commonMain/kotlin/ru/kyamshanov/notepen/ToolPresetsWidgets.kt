package ru.kyamshanov.notepen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One entry in the presets section of a tool wheel.
 *
 * @property id Stable preset id, echoed back by the apply/delete callbacks.
 * @property deletable Whether the user may delete this preset (built-ins are not).
 * @property selected Whether the preset matches the tool's current settings.
 * @property preview Renders a thumbnail reflecting the preset's actual tool
 *   configuration (colour, thickness, eraser shape/size/mode). Drawn centred
 *   inside the preset chip.
 */
public class ToolPresetItem(
    public val id: String,
    public val deletable: Boolean,
    public val selected: Boolean,
    public val preview: @Composable () -> Unit,
)

/**
 * Builds the presets section of a tool wheel as a list of [WheelEntry]: one chip
 * per preset followed by a trailing add button. The chips share the wheel's
 * single scroll axis with the tool toggles and settings slots.
 *
 * Tapping a chip calls [onApply]; tapping the add button calls [onAdd] to capture
 * the current settings. A deletable chip opens a delete menu on long-press (touch)
 * or secondary click (desktop), invoking [onDelete].
 *
 * The add button is shown only when [showAdd] is true. Callers hide it when the
 * current settings already match an existing preset, so identical presets can't be
 * duplicated.
 */
public fun toolPresetWheelEntries(
    items: List<ToolPresetItem>,
    addIcon: ImageVector,
    onApply: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    showAdd: Boolean = true,
): List<WheelEntry> =
    buildList {
        items.forEach { item ->
            add(
                WheelEntry(item.id) {
                    PresetChip(item = item, onApply = { onApply(item.id) }, onDelete = { onDelete(item.id) })
                },
            )
        }
        if (showAdd) {
            add(WheelEntry(ADD_PRESET_KEY) { AddPresetButton(icon = addIcon, onClick = onAdd) })
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    item: ToolPresetItem,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "presetBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (item.selected) PRESET_BORDER_SELECTED_W else PRESET_BORDER_DEFAULT_W,
        label = "presetBorderWidth",
    )

    val openMenu = { if (item.deletable) menuOpen = true }
    var itemModifier =
        Modifier
            .size(PRESET_ITEM_SIZE)
            .clip(CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .combinedClickable(onClick = onApply, onLongClick = openMenu)
    if (item.deletable) {
        itemModifier = itemModifier.secondaryClickModifier { menuOpen = true }
    }

    Box(contentAlignment = Alignment.Center) {
        Box(modifier = itemModifier, contentAlignment = Alignment.Center) {
            item.preview()
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
private fun AddPresetButton(
    icon: ImageVector,
    onClick: () -> Unit,
) {
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

private const val ADD_PRESET_KEY = "__add_preset__"
private val PRESET_ITEM_SIZE = 28.dp
private val PRESET_ICON_SIZE = 18.dp
private val PRESET_BORDER_DEFAULT_W = 1.dp
private val PRESET_BORDER_SELECTED_W = 2.dp
