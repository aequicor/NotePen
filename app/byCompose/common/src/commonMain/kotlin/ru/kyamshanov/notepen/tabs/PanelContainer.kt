package ru.kyamshanov.notepen.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/** Width / height of the draggable divider between two panels. */
private val SPLITTER_THICKNESS = 6.dp

/**
 * Renders one or two panels of editor content.
 *
 * - [PanelLayout.Single] → invokes [content] once with [PanelSide.PRIMARY]
 *   and the layout's tabs.
 * - [PanelLayout.Split] → invokes [content] twice, once per side. Side
 *   layout follows [PanelLayout.Split.orientation]: HORIZONTAL puts
 *   PRIMARY on the left and SECONDARY on the right; VERTICAL puts
 *   PRIMARY on top and SECONDARY on the bottom. A draggable splitter
 *   sits between them and reports new ratios through [onSetSplitRatio].
 *
 * @param onSetSplitRatio fired while the user drags the splitter. The
 *   passed ratio is clamped by the model (see
 *   [PanelLayout.MIN_RATIO]..[PanelLayout.MAX_RATIO]).
 */
@Composable
fun PanelContainer(
    layout: PanelLayout,
    onSetSplitRatio: (Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (side: PanelSide, openDocs: OpenDocuments) -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        when (layout) {
            is PanelLayout.Single -> content(PanelSide.PRIMARY, layout.tabs)
            is PanelLayout.Split -> when (layout.orientation) {
                PanelOrientation.HORIZONTAL -> HorizontalSplit(layout, onSetSplitRatio, content)
                PanelOrientation.VERTICAL -> VerticalSplit(layout, onSetSplitRatio, content)
            }
        }
    }
}

@Composable
private fun HorizontalSplit(
    layout: PanelLayout.Split,
    onSetSplitRatio: (Float) -> Unit,
    content: @Composable (PanelSide, OpenDocuments) -> Unit,
) {
    var containerWidthPx by remember { mutableStateOf(0f) }
    Row(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerWidthPx = it.width.toFloat() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(layout.ratio),
        ) {
            content(PanelSide.PRIMARY, layout.left.tabs)
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(SPLITTER_THICKNESS)
                .background(MaterialTheme.colorScheme.outlineVariant)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val width = containerWidthPx.takeIf { it > 0f } ?: return@detectDragGestures
                        val deltaRatio = dragAmount.x / width
                        onSetSplitRatio((layout.ratio + deltaRatio).coerceIn(0f, 1f))
                    }
                },
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
        ) {
            content(PanelSide.SECONDARY, layout.right.tabs)
        }
    }
}

@Composable
private fun VerticalSplit(
    layout: PanelLayout.Split,
    onSetSplitRatio: (Float) -> Unit,
    content: @Composable (PanelSide, OpenDocuments) -> Unit,
) {
    var containerHeightPx by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerHeightPx = it.height.toFloat() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(layout.ratio),
        ) {
            content(PanelSide.PRIMARY, layout.left.tabs)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SPLITTER_THICKNESS)
                .background(MaterialTheme.colorScheme.outlineVariant)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val height = containerHeightPx.takeIf { it > 0f } ?: return@detectDragGestures
                        val deltaRatio = dragAmount.y / height
                        onSetSplitRatio((layout.ratio + deltaRatio).coerceIn(0f, 1f))
                    }
                },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            content(PanelSide.SECONDARY, layout.right.tabs)
        }
    }
}
