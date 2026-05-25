package ru.kyamshanov.notepen.book

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Верстает комикс (изображения страниц) в PDF на JVM: каждая картинка — одна
 * страница «в край», размер страницы равен размеру изображения. JPEG
 * встраивается без перекодирования ([JPEGFactory]), прочие форматы — через
 * [LosslessFactory]. Нечитаемые изображения пропускаются.
 */
object JvmComicPdfRenderer {
    /**
     * @param images байты изображений страниц в порядке чтения
     * @param output файл назначения PDF (создаётся/перезаписывается)
     */
    fun render(
        images: List<ByteArray>,
        output: File,
    ) {
        PDDocument().use { doc ->
            for (bytes in images) {
                val image = imageOf(doc, bytes) ?: continue
                val page = PDPage(PDRectangle(image.width.toFloat(), image.height.toFloat()))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.drawImage(image, 0f, 0f, image.width.toFloat(), image.height.toFloat())
                }
            }
            if (doc.numberOfPages == 0) doc.addPage(PDPage(PDRectangle.A4))
            output.parentFile?.mkdirs()
            doc.save(output)
        }
    }

    private fun imageOf(
        doc: PDDocument,
        bytes: ByteArray,
    ): PDImageXObject? =
        runCatching {
            if (isJpeg(bytes)) {
                JPEGFactory.createFromStream(doc, ByteArrayInputStream(bytes))
            } else {
                val buffered = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
                LosslessFactory.createFromImage(doc, buffered)
            }
        }.getOrNull()

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
}
