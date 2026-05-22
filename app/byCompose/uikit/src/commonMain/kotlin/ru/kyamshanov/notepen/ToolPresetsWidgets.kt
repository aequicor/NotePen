package ru.kyamshanov.notepen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One entry in a [ToolPresetStrip].
 *
 * @property id Stable preset id, echoed back by [ToolPresetStrip] callbacks.
 * @property deletable Whether the user may delete this preset (built-ins are not).
 * @property selected Whether the preset matches the tool's current settings.
 * @property preview Renders a thumbnail reflecting the preset's actual tool
 *   configuration (colour, thickness, eraser shape/size/mode). Drawn centred
 *   inside the preset chip.
 */
class ToolPresetItem(
    val id: String,
    val deletable: Boolean,
    val selected: Boolean,
    val preview: @Composable () -> Unit,
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
    // Eagerly initialised from the first items so the first frame shows content without animation.
    val latestItems = remember { mutableStateMapOf<String, ToolPresetItem>().also { m -> items.forEach { m[it.id] = it } } }
    val orderedIds = remember { mutableStateListOf<String>().also { l -> items.forEach { l.add(it.id) } } }
    val visibilityStates = remember {
        mutableStateMapOf<String, MutableTransitionState<Boolean>>().also { m ->
            items.forEach { m[it.id] = MutableTransitionState(true) }
        }
    }

    LaunchedEffect(items) {
        val inputIds = items.map { it.id }.toSet()
        for (item in items) {
            latestItems[item.id] = item
            val existing = visibilityStates[item.id]
            when {
                existing == null -> {
                    val ts = MutableTransitionState(false)
                    ts.targetState = true
                    visibilityStates[item.id] = ts
                    orderedIds.add(item.id)
                }
                // Item was mid-removal but came back — reverse the animation.
                !existing.targetState -> existing.targetState = true
            }
        }
        for (id in orderedIds.toList()) {
            if (id !in inputIds) visibilityStates[id]?.targetState = false
        }
    }

    // Remove items whose exit animation has fully completed.
    LaunchedEffect(Unit) {
        snapshotFlow {
            orderedIds.filter { id -> visibilityStates[id]?.let { it.isIdle && !it.currentState } == true }
        }.collect { finished ->
            finished.forEach { id ->
                orderedIds.remove(id)
                visibilityStates.remove(id)
                latestItems.remove(id)
            }
        }
    }

    val content: @Composable () -> Unit = {
        for (id in orderedIds) {
            val item = latestItems[id] ?: continue
            val visState = visibilityStates[id] ?: continue
            key(id) {
                AnimatedVisibility(
                    visibleState = visState,
                    enter = scaleIn(initialScale = 0.5f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.5f) + fadeOut(),
                ) {
                    PresetItemView(
                        item = item,
                        onApply = { onApply(id) },
                        onDelete = { onDelete(id) },
                    )
                }
            }
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
    val borderColor by animateColorAsState(
        targetValue = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "presetBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (item.selected) PRESET_BORDER_SELECTED_W else PRESET_BORDER_DEFAULT_W,
        label = "presetBorderWidth",
    )

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
