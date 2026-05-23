package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable

/** Desktop has no system bars to hide — full-screen is user-driven. */
@Composable
actual fun ImmersiveEditorMode() {
    // no-op
}
