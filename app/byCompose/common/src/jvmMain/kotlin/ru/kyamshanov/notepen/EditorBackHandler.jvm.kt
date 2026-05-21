package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable

/** Desktop has no system back action — back is driven by explicit UI controls. */
@Composable
actual fun EditorBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // no-op
}
