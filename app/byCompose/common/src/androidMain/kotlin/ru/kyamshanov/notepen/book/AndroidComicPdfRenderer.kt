package ru.kyamshanov.notepen.book

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Верстает комикс (изображения страниц) в PDF на Android: каждая картинка —
 * одна страница «в край», размер страницы равен размеру изображения.
 * Нечитаемые изображения пропускаются; растровые буферы освобождаются сразу
 * после отрисовки страницы.
 */
object AndroidComicPdfRenderer {
    private const val FALLBACK_WIDTH = 595 // A4 in points
    private const val FALLBACK_HEIGHT = 842

    /**
     * @param images байты изображений страниц в порядке чтения
     * @param output файл назначения PDF (создаётся/перезаписывается)
     */
    fun render(
        images: List<ByteArray>,
        output: File,
    ) {
        if (images.size > BookLimits.MAX_COMIC_IMAGES) {
            throw DocumentTooLargeException("Comic has more than ${BookLimits.MAX_COMIC_IMAGES} images")
        }
        val pdf = PdfDocument()
        var pageNumber = 1
        for (bytes in images) {
            if (bytes.size > BookLimits.MAX_ENTRY_BYTES) {
                throw DocumentTooLargeException("Comic image exceeds ${BookLimits.MAX_ENTRY_BYTES} bytes")
            }
            val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() ?: continue
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, pageNumber).create())
            page.canvas.drawBitmap(bitmap, 0f, 0f, null)
            pdf.finishPage(page)
            bitmap.recycle()
            pageNumber++
        }
        if (pageNumber == 1) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(FALLBACK_WIDTH, FALLBACK_HEIGHT, 1).create())
            pdf.finishPage(page)
        }

        output.parentFile?.mkdirs()
        try {
            FileOutputStream(output).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }
}
