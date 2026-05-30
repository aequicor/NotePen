package ru.kyamshanov.notepen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Icon toggle for the tool wheel. When [selected] the button fills with the
 * accent [ColorScheme.primary] and the icon switches to `onPrimary`, so the active
 * toggle stands out clearly against the cream rail — the pale `primaryContainer`
 * tint used previously was nearly invisible on the surface.
 *
 * @param showSelectionBackground draw this button's own circular `primary`
 *   indicator when selected. Independent toggles (magnifier, thumbnails…) keep it
 *   `true`; the mutually-exclusive drawing tools pass `false` because the wheel
 *   draws one shared indicator that physically slides between them.
 * @param enabled when `false` the button is non-interactive and its icon is
 *   dimmed — e.g. reading mode on an image-only PDF with no selectable text.
 */
@Composable
internal fun ToolToggleButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showSelectionBackground: Boolean = true,
    enabled: Boolean = true,
) {
    val indicatorColor by animateColorAsState(
        targetValue =
            if (selected && showSelectionBackground && enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            },
        label = "toolIndicator",
    )
    val iconTint by animateColorAsState(
        targetValue =
            when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TOOL_DISABLED_ALPHA)
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        label = "toolIconTint",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .size(RAIL_BUTTON_SIZE)
                .semantics {
                    role = Role.Button
                    this.selected = selected
                },
    ) {
        Box(
            modifier =
                Modifier
                    .size(RAIL_INDICATOR_SIZE)
                    .clip(CircleShape)
                    .background(indicatorColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (selected) "$contentDescription (активен)" else contentDescription,
                tint = iconTint,
            )
        }
    }
}

/**
 * Pure state-mapping helper for [PdfFloatingToolbar] tool buttons:
 *  - tap on the active tool → return to [ToolMode.NONE] (toggle off);
 *  - tap on the inactive tool → switch to it (mutual exclusion);
 *  - explicit [ToolMode.NONE] request → always [ToolMode.NONE].
 *
 * Extracted so toggle semantics stay testable without compose-ui-test infra.
 * Verifies AC-2 / AC-3 toggle / mutual-exclusion semantics.
 */
fun nextToolModeOnToggle(
    current: ToolMode,
    requested: ToolMode,
): ToolMode =
    when {
        requested == ToolMode.NONE -> ToolMode.NONE
        current == requested -> ToolMode.NONE
        else -> requested
    }

internal const val MIN_SCALE = 25
internal const val MAX_SCALE = 800

/** Compact touch target for icon buttons inside the tool wheel (denser than the 48dp default). */
internal val RAIL_BUTTON_SIZE = 40.dp

/** Selected-tool circular indicator — same size as the button's round hover/ripple state layer. */
private val RAIL_INDICATOR_SIZE = RAIL_BUTTON_SIZE

/** Icon-tint alpha for a disabled [ToolToggleButton] (matches Material's disabled affordance). */
private const val TOOL_DISABLED_ALPHA = 0.38f

private val TOOLBAR_PADDING = 4.dp

/** Ширина вертикального тулбара: кнопка 48dp + отступы по бокам. */
internal val FLOATING_TOOLBAR_WIDTH = 48.dp + TOOLBAR_PADDING * 2
