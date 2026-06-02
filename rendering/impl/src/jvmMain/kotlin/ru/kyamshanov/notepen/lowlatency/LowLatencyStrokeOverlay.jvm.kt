package ru.kyamshanov.notepen.lowlatency

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.drawing.api.LiveStrokeSample
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.cos
import kotlin.math.sin

private const val TILT_WIDTH_GAIN = 0.5f
private const val HANDOFF_HOLD_MS = 50L
private const val MARKER_NIB_ANGLE_RADIANS = 0.7853982f

private data class StrokeSegment(
    val prev: DrawingPoint?,
    val curr: DrawingPoint,
    val colorArgb: Int,
    val widthPx: Float,
    val extent: PageExtent,
    val toolKind: ToolKind,
)

@Composable
actual fun LowLatencyStrokeOverlay(
    drawingState: PdfDrawingState,
    modifier: Modifier,
) {
    val panel = remember { LiveInkPanel() }
    SwingPanel(
        modifier = modifier,
        background = Color.Transparent,
        factory = { panel },
    )

    DisposableEffect(drawingState, panel) {
        val unregister =
            drawingState.addLiveStrokeListener { sample ->
                panel.render(sample)
            }
        onDispose {
            unregister()
            panel.clearInk()
        }
    }

    LaunchedEffect(drawingState, panel) {
        snapshotFlow { drawingState.isDrawing.value }
            .collect { drawing ->
                if (drawing) {
                    panel.clearInk()
                } else {
                    delay(HANDOFF_HOLD_MS)
                    panel.clearInk()
                }
            }
    }
}

@Composable
actual fun rememberLowLatencyOverlayAvailable(): Boolean = true

@Composable
actual fun rememberLowLatencyOverlayMaxDimensionPx(): Int = Int.MAX_VALUE

private class LiveInkPanel : JComponent() {
    private val segments = ArrayList<StrokeSegment>(512)
    private val scratch = Path2D.Float()

    init {
        isOpaque = false
        isFocusable = false
        isDoubleBuffered = false
        isRequestFocusEnabled = false
        isEnabled = false
    }

    fun render(sample: LiveStrokeSample) {
        val update = {
            if (width > 0 && height > 0) {
                if (sample.previous == null) segments.clear()
                segments.add(sample.toSegment(width.toFloat()))
                paintImmediately(0, 0, width, height)
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            update()
        } else {
            SwingUtilities.invokeLater(update)
        }
    }

    fun clearInk() {
        val update = {
            segments.clear()
            if (width > 0 && height > 0) paintImmediately(0, 0, width, height)
        }
        if (SwingUtilities.isEventDispatchThread()) {
            update()
        } else {
            SwingUtilities.invokeLater(update)
        }
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.composite = AlphaComposite.Clear
            g2.fillRect(0, 0, width, height)
            g2.composite = AlphaComposite.SrcOver
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            for (segment in segments) {
                drawSegment(g2, segment, scratch)
            }
        } finally {
            g2.dispose()
        }
    }
}

private fun LiveStrokeSample.toSegment(slotWidthPx: Float): StrokeSegment {
    val widthPx =
        if (extent.width > 0f) {
            normalizedStrokeWidth * slotWidthPx / extent.width
        } else {
            0f
        }
    return StrokeSegment(
        prev = previous,
        curr = current,
        colorArgb = colorArgb.toInt(),
        widthPx = widthPx,
        extent = extent,
        toolKind = toolKind,
    )
}

private fun drawSegment(
    g: Graphics2D,
    segment: StrokeSegment,
    scratch: Path2D.Float,
) {
    val prev = segment.prev
    val curr = segment.curr
    val ext = segment.extent
    val pdfW = if (ext.width > 0f) g.clipBounds.width / ext.width else g.clipBounds.width.toFloat()
    val pdfH = if (ext.height > 0f) g.clipBounds.height / ext.height else g.clipBounds.height.toFloat()
    val offX = -ext.left
    val offY = -ext.top
    val x = (curr.x + offX) * pdfW
    val y = (curr.y + offY) * pdfH

    g.color = java.awt.Color(segment.colorArgb, true)
    if (segment.toolKind == ToolKind.MARKER) {
        if (prev == null) return
        val halfWidthPx = segment.widthPx * 0.5f
        if (halfWidthPx <= 0f) return
        val nibX = cos(MARKER_NIB_ANGLE_RADIANS) * halfWidthPx
        val nibY = sin(MARKER_NIB_ANGLE_RADIANS) * halfWidthPx
        val x1 = (prev.x + offX) * pdfW
        val y1 = (prev.y + offY) * pdfH
        scratch.reset()
        scratch.moveTo((x1 + nibX).toDouble(), (y1 + nibY).toDouble())
        scratch.lineTo((x1 - nibX).toDouble(), (y1 - nibY).toDouble())
        scratch.lineTo((x - nibX).toDouble(), (y - nibY).toDouble())
        scratch.lineTo((x + nibX).toDouble(), (y + nibY).toDouble())
        scratch.closePath()
        g.fill(scratch)
        return
    }

    val tiltBoost = 1f + TILT_WIDTH_GAIN * curr.tilt
    val widthPx = (segment.widthPx * curr.pressure * tiltBoost).coerceAtLeast(1f)
    g.stroke = BasicStroke(widthPx, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    if (prev == null) {
        g.drawLine(x.toInt(), y.toInt(), x.toInt(), y.toInt())
    } else {
        val x1 = (prev.x + offX) * pdfW
        val y1 = (prev.y + offY) * pdfH
        scratch.reset()
        scratch.moveTo(x1.toDouble(), y1.toDouble())
        scratch.lineTo(x.toDouble(), y.toDouble())
        g.draw(scratch)
    }
}
