package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize

/**
 * На десктопе окно свободно меняет размер (нет дискретной «ориентации»), а
 * `WindowInfo.containerSize` обновляется корректно — используем его напрямую.
 */
@Composable
actual fun currentWindowSizePx(): IntSize = LocalWindowInfo.current.containerSize
