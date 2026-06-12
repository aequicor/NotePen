package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer

/**
 * Снимает текстуру кёрла с живого страничного [GraphicsLayer] (записан в `captureToLayer`).
 *
 * Это запасной путь после [captureReflowTexture]: на Desktop закадровая сцена — эталон
 * (sRGB-тег, чистое состояние выделения), а слой выручает тап, опередивший префетч; на
 * Android закадровой сцены нет, и слой — основной источник. `GraphicsLayer.toImageBitmap()`
 * на Android ненадёжен (вторичный HardwareRenderer на части устройств отдаёт пустой кадр) —
 * там слой отрисовывается софтверно в ARGB_8888-битмап.
 *
 * Контракт обеих реализаций:
 *  - полностью прозрачный кадр (слой не отрисовался) → null, чтобы вызывающий код повторил
 *    попытку или деградировал до перелистывания без кёрла, а пустышка не осела в кэше;
 *  - под непустой контент подкладывается [drawBackground] (бумага): слой пейджера содержит
 *    только контент на прозрачном фоне (бумагу рисует родитель), а лист кёрла обязан быть
 *    непрозрачным, иначе изнанка просвечивает под-страницей.
 */
internal expect suspend fun snapshotBookCurlLayer(
    layer: GraphicsLayer,
    drawBackground: DrawScope.() -> Unit,
): ImageBitmap?
