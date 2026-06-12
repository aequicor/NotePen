package ru.kyamshanov.notepen.reflow.ui.bookcurl

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val logger = KotlinLogging.logger {}

// Софтверная отрисовка записанного слоя, а не GraphicsLayer.toImageBitmap(): toImageBitmap на
// API 28+ рендерит RenderNode слоя ВТОРИЧНЫМ HardwareRenderer'ом (Bitmap.createBitmap(Picture)),
// который на части устройств отдаёт кадр без содержимого — это и был «матовый лист» вместо
// текста при книжном перелистывании. Софтверный канвас идёт другим механизмом: Compose
// ПЕРЕИСПОЛНЯЕТ записанный блок слоя (вместе с вложенными слоями элементов LazyColumn) — путь,
// которым пользуется сам Compose (LayerSnapshotV21), детерминированный на любом устройстве.
// Содержимое страниц reflow софтверно-безопасно: все битмапы внутри ARGB_8888, RenderEffect на
// страницах не применяется. Бонус: результат сразу софтверный ARGB_8888 — toSoftwareBookCurlBitmap
// в рендерере/кроппере не делает покадровых копий, как делал бы с HARDWARE-кадром toImageBitmap.
internal actual suspend fun snapshotBookCurlLayer(
    layer: GraphicsLayer,
    drawBackground: DrawScope.() -> Unit,
): ImageBitmap? {
    val size = layer.size
    if (size.width <= 0 || size.height <= 0) return null
    return withContext(Dispatchers.Main.immediate) {
        val content = drawLayerToSoftwareBitmap(layer) ?: hardwareSnapshot(layer) ?: return@withContext null
        // Полностью прозрачный кадр = содержимое слоя не отрисовалось. null вместо кэширования:
        // ensureCurlImage/префетч повторят попытку, а не заморозят пустую текстуру навсегда.
        val texture = if (content.hasInkContent()) opaqueTexture(content, drawBackground) else null
        // Промежуточный полнокадровый битмап (≈10—20 МБ) больше не нужен — освобождаем сразу,
        // не дожидаясь GC; он приватный для этой функции на обоих путях получения.
        content.asAndroidBitmap().recycle()
        texture
    }
}

private fun drawLayerToSoftwareBitmap(layer: GraphicsLayer): ImageBitmap? =
    runCatching {
        renderToImageBitmap(layer.size.width, layer.size.height) { drawLayer(layer) }
    }.onFailure { error ->
        logger.warn(error) { "PdfReflow: book-curl software layer snapshot failed, falling back to toImageBitmap" }
    }.getOrNull()

private suspend fun hardwareSnapshot(layer: GraphicsLayer): ImageBitmap? =
    runCatching {
        // HARDWARE-кадр сразу копируем в ARGB_8888: getPixel для проверки на «чернила» на
        // HARDWARE-конфиге бросает, а кроппер/рендерер всё равно сделали бы эту копию позже.
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        val software =
            if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        software?.asImageBitmap()
    }.onFailure { error ->
        logger.warn(error) { "PdfReflow: book-curl toImageBitmap snapshot failed" }
    }.getOrNull()

// Слой страницы — контент на ПРОЗРАЧНОМ фоне (бумагу рисует родитель пейджера, в слой она не
// попадает), а лист кёрла обязан быть непрозрачным — иначе изнанка просвечивает под-страницей
// (текст поверх текста). Подкладываем тот же фон, что закадровый захват на Desktop.
private fun opaqueTexture(
    content: ImageBitmap,
    drawBackground: DrawScope.() -> Unit,
): ImageBitmap =
    renderToImageBitmap(content.width, content.height) {
        drawBackground()
        drawImage(content)
    }

// Проверка ИСЧЕРПЫВАЮЩАЯ (каждый пиксель, с ранним выходом), а не по решётке: разрежённая
// сетка детерминированно промахивалась мимо страниц с парой строк (хвост документа) — валидная
// текстура навсегда отбраковывалась, и кёрл на таких страницах молча выключался. Страница с
// текстом у верха выходит за первые ~сотню строк; полный скан только у честно пустых кадров.
private fun ImageBitmap.hasInkContent(): Boolean {
    val bitmap = asAndroidBitmap()
    val row = IntArray(width)
    for (y in 0 until height) {
        bitmap.getPixels(row, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            if (row[x] != 0) return true
        }
    }
    logger.debug { "PdfReflow: book-curl layer snapshot is fully transparent (blank), discarding" }
    return false
}
