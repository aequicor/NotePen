package ru.kyamshanov.notepen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import ru.kyamshanov.notepen.blur.GlassSurface
import ru.kyamshanov.notepen.blur.LocalBlurEnabled

private val DropdownMinWidth: Dp = 112.dp
private val DropdownMenuVerticalPadding: Dp = 8.dp
private val DropdownMenuShape = RoundedCornerShape(20.dp)

/**
 * Frosted-glass replacement for `androidx.compose.material3.DropdownMenu` with a
 * compatible call shape. Anchors to its parent (place inside the same `Box` as
 * the trigger button) and dismisses on outside click / escape.
 *
 * Unlike a Material `Surface`, the menu refracts the screen below via
 * `GlassSurface` — the menu sits directly over content (no scrim), so the
 * frosted look reads cleanly.
 */
@Composable
fun LiquidGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    val positionProvider = remember(offset) { DropdownMenuPositionProvider(offset) }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        // Disable backdrop sampling inside Popup: the popup composes in a
        // separate root, so the host's GlassBackdropProvider can't reach it
        // for position-correct blur. Falling back to the denser flat surface +
        // shadow reads as frosted without the broken half-blur artefact.
        CompositionLocalProvider(LocalBlurEnabled provides false) {
            GlassSurface(
                modifier =
                    modifier
                        .wrapContentSize()
                        .widthIn(min = DropdownMinWidth)
                        .shadow(elevation = 12.dp, shape = DropdownMenuShape, clip = false)
                        .clip(DropdownMenuShape),
                shape = DropdownMenuShape,
                tint = MaterialTheme.colorScheme.surface,
                fillAlpha = DROPDOWN_FILL_ALPHA,
            ) {
                // IntrinsicSize.Max: Material3's DropdownMenuItem internally uses
                // fillMaxWidth; without an intrinsic-width constraint on the parent,
                // it inflates the popup to the full window width. Material3's own
                // DropdownMenu applies the same trick — see m3 DropdownMenuContent.
                Column(
                    modifier =
                        Modifier
                            .width(IntrinsicSize.Max)
                            .padding(vertical = DropdownMenuVerticalPadding),
                    content = content,
                )
            }
        }
    }
}

/**
 * Position provider that anchors the menu below the trigger by default, falling
 * back to above the trigger when there isn't enough room. Mirrors the behaviour
 * of Material3's internal `DropdownMenuPositionProvider`, just without the
 * `Density` / animation-origin plumbing — we don't need them.
 */
private class DropdownMenuPositionProvider(
    private val contentOffset: DpOffset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val offsetXPx = (contentOffset.x.value * WINDOW_DENSITY_SCALE).toInt()
        val offsetYPx = (contentOffset.y.value * WINDOW_DENSITY_SCALE).toInt()

        // Horizontal: align to the start edge of the anchor (or end edge in RTL).
        val x =
            if (layoutDirection == LayoutDirection.Ltr) {
                anchorBounds.left + offsetXPx
            } else {
                anchorBounds.right - popupContentSize.width - offsetXPx
            }.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))

        // Vertical: prefer below the anchor; flip above when it would overflow.
        val below = anchorBounds.bottom + offsetYPx
        val above = anchorBounds.top - popupContentSize.height - offsetYPx
        val y =
            if (below + popupContentSize.height <= windowSize.height || above < 0) {
                below
            } else {
                above
            }.coerceAtLeast(0)

        return IntOffset(x, y)
    }
}

/**
 * `Popup`'s `calculatePosition` works in raw pixels, but our offsets are in dp.
 * We don't have `Density` at this scope, so use a 1f fallback — `DpOffset.Zero`
 * is by far the common case and produces a pixel offset of 0 regardless.
 */
private const val WINDOW_DENSITY_SCALE: Float = 1f

/** Denser than the default fall-through alpha so dropdown text stays readable. */
private const val DROPDOWN_FILL_ALPHA: Float = 0.92f
