package ru.kyamshanov.notepen.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Vertical extent of a [TabBar] — exposed so callers can offset the content below it. */
val TAB_BAR_HEIGHT = 36.dp
private val TAB_MIN_WIDTH = 96.dp
private val TAB_MAX_WIDTH = 200.dp
private val TAB_SHAPE = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)

/**
 * Horizontal tab strip for one panel. Active tab is highlighted with
 * [MaterialTheme.colorScheme.secondaryContainer]; inactive tabs use
 * [MaterialTheme.colorScheme.surface]. Right-most slot is an `+` icon
 * button that delegates to [onAddTab].
 *
 * @param side the panel this bar belongs to — passed back to the
 *   caller in each callback so the same bar implementation drives both
 *   panels in a split layout.
 * @param openDocs current set of tabs and active id for [side].
 * @param onSelect invoked when the user taps a (non-active) tab.
 * @param onClose invoked when the user clicks the tab's `×`.
 * @param onAddTab invoked when the user clicks the trailing `+`.
 */
@Composable
fun TabBar(
    side: PanelSide,
    openDocs: OpenDocuments,
    onSelect: (PanelSide, DocumentId) -> Unit,
    onClose: (PanelSide, DocumentId) -> Unit,
    onAddTab: (PanelSide) -> Unit,
    /**
     * Invoked from the per-tab context menu ("Open in split right" /
     * "Open in split bottom"). `null` when the workspace already has a
     * split — the spec disallows nesting deeper than one level, so the
     * menu items are hidden.
     */
    onOpenInSplit: ((PanelSide, PanelOrientation) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(TAB_BAR_HEIGHT),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            openDocs.tabs.forEach { tab ->
                TabChip(
                    tab = tab,
                    isActive = tab.id == openDocs.activeId,
                    onSelect = { onSelect(side, tab.id) },
                    onClose = { onClose(side, tab.id) },
                    onOpenInSplit = onOpenInSplit?.let { callback ->
                        { orientation -> callback(side, orientation) }
                    },
                )
            }
            IconButton(
                onClick = { onAddTab(side) },
                modifier = Modifier
                    .size(TAB_BAR_HEIGHT)
                    .padding(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Открыть PDF в новой вкладке",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabChip(
    tab: DocumentTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onOpenInSplit: ((PanelOrientation) -> Unit)?,
) {
    val background: Color = if (isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor: Color = if (isActive) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .height(TAB_BAR_HEIGHT)
            .widthIn(min = TAB_MIN_WIDTH, max = TAB_MAX_WIDTH)
            .background(color = background, shape = TAB_SHAPE)
            .combinedClickable(
                enabled = true,
                onClick = { if (!isActive) onSelect() },
                onLongClick = if (onOpenInSplit != null) {
                    { menuExpanded = true }
                } else {
                    null
                },
            )
            .padding(start = 12.dp, end = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f, fill = false),
            ) {
                androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides contentColor) {
                    Text(
                        text = tab.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть вкладку ${tab.displayName}",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        if (onOpenInSplit != null) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Open in split right") },
                    onClick = {
                        menuExpanded = false
                        onOpenInSplit(PanelOrientation.HORIZONTAL)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Open in split bottom") },
                    onClick = {
                        menuExpanded = false
                        onOpenInSplit(PanelOrientation.VERTICAL)
                    },
                )
            }
        }
    }
}
