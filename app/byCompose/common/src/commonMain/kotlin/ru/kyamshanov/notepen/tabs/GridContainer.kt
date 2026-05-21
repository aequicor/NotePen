package ru.kyamshanov.notepen.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/** Width / height of a draggable divider between panels. */
private val DIVIDER_THICKNESS = 6.dp

/** Border width drawn around the focused panel. */
private val FOCUS_BORDER = 2.dp

/**
 * Renders the workspace grid for [layout]. Invokes [content] once per
 * [Panel], laid out per [WorkspaceLayout.template] with draggable dividers
 * whose positions come from [WorkspaceLayout.ratios].
 *
 * The focused panel ([WorkspaceLayout.focusedPanelId]) gets a highlight
 * border. Pressing anywhere in a panel reports it through [onFocusPanel]
 * (observed on the initial pass, so it never steals gestures from the
 * panel content). Dragging a divider reports its new ratio through
 * [onSetRatio] keyed by the divider index (see [WorkspaceLayout] for the
 * per-template index meaning).
 */
@Composable
fun GridContainer(
    layout: WorkspaceLayout,
    onSetRatio: (index: Int, value: Float) -> Unit,
    onFocusPanel: (PanelId) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Panel) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when (layout.template) {
            LayoutTemplate.FULL -> Cell(layout, layout.panels[0], onFocusPanel, content, Modifier.fillMaxSize())
            LayoutTemplate.COLUMNS_2 -> Columns(layout, onSetRatio, onFocusPanel, content)
            LayoutTemplate.COLUMNS_3 -> Columns(layout, onSetRatio, onFocusPanel, content)
            LayoutTemplate.LEFT_PLUS_STACK -> LeftPlusStack(layout, onSetRatio, onFocusPanel, content)
            LayoutTemplate.GRID_2X2 -> GridTwoByTwo(layout, onSetRatio, onFocusPanel, content)
        }
    }
}

/** Two or three side-by-side columns sharing all height. */
@Composable
private fun Columns(
    layout: WorkspaceLayout,
    onSetRatio: (Int, Float) -> Unit,
    onFocusPanel: (PanelId) -> Unit,
    content: @Composable (Panel) -> Unit,
) {
    var widthPx by remember { mutableStateOf(0f) }
    Row(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width.toFloat() }) {
        val r = layout.ratios
        val weights = when (layout.panels.size) {
            2 -> listOf(r[0], 1f - r[0])
            else -> listOf(r[0], r[1] - r[0], 1f - r[1])
        }
        layout.panels.forEachIndexed { index, panel ->
            Cell(layout, panel, onFocusPanel, content, Modifier.fillMaxHeight().weight(weights[index]))
            if (index < layout.panels.size - 1) {
                VerticalDivider { dx -> onSetRatio(index, ratioAt(layout, index) + delta(dx, widthPx)) }
            }
        }
    }
}

/** One large left panel, two stacked on the right. */
@Composable
private fun LeftPlusStack(
    layout: WorkspaceLayout,
    onSetRatio: (Int, Float) -> Unit,
    onFocusPanel: (PanelId) -> Unit,
    content: @Composable (Panel) -> Unit,
) {
    var widthPx by remember { mutableStateOf(0f) }
    var rightHeightPx by remember { mutableStateOf(0f) }
    val (left, topRight, bottomRight) = Triple(layout.panels[0], layout.panels[1], layout.panels[2])
    Row(Modifier.fillMaxSize().onSizeChanged { widthPx = it.width.toFloat() }) {
        Cell(layout, left, onFocusPanel, content, Modifier.fillMaxHeight().weight(layout.ratios[0]))
        VerticalDivider { dx -> onSetRatio(0, layout.ratios[0] + delta(dx, widthPx)) }
        Column(
            Modifier.fillMaxHeight().weight(1f - layout.ratios[0])
                .onSizeChanged { rightHeightPx = it.height.toFloat() },
        ) {
            Cell(layout, topRight, onFocusPanel, content, Modifier.fillMaxWidth().weight(layout.ratios[1]))
            HorizontalDivider { dy -> onSetRatio(1, layout.ratios[1] + delta(dy, rightHeightPx)) }
            Cell(layout, bottomRight, onFocusPanel, content, Modifier.fillMaxWidth().weight(1f - layout.ratios[1]))
        }
    }
}

/** Two-by-two grid: a full-height vertical divider, a row divider per column. */
@Composable
private fun GridTwoByTwo(
    layout: WorkspaceLayout,
    onSetRatio: (Int, Float) -> Unit,
    onFocusPanel: (PanelId) -> Unit,
    content: @Composable (Panel) -> Unit,
) {
    var widthPx by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }
    val p = layout.panels
    Row(
        Modifier.fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat(); heightPx = it.height.toFloat() },
    ) {
        GridColumn(layout, p[0], p[2], onSetRatio, onFocusPanel, content, heightPx, Modifier.weight(layout.ratios[0]))
        VerticalDivider { dx -> onSetRatio(0, layout.ratios[0] + delta(dx, widthPx)) }
        GridColumn(layout, p[1], p[3], onSetRatio, onFocusPanel, content, heightPx, Modifier.weight(1f - layout.ratios[0]))
    }
}

@Composable
private fun GridColumn(
    layout: WorkspaceLayout,
    top: Panel,
    bottom: Panel,
    onSetRatio: (Int, Float) -> Unit,
    onFocusPanel: (PanelId) -> Unit,
    content: @Composable (Panel) -> Unit,
    heightPx: Float,
    modifier: Modifier,
) {
    Column(modifier.fillMaxHeight()) {
        Cell(layout, top, onFocusPanel, content, Modifier.fillMaxWidth().weight(layout.ratios[1]))
        HorizontalDivider { dy -> onSetRatio(1, layout.ratios[1] + delta(dy, heightPx)) }
        Cell(layout, bottom, onFocusPanel, content, Modifier.fillMaxWidth().weight(1f - layout.ratios[1]))
    }
}

private fun ratioAt(layout: WorkspaceLayout, index: Int): Float = layout.ratios[index]

private fun delta(dragPx: Float, extentPx: Float): Float =
    if (extentPx > 0f) dragPx / extentPx else 0f

@Composable
private fun Cell(
    layout: WorkspaceLayout,
    panel: Panel,
    onFocusPanel: (PanelId) -> Unit,
    content: @Composable (Panel) -> Unit,
    modifier: Modifier,
) {
    // Focus highlight only makes sense when more than one panel is open.
    val focused = layout.isSplit && panel.id == layout.focusedPanelId
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier
            .border(FOCUS_BORDER, borderColor)
            .focusOnPress { onFocusPanel(panel.id) },
    ) {
        content(panel)
    }
}

/** Reports a press on the initial pass without consuming it (so panel gestures still work). */
private fun Modifier.focusOnPress(onFocus: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed && it.previousPressed.not() }) onFocus()
        }
    }
}

@Composable
private fun RowScope.VerticalDivider(onDrag: (Float) -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .width(DIVIDER_THICKNESS)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            },
    )
}

@Composable
private fun ColumnScope.HorizontalDivider(onDrag: (Float) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(DIVIDER_THICKNESS)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            },
    )
}
