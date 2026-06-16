package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import androidx.compose.runtime.Composable

/**
 * Desktop/JVM actual ориентации sandbox.
 *
 * Desktop-приложение не меняет ориентацию окна, поэтому toggle влияет только на
 * aspect ratio preview-рамки в common UI.
 *
 * @param orientation выбранная ориентация sandbox preview.
 */
@Composable
internal actual fun SandboxApplyOrientation(orientation: SandboxPreviewOrientation) {
    Unit
}
