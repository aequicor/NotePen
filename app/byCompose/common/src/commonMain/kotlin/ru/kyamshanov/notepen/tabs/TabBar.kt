package ru.kyamshanov.notepen.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.kyamshanov.notepen.ui.glass.GlassSurface

/** Vertical extent of a [TabBar] — exposed so callers can offset the content below it. */
val TAB_BAR_HEIGHT = 36.dp
private val TAB_MIN_WIDTH = 96.dp
private val TAB_CLOSE_SLOT = 28.dp
private val TAB_SHAPE = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)

/**
 * Horizontal tab strip for one panel. Tabs share all available space equally
 * (Safari-style) and shrink down to [TAB_MIN_WIDTH] with horizontal scroll when
 * there are too many to fit. The `+` button is always anchored to the right
 * outside the scrollable area. The close (`×`) button is hidden when only one
 * tab is open.
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
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(TAB_BAR_HEIGHT),
        shape = RectangleShape,
        tint = MaterialTheme.colorScheme.surfaceContainerLow,
        fillAlpha = 0.35f,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val tabCount = openDocs.tabs.size
                val scrollState = rememberScrollState()
                val needsScroll = tabCount > 0 && maxWidth / tabCount < TAB_MIN_WIDTH
                val tabWidth: Dp = when {
                    tabCount == 0 -> TAB_MIN_WIDTH
                    needsScroll -> TAB_MIN_WIDTH
                    else -> maxWidth / tabCount
                }
                Row(
                    modifier = if (needsScroll) {
                        Modifier.horizontalScroll(scrollState).fillMaxHeight()
                    } else {
                        Modifier.fillMaxWidth().fillMaxHeight()
                    },
                    verticalAlignment = Alignment.Bottom,
                ) {
                    openDocs.tabs.forEachIndexed { index, tab ->
                        if (index > 0) {
                            val prevTab = openDocs.tabs[index - 1]
                            val showDivider = prevTab.id != openDocs.activeId && tab.id != openDocs.activeId
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(TAB_BAR_HEIGHT),
                            ) {
                                if (showDivider) {
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .fillMaxHeight(0.6f)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    )
                                }
                            }
                        }
                        TabChip(
                            tab = tab,
                            isActive = tab.id == openDocs.activeId,
                            showClose = tab.id == openDocs.activeId && tabCount > 1,
                            onSelect = { onSelect(side, tab.id) },
                            onClose = { onClose(side, tab.id) },
                            onOpenInSplit = onOpenInSplit?.let { callback ->
                                { orientation -> callback(side, orientation) }
                            },
                            modifier = Modifier.width(tabWidth),
                        )
                    }
                }
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
    showClose: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onOpenInSplit: ((PanelOrientation) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Active tab: subtle highlight that lets the outer glass bar show through.
    // Inactive tab: fully transparent — the glass bar background is the visual context.
    val chipBackground: Color = if (isActive) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    } else {
        Color.Transparent
    }
    val contentColor: Color = if (isActive) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(TAB_BAR_HEIGHT)
            .background(color = chipBackground, shape = TAB_SHAPE)
            .combinedClickable(
                enabled = true,
                onClick = { if (!isActive) onSelect() },
                onLongClick = if (onOpenInSplit != null) {
                    { menuExpanded = true }
                } else {
                    null
                },
            ),
    ) {
        // Left spacer mirrors the close-button slot so the label stays centred
        // regardless of whether the button is shown.
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(TAB_CLOSE_SLOT))
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Text(
                    text = tab.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
            if (showClose) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(TAB_CLOSE_SLOT),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть вкладку ${tab.displayName}",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(TAB_CLOSE_SLOT))
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
