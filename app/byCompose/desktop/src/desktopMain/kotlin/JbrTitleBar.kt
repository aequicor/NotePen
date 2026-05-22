package ru.kyamshanov.notepen

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations.CustomTitleBar
import ru.kyamshanov.notepen.tabs.TAB_BAR_HEIGHT
import ru.kyamshanov.notepen.titlebar.TitleBarInteraction
import java.awt.Frame

/**
 * Initialises a JBR [CustomTitleBar] on [window], replacing the OS chrome with
 * the Compose canvas.  The Compose layout fills from (0, 0) — the
 * [ru.kyamshanov.notepen.tabs.TabBar] composables already rendered at the top of
 * each panel occupy the area that was previously the OS title bar.
 *
 * @return A [TitleBarInteraction] + left-inset pair that the caller must provide
 * via [ru.kyamshanov.notepen.titlebar.LocalTitleBarInteraction] and
 * [ru.kyamshanov.notepen.titlebar.LocalTitleBarStartInset], or `null` when JBR
 * decorations are not supported on the current runtime.
 */
internal fun setupJbrTitleBar(window: Frame): Pair<TitleBarInteraction, Dp>? {
    if (!JBR.isWindowDecorationsSupported()) return null
    val tb = JBR.getWindowDecorations().createCustomTitleBar().apply {
        height = TAB_BAR_HEIGHT.value
    }
    JBR.getWindowDecorations().setCustomTitleBar(window, tb)
    return JbrTitleBarInteraction(tb) to tb.leftInset.dp
}

private class JbrTitleBarInteraction(private val titleBar: CustomTitleBar) : TitleBarInteraction {

    /**
     * Registers this node as the window-drag zone.  Every pointer event reaching
     * the node calls [CustomTitleBar.forceHitTest] with `false`, telling the OS
     * "treat this as the title bar, allow dragging". Interactive children that
     * use [interactive] override this per-event with `true`.
     */
    override fun dragArea(modifier: Modifier): Modifier = modifier.pointerInput(titleBar) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                event.changes.forEach { _ -> titleBar.forceHitTest(false) }
            }
        }
    }

    /**
     * Registers this node as an interactive element.  Every pointer event
     * reaching the node calls [CustomTitleBar.forceHitTest] with `true`,
     * preventing the OS from treating the press as a window-drag gesture.
     *
     * Because `Main` pass dispatches from inner to outer nodes, this call fires
     * after [dragArea] for events landing on an interactive child — so `true`
     * wins and the click reaches the component normally.
     */
    override fun interactive(modifier: Modifier): Modifier = modifier.pointerInput(titleBar) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                event.changes.forEach { _ -> titleBar.forceHitTest(true) }
            }
        }
    }
}
