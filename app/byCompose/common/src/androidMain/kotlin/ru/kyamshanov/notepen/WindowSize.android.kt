package ru.kyamshanov.notepen

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Размер окна из активной `Configuration`. `screenWidthDp`/`screenHeightDp`
 * обновляются на каждый поворот (Activity получает `onConfigurationChanged` даже
 * при `configChanges`), поэтому ориентация не «залипает» на прошлой, как это бывало
 * с `WindowInfo.containerSize` на Huawei/EMUI.
 */
@Composable
actual fun currentWindowSizePx(): IntSize {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    return IntSize(
        width = (configuration.screenWidthDp * density).roundToInt(),
        height = (configuration.screenHeightDp * density).roundToInt(),
    )
}
