package ru.kyamshanov.notepen.reflow.ui.bookcurl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

// Выделенный резолвер шрифтов ТОЛЬКО для закадрового захвата: createFontFamilyResolver() даёт свежий
// FontCache → свой нативный FontCollection (THashMap familyKey→typefaces). Раскладка текста захвата идёт
// на Dispatchers.Default параллельно с раскладкой главного потока ридера; FontCollection НЕ
// потокобезопасен (одновременная вставка/resize THashMap рушит таблицу → SIGSEGV). Свой FontCollection
// для захвата + сериализация захватов [captureMutex] убирают конкурентную мутацию. Резолвер один на все
// захваты (его FontCollection остаётся тёплым), а мьютекс не даёт двум захватам трогать его разом.
private val captureFontFamilyResolver by lazy { createFontFamilyResolver() }
private val captureMutex = Mutex()

/**
 * Растеризует [content] в [ImageBitmap] ВНЕ основной композиции — текстуру страницы для page-curl.
 *
 * Возвращает текстуру, ЯВНО помеченную sRGB. Это важно вдвойне:
 *  1. Гамма-корректное смешивание текста при рендере в sRGB-поверхность (текст не «жирнеет»).
 *  2. Тег sRGB ВЫЖИВАЕТ через `Bitmap.asComposeImageBitmap()` (в отличие от `Image.toComposeImageBitmap()`,
 *     который тег теряет). Без тега на wide-gamut (P3) дисплее GPU-бэкенд Skia рисует текстуру БЕЗ
 *     гамут-конвертации — и весь лист (фон + жёлтая подсветка) выходит теплее/насыщеннее, чем живая
 *     страница. С тегом GPU цвет-менеджит текстуру в пространство экрана так же, как живой текст, и
 *     загиб совпадает со статичной страницей. Тег несёт сам bitmap, поэтому корректны ОБА пути отрисовки
 *     в [BookCurlOverlay] — и меш (front/back), и плоский under-page через `drawImage`.
 */
internal actual suspend fun captureReflowTexture(
    widthPx: Int,
    heightPx: Int,
    density: Density,
    content: @Composable () -> Unit,
): ImageBitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    // Текст захвата раскладываем через ВЫДЕЛЕННЫЙ резолвер шрифтов (свой FontCollection), а сами захваты
    // сериализуем [captureMutex]. Иначе раскладка на Dispatchers.Default и главный поток ридера мутируют
    // ОБЩИЙ нативный FontCollection (THashMap, не потокобезопасный) ⇒ параллельный resize рушит таблицу
    // → SIGSEGV в FontCollection.findTypefaces. Резолвер тот же системный ⇒ текстура визуально идентична.
    val isolatedContent: @Composable () -> Unit = {
        CompositionLocalProvider(LocalFontFamilyResolver provides captureFontFamilyResolver) {
            content()
        }
    }
    // Закадровый рендер не на main: сцена держит собственные часы и безопасно работает на отдельном потоке.
    return withContext(Dispatchers.Default) {
        captureMutex.withLock {
            runCatching {
                renderToSrgbBitmap(widthPx = widthPx, heightPx = heightPx, density = density, content = isolatedContent)
            }.getOrNull()
                ?: runCatching {
                    // Фолбэк, если низкоуровневый путь сломается на будущей версии Compose: штатный
                    // ImageComposeScene отдаёт untagged Image; перерисовываем его в sRGB-поверхность и
                    // снимаем sRGB-bitmap, чтобы текстура всё равно несла тег (см. KDoc выше).
                    val scene =
                        ImageComposeScene(width = widthPx, height = heightPx, density = density, content = isolatedContent)
                    try {
                        val image = scene.render()
                        val surface = newSrgbSurface(widthPx, heightPx)
                        try {
                            surface.canvas.drawImage(image, 0f, 0f)
                            surface.toSrgbComposeBitmap(widthPx, heightPx)
                        } finally {
                            surface.close()
                        }
                    } finally {
                        scene.close()
                    }
                }.getOrNull()
        }
    }
}

/**
 * Рендер [content] в РАСТЕРОВУЮ sRGB-поверхность через низкоуровневую сцену (ImageComposeScene жёстко
 * использует untagged `makeRasterN32Premul`). Снимаем результат sRGB-bitmap'ом — тег сохраняется.
 */
@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
private fun renderToSrgbBitmap(
    widthPx: Int,
    heightPx: Int,
    density: Density,
    content: @Composable () -> Unit,
): ImageBitmap {
    val scene =
        CanvasLayersComposeScene(
            density = density,
            size = IntSize(widthPx, heightPx),
            coroutineContext = Dispatchers.Unconfined,
        )
    return try {
        scene.setContent(content)
        val surface = newSrgbSurface(widthPx, heightPx)
        try {
            surface.canvas.clear(0)
            scene.render(surface.canvas.asComposeCanvas(), nanoTime = 0L)
            surface.toSrgbComposeBitmap(widthPx, heightPx)
        } finally {
            surface.close()
        }
    } finally {
        scene.close()
    }
}

private fun newSrgbSurface(
    widthPx: Int,
    heightPx: Int,
): Surface = Surface.makeRaster(ImageInfo(ColorInfo(ColorType.N32, ColorAlphaType.PREMUL, ColorSpace.sRGB), widthPx, heightPx))

/** Снимает пиксели sRGB-поверхности в sRGB-помеченный [ImageBitmap]; тег несёт сам bitmap. */
private fun Surface.toSrgbComposeBitmap(
    widthPx: Int,
    heightPx: Int,
): ImageBitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(ColorInfo(ColorType.N32, ColorAlphaType.PREMUL, ColorSpace.sRGB), widthPx, heightPx))
    readPixels(bitmap, 0, 0)
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}
