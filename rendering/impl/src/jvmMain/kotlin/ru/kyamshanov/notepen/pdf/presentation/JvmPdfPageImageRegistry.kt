package ru.kyamshanov.notepen.pdf.presentation

import ru.kyamshanov.notepen.pdf.domain.model.PdfPageData
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference

private const val MAX_REGISTERED_IMAGES = 32

private data class RegisteredPageImage(
    val pageData: WeakReference<PdfPageData>,
    val image: BufferedImage,
)

private val registeredImages = ArrayDeque<RegisteredPageImage>()

/**
 * JVM-only fast path for renderers that already have the final [BufferedImage].
 *
 * [PdfPageData] stays as the common API boundary, but desktop conversion can
 * avoid materialising `IntArray -> BufferedImage` again when the renderer
 * registers the image associated with this exact page-data instance.
 */
fun PdfPageData.withJvmBufferedImage(image: BufferedImage): PdfPageData {
    require(image.width == widthPx && image.height == heightPx) {
        "Registered image size ${image.width}x${image.height} does not match PdfPageData ${widthPx}x$heightPx"
    }
    synchronized(registeredImages) {
        pruneRegisteredImages()
        registeredImages.addLast(RegisteredPageImage(WeakReference(this), image))
        while (registeredImages.size > MAX_REGISTERED_IMAGES) registeredImages.removeFirst()
    }
    return this
}

internal fun PdfPageData.findJvmBufferedImage(): BufferedImage? =
    synchronized(registeredImages) {
        pruneRegisteredImages()
        registeredImages.firstOrNull { it.pageData.get() === this }?.image
    }

private fun pruneRegisteredImages() {
    registeredImages.removeAll { it.pageData.get() == null }
}
