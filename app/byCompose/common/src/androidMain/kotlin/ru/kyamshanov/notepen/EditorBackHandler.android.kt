package ru.kyamshanov.notepen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun EditorBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(enabled = enabled, onBack = onBack)
}
