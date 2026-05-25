package ru.kyamshanov.notepen.titlebar

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pluggable hook that lets platform-specific code wire Compose pointer input to OS
 * window-chrome APIs (e.g. JBR [CustomTitleBar.forceHitTest]).
 *
 * - [dragArea] — apply to regions that should drag the window on pointer press.
 * - [interactive] — apply to clickable / focusable elements inside the title-bar
 *   area so the OS does not intercept their events as window-drag.
 *
 * Both functions are non-consuming wrappers: they only communicate with the OS
 * and leave pointer events unconsumed for normal Compose handling.
 */
interface TitleBarInteraction {
    /** Marks [modifier]'s node as a window-drag zone. */
    fun dragArea(modifier: Modifier): Modifier

    /** Marks [modifier]'s node as an interactive (non-drag) element. */
    fun interactive(modifier: Modifier): Modifier
}

/**
 * Provided by the desktop entry point when JBR window decorations are
 * available. `null` on platforms where no such API exists (Android, Linux
 * without JBR runtime).
 */
val LocalTitleBarInteraction = staticCompositionLocalOf<TitleBarInteraction?> { null }

/**
 * Left inset in dp reserved for OS window controls that overlay the title-bar
 * area (macOS traffic lights). Zero when not applicable.
 */
val LocalTitleBarStartInset = staticCompositionLocalOf<Dp> { 0.dp }

/**
 * Right inset in dp reserved for OS window controls that overlay the title-bar
 * area (Windows caption buttons: minimize / maximize / close). Zero when not
 * applicable (e.g. macOS, where controls sit on the leading edge).
 */
val LocalTitleBarEndInset = staticCompositionLocalOf<Dp> { 0.dp }
