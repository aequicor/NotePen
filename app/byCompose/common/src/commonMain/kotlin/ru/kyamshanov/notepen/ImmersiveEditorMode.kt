package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable

/**
 * Hides the platform system bars (the status bar with the clock and the
 * navigation bar) for as long as this composable stays in the composition,
 * giving the editor the whole screen for drawing. The bars remain reachable
 * via a swipe from the screen edge and are restored automatically once the
 * editor leaves the composition.
 *
 * Desktop has no system bars — the actual is a no-op there.
 */
@Composable
expect fun ImmersiveEditorMode()
