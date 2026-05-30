package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntSize

/**
 * Текущий размер окна (в пикселях) из НАИБОЛЕЕ достоверного для платформы источника.
 *
 * На Android берётся из активной `Configuration` (`screenWidthDp`/`screenHeightDp` ×
 * density): она обновляется при КАЖДОЙ смене конфигурации, включая поворот, даже
 * когда Activity сама обрабатывает `configChanges` и не пересоздаётся. Это надёжнее,
 * чем `androidx.compose.ui.platform.WindowInfo.containerSize`, который на части
 * OEM-прошивок (например, Huawei/EMUI) «отстаёт» на один поворот — из-за чего
 * раскладка редактора (боковая рельса в альбомной / верхний бар в портретной)
 * показывала предыдущую ориентацию.
 *
 * На десктопе понятия «ориентация» нет, окно свободно меняет размер — там источником
 * остаётся `WindowInfo.containerSize`.
 */
@Composable
expect fun currentWindowSizePx(): IntSize
