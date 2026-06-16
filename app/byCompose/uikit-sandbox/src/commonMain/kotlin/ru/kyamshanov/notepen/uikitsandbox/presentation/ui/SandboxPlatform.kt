package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import androidx.compose.runtime.Composable

/**
 * Применяет выбранную ориентацию standalone sandbox на текущей платформе.
 *
 * На Android actual меняет Activity orientation, на Desktop actual является no-op.
 *
 * @param orientation выбранная ориентация preview.
 */
@Composable
internal expect fun SandboxApplyOrientation(orientation: SandboxPreviewOrientation)
