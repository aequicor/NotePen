package ru.kyamshanov.notepen

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.CubicResampler
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.SamplingMode
import java.util.IdentityHashMap

private const val MAX_NATIVE_IMAGE_CACHE_ENTRIES = 96
private const val MAX_NATIVE_IMAGE_CACHE_PIXELS = 128_000_000L

private object NativeImageStore {
    private data class Entry(
        val width: Int,
        val height: Int,
        val image: Image,
        var activeUseCount: Int = 0,
    )

    private val entries = IdentityHashMap<ImageBitmap, Entry>()
    private val lru = ArrayList<ImageBitmap>(MAX_NATIVE_IMAGE_CACHE_ENTRIES)

    fun imageFor(bitmap: ImageBitmap): Image {
        val cached = synchronized(this) { existingEntryLocked(bitmap)?.image }
        if (cached != null) return cached

        val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        var duplicateToClose: Image? = null
        val result =
            synchronized(this) {
                val existing = existingEntryLocked(bitmap)
                if (existing != null) {
                    duplicateToClose = image
                    existing.image
                } else {
                    entries[bitmap] = Entry(bitmap.width, bitmap.height, image)
                    markUsed(bitmap)
                    trimToSize(protectedBitmap = bitmap)
                    image
                }
            }
        duplicateToClose?.close()
        return result
    }

    fun <T> withImage(
        bitmap: ImageBitmap,
        block: (Image) -> T,
    ): T {
        val image = acquireImage(bitmap)
        try {
            return block(image)
        } finally {
            releaseImage(bitmap, image)
        }
    }

    private fun acquireImage(bitmap: ImageBitmap): Image {
        val cached =
            synchronized(this) {
                existingEntryLocked(bitmap)?.let { entry ->
                    entry.activeUseCount += 1
                    entry.image
                }
            }
        if (cached != null) return cached

        val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        var duplicateToClose: Image? = null
        val result =
            synchronized(this) {
                val existing = existingEntryLocked(bitmap)
                if (existing != null) {
                    existing.activeUseCount += 1
                    duplicateToClose = image
                    existing.image
                } else {
                    entries[bitmap] =
                        Entry(
                            width = bitmap.width,
                            height = bitmap.height,
                            image = image,
                            activeUseCount = 1,
                        )
                    markUsed(bitmap)
                    trimToSize(protectedBitmap = bitmap)
                    image
                }
            }
        duplicateToClose?.close()
        return result
    }

    private fun releaseImage(
        bitmap: ImageBitmap,
        image: Image,
    ) {
        synchronized(this) {
            val entry = entries[bitmap]
            if (entry?.image === image && entry.activeUseCount > 0) {
                entry.activeUseCount -= 1
                trimToSize(protectedBitmap = null)
            }
        }
    }

    private fun existingEntryLocked(bitmap: ImageBitmap): Entry? {
        entries[bitmap]?.let { entry ->
            if (entry.width == bitmap.width && entry.height == bitmap.height && !entry.image.isClosed) {
                markUsed(bitmap)
                return entry
            }
            entries.remove(bitmap)?.image?.close()
            lru.remove(bitmap)
        }
        return null
    }

    private fun markUsed(bitmap: ImageBitmap) {
        lru.remove(bitmap)
        lru += bitmap
    }

    private fun trimToSize(protectedBitmap: ImageBitmap?) {
        while (lru.size > MAX_NATIVE_IMAGE_CACHE_ENTRIES || totalPixels() > MAX_NATIVE_IMAGE_CACHE_PIXELS) {
            val evicted =
                lru.firstOrNull { candidate ->
                    candidate !== protectedBitmap && entries[candidate]?.activeUseCount == 0
                } ?: break
            lru.remove(evicted)
            entries.remove(evicted)?.image?.close()
        }
    }

    private fun totalPixels(): Long {
        var total = 0L
        for (entry in entries.values) {
            total += entry.width.toLong() * entry.height
        }
        return total
    }
}

internal actual class NativeImageDrawCache actual constructor() {
    private val paint = Paint()

    fun imageFor(bitmap: ImageBitmap): Image = NativeImageStore.imageFor(bitmap)

    fun <T> withImage(
        bitmap: ImageBitmap,
        block: (Image) -> T,
    ): T = NativeImageStore.withImage(bitmap, block)

    fun skiaPaint(
        blendMode: BlendMode,
        filterQuality: FilterQuality,
    ): org.jetbrains.skia.Paint {
        paint.blendMode = blendMode
        paint.filterQuality = filterQuality
        return paint.skiaPaint
    }

    actual fun close() = Unit
}

internal actual fun prewarmNativeImage(image: ImageBitmap) {
    NativeImageStore.imageFor(image)
}

internal actual fun DrawScope.drawNativeCachedImage(
    cache: NativeImageDrawCache,
    image: ImageBitmap,
    srcOffset: IntOffset,
    srcSize: IntSize,
    dstOffset: IntOffset,
    dstSize: IntSize,
    blendMode: BlendMode,
    filterQuality: FilterQuality,
) {
    val srcLeft = srcOffset.x.toFloat()
    val srcTop = srcOffset.y.toFloat()
    val dstLeft = dstOffset.x.toFloat()
    val dstTop = dstOffset.y.toFloat()
    cache.withImage(image) { skiaImage ->
        drawContext.canvas.skiaCanvas.drawImageRect(
            image = skiaImage,
            srcLeft = srcLeft,
            srcTop = srcTop,
            srcRight = srcLeft + srcSize.width.toFloat(),
            srcBottom = srcTop + srcSize.height.toFloat(),
            dstLeft = dstLeft,
            dstTop = dstTop,
            dstRight = dstLeft + dstSize.width.toFloat(),
            dstBottom = dstTop + dstSize.height.toFloat(),
            samplingMode = filterQuality.toSkiaSampling(),
            paint = cache.skiaPaint(blendMode, filterQuality),
            strict = true,
        )
    }
}

private fun FilterQuality.toSkiaSampling(): SamplingMode =
    when (this) {
        FilterQuality.Low -> FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)
        FilterQuality.Medium -> FilterMipmap(FilterMode.LINEAR, MipmapMode.NEAREST)
        FilterQuality.High -> CubicResampler(1 / 3.0f, 1 / 3.0f)
        else -> FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
    }
