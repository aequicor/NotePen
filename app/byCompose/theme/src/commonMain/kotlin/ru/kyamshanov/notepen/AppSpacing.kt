package ru.kyamshanov.notepen

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppSpacing(
    /** Material3 minimum touch target / WCAG 2.5.5 */
    val touchTarget: Dp,
    /** Material3 standard screen-edge margin */
    val screenEdge: Dp,
)

internal val LocalAppSpacing =
    staticCompositionLocalOf {
        AppSpacing(
            touchTarget = 48.dp,
            screenEdge = 16.dp,
        )
    }

internal val DefaultAppSpacing =
    AppSpacing(
        touchTarget = 48.dp,
        screenEdge = 16.dp,
    )
