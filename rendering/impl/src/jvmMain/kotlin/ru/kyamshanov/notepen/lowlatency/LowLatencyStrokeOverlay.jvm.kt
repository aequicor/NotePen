package ru.kyamshanov.notepen.lowlatency

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LONG
import com.sun.jna.platform.win32.WinUser.GWL_EXSTYLE
import com.sun.jna.win32.StdCallLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import ru.kyamshanov.notepen.drawing.api.LiveStrokeSample
import ru.kyamshanov.notepen.drawing.api.PdfDrawingState
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.Window
import java.awt.geom.Path2D
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TILT_WIDTH_GAIN = 0.5f
private const val HANDOFF_HOLD_MS = 50L
private const val MARKER_NIB_ANGLE_RADIANS = 0.7853982f

private const val WS_EX_TRANSPARENT = 0x00000020
private const val WS_EX_LAYERED = 0x00080000
private const val WS_EX_NOACTIVATE = 0x08000000
private const val WS_EX_TOOLWINDOW = 0x00000080
private const val OVERLAY_WINDOW_NAME = "NotePenLiveInkOverlay"

private data class StrokeSegment(
    val prev: DrawingPoint?,
    val curr: DrawingPoint,
    val colorArgb: Int,
    val widthPx: Float,
    val extent: PageExtent,
    val toolKind: ToolKind,
)

private data class ViewportMapping(
    val fullWidthPx: Int,
    val fullHeightPx: Int,
    val offsetX: Int,
    val offsetY: Int,
)

@Composable
actual fun LowLatencyStrokeOverlay(
    drawingState: PdfDrawingState,
    modifier: Modifier,
    viewportScale: Float,
    windowBounds: Rect?,
) {
    if (!Platform.isWindows()) return

    val boundsState = remember { mutableStateOf(Rect.Zero) }
    val overlay = remember { DesktopLiveInkOverlay() }
    Box(
        modifier =
            modifier.onGloballyPositioned { coords ->
                boundsState.value = coords.boundsInWindow()
            },
    )

    val activeBounds = windowBounds ?: boundsState.value
    val activeViewportScale = if (windowBounds == null) viewportScale else 1f
    LaunchedEffect(overlay, activeBounds, activeViewportScale) {
        overlay.updateBounds(activeBounds, activeViewportScale)
    }

    DisposableEffect(drawingState, overlay) {
        overlay.show()
        val unregister =
            drawingState.addLiveStrokeListener { sample ->
                overlay.render(sample)
            }
        onDispose {
            unregister()
            overlay.dispose()
        }
    }

    LaunchedEffect(drawingState, overlay) {
        snapshotFlow { drawingState.isDrawing.value }
            .collect { drawing ->
                if (drawing) {
                    overlay.clearInk()
                } else {
                    delay(HANDOFF_HOLD_MS)
                    overlay.clearInk()
                }
            }
    }
}

@Composable
actual fun rememberLowLatencyOverlayAvailable(): Boolean = remember { Platform.isWindows() }

@Composable
actual fun rememberLowLatencyOverlayMaxDimensionPx(): Int = Int.MAX_VALUE

private class DesktopLiveInkOverlay {
    private val panel = LiveInkPanel()
    private val window =
        JWindow().apply {
            name = OVERLAY_WINDOW_NAME
            background = Color(0, 0, 0, 0)
            contentPane = panel
            isAlwaysOnTop = true
            isFocusable = false
            focusableWindowState = false
            type = Window.Type.UTILITY
        }

    private var visible = false

    fun show() {
        runOnEdt {
            if (!visible && panel.hasViewport) {
                window.isVisible = true
                visible = true
                applyClickThrough(window)
            }
        }
    }

    fun updateBounds(
        bounds: Rect,
        viewportScale: Float,
    ) {
        val scale = viewportScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val page =
            IntRect(
                left = (bounds.left * scale).roundToInt(),
                top = (bounds.top * scale).roundToInt(),
                right = (bounds.right * scale).roundToInt(),
                bottom = (bounds.bottom * scale).roundToInt(),
            )
        if (page.width <= 1 || page.height <= 1) return
        runOnEdt {
            val area = contentAreaFor(window, page)
            val visiblePage = area?.let { page.intersection(IntRect(0, 0, it.width, it.height)) }
            if (area == null || visiblePage == null || visiblePage.width <= 1 || visiblePage.height <= 1) {
                window.isVisible = false
                visible = false
                panel.clearViewport()
                return@runOnEdt
            }
            panel.updateViewport(
                ViewportMapping(
                    fullWidthPx = page.width,
                    fullHeightPx = page.height,
                    offsetX = visiblePage.left - page.left,
                    offsetY = visiblePage.top - page.top,
                ),
            )
            window.setBounds(
                area.origin.x + visiblePage.left,
                area.origin.y + visiblePage.top,
                visiblePage.width,
                visiblePage.height,
            )
            panel.setSize(visiblePage.width, visiblePage.height)
            if (!visible) {
                window.isVisible = true
                visible = true
                applyClickThrough(window)
            }
        }
    }

    fun render(sample: LiveStrokeSample) {
        runOnEdt { panel.render(sample) }
    }

    fun clearInk() {
        runOnEdt { panel.clearInk() }
    }

    fun dispose() {
        runOnEdt {
            panel.clearInk()
            panel.clearViewport()
            window.isVisible = false
            window.dispose()
            visible = false
        }
    }
}

private class LiveInkPanel : JComponent() {
    private val segments = ArrayList<StrokeSegment>(512)
    private val scratch = Path2D.Float()
    private var viewport: ViewportMapping? = null

    val hasViewport: Boolean
        get() = viewport != null

    init {
        isOpaque = false
        isFocusable = false
        isDoubleBuffered = false
        isRequestFocusEnabled = false
        isEnabled = false
    }

    fun updateViewport(value: ViewportMapping) {
        if (viewport != value) {
            viewport = value
            segments.clear()
            if (width > 0 && height > 0) paintImmediately(0, 0, width, height)
        }
    }

    fun clearViewport() {
        viewport = null
        clearInk()
    }

    fun render(sample: LiveStrokeSample) {
        val currentViewport = viewport ?: return
        if (width <= 0 || height <= 0) return
        if (sample.previous == null) segments.clear()
        segments.add(sample.toSegment(currentViewport.fullWidthPx.toFloat()))
        paintImmediately(0, 0, width, height)
    }

    fun clearInk() {
        segments.clear()
        if (width > 0 && height > 0) paintImmediately(0, 0, width, height)
    }

    override fun paintComponent(g: Graphics) {
        val currentViewport = viewport ?: return
        val g2 = g.create() as Graphics2D
        try {
            g2.composite = AlphaComposite.Clear
            g2.fillRect(0, 0, width, height)
            g2.composite = AlphaComposite.SrcOver
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            for (segment in segments) {
                drawSegment(g2, segment, currentViewport, scratch)
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
    viewport: ViewportMapping,
    scratch: Path2D.Float,
) {
    val prev = segment.prev
    val curr = segment.curr
    val ext = segment.extent
    val pdfW = if (ext.width > 0f) viewport.fullWidthPx / ext.width else viewport.fullWidthPx.toFloat()
    val pdfH = if (ext.height > 0f) viewport.fullHeightPx / ext.height else viewport.fullHeightPx.toFloat()
    val x = (curr.x - ext.left) * pdfW - viewport.offsetX
    val y = (curr.y - ext.top) * pdfH - viewport.offsetY

    g.color = Color(segment.colorArgb, true)
    if (segment.toolKind == ToolKind.MARKER) {
        if (prev == null) return
        val halfWidthPx = segment.widthPx * 0.5f
        if (halfWidthPx <= 0f) return
        val nibX = cos(MARKER_NIB_ANGLE_RADIANS) * halfWidthPx
        val nibY = sin(MARKER_NIB_ANGLE_RADIANS) * halfWidthPx
        val x1 = (prev.x - ext.left) * pdfW - viewport.offsetX
        val y1 = (prev.y - ext.top) * pdfH - viewport.offsetY
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
        val x1 = (prev.x - ext.left) * pdfW - viewport.offsetX
        val y1 = (prev.y - ext.top) * pdfH - viewport.offsetY
        scratch.reset()
        scratch.moveTo(x1.toDouble(), y1.toDouble())
        scratch.lineTo(x.toDouble(), y.toDouble())
        g.draw(scratch)
    }
}

private fun runOnEdt(block: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) {
        block()
    } else {
        SwingUtilities.invokeLater(block)
    }
}

private fun applyClickThrough(window: Window) {
    runCatching {
        val hwnd = HWND(Native.getComponentPointer(window))
        val user32 = Native.load("user32", User32Ext::class.java)
        val style = user32.GetWindowLong(hwnd, GWL_EXSTYLE).toInt()
        val newStyle =
            (style or WS_EX_LAYERED or WS_EX_TRANSPARENT or WS_EX_NOACTIVATE or WS_EX_TOOLWINDOW)
                .toLong()
        user32.SetWindowLong(
            hwnd,
            GWL_EXSTYLE,
            LONG(newStyle),
        )
    }
}

private fun contentAreaFor(
    overlay: Window,
    page: IntRect,
): ContentArea? =
    Window.getWindows()
        .asSequence()
        .filter { it.isShowing && it !== overlay && it.name != OVERLAY_WINDOW_NAME }
        .mapNotNull { window -> window.contentAreaOnScreen() }
        .maxByOrNull { area ->
            val intersection = page.intersection(IntRect(0, 0, area.width, area.height))
            intersection?.area ?: 0L
        }
        ?.takeIf { area ->
            page.intersection(IntRect(0, 0, area.width, area.height)) != null
        }

private data class ContentArea(
    val origin: Point,
    val width: Int,
    val height: Int,
)

private data class IntRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = width.toLong() * height.toLong()

    fun intersection(other: IntRect): IntRect? {
        val l = maxOf(left, other.left)
        val t = maxOf(top, other.top)
        val r = minOf(right, other.right)
        val b = minOf(bottom, other.bottom)
        return if (r > l && b > t) IntRect(l, t, r, b) else null
    }
}

private fun Window.contentAreaOnScreen(): ContentArea? {
    val content = (this as? RootPaneContainer)?.contentPane ?: this
    if (!content.isShowing || content.width <= 0 || content.height <= 0) return null
    return runCatching {
        ContentArea(
            origin = content.locationOnScreen,
            width = content.width,
            height = content.height,
        )
    }.getOrNull()
}

private interface User32Ext : StdCallLibrary {
    fun GetWindowLong(
        hWnd: HWND,
        nIndex: Int,
    ): LONG

    fun SetWindowLong(
        hWnd: HWND,
        nIndex: Int,
        dwNewLong: LONG,
    ): LONG
}
