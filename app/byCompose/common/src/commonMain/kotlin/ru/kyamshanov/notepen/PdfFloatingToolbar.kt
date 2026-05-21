package ru.kyamshanov.notepen

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ToolToggleButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics {
            role = Role.Button
            this.selected = selected
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (selected) "$contentDescription (активен)" else contentDescription,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
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
fun nextToolModeOnToggle(current: ToolMode, requested: ToolMode): ToolMode = when {
    requested == ToolMode.NONE -> ToolMode.NONE
    current == requested -> ToolMode.NONE
    else -> requested
}

internal const val MIN_SCALE = 25
internal const val MAX_SCALE = 800

private val TOOLBAR_PADDING = 4.dp

/** Ширина вертикального тулбара: кнопка 48dp + отступы по бокам. */
internal val FLOATING_TOOLBAR_WIDTH = 48.dp + TOOLBAR_PADDING * 2
