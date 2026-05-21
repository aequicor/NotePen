package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable

/**
 * Intercepts the platform back action (Android system back gesture/button) while
 * the editor is shown, so unsaved annotations can be flushed before navigating
 * away. Desktop has no system back — the actual is a no-op there.
 */
@Composable
expect fun EditorBackHandler(enabled: Boolean, onBack: () -> Unit)
