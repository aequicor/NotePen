package ru.kyamshanov.notepen

import androidx.compose.ui.geometry.Size
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.IntSize
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import java.awt.image.BufferedImage

class PdfManagerJvm(private val filePath: String) : PdfManager {

    private val logger = KotlinLogging.logger {}

    private val document: PDDocument
    private val renderer: PDFRenderer

    init {
        try {
            document = Loader.loadPDF(File(filePath))
            renderer = PDFRenderer(document)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override val metadata: PdfInfo by lazy { loadPdfInfo() }

    /**
     * Загружает и парсит информацию о PDF документе
     * @return полная информация о PDF
     * @throws IllegalArgumentException если файл не существует или не является PDF
     * @throws SecurityException если нет прав доступа к файлу
     */
    private fun loadPdfInfo(): PdfInfo {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("Файл не существует: $filePath")
            }
            if (!file.canRead()) {
                throw SecurityException("Нет прав на чтение файла: $filePath")
            }

            val documentInfo = document.documentInformation

            val pagesInfo = document.pages.mapIndexed { index, page ->
                val mediaBox = page.mediaBox
                PdfPageInfo(
                    pageNumber = index,
                    width = mediaBox.width,
                    height = mediaBox.height,
                    rotation = page.rotation
                )
            }

            // Проверяем одинаковы ли размеры всех страниц
            val hasUniformSizes = pagesInfo.map { it.width to it.height }
                .toSet()
                .size == 1

            return PdfInfo(
                filePath = filePath,
                pageCount = document.numberOfPages,
                pages = pagesInfo,
                title = documentInfo.title,
                author = documentInfo.author,
                creator = documentInfo.creator,
                creationDate = documentInfo.creationDate?.toString(),
                modificationDate = documentInfo.modificationDate?.toString(),
                isEncrypted = document.isEncrypted,
                hasUniformPageSizes = hasUniformSizes
            )

        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException, is SecurityException -> throw e
                else -> throw IllegalArgumentException("Ошибка чтения PDF файла: ${e.message}", e)
            }
        }
    }

    @Synchronized
    override fun renderPage(pageIndex: Int, viewSize: IntSize): ImageBitmap? {
        return try {
            if (pageIndex < 0 || pageIndex >= document.numberOfPages) {
                return null
            }
            logger.debug { "Render page: $pageIndex with width ${viewSize.width} and height ${viewSize.height}" }
            val page = document.getPage(pageIndex)
            val pageWidth = page.mediaBox.width
            val pageHeight = page.mediaBox.height
            val scale = calculateOptimalScale(pageWidth, pageHeight, viewSize)
            val bufferedImage = renderer.renderImageWithDPI(pageIndex, scale * 72f)
            val scaledImage = scaleImageToSize(bufferedImage, viewSize)
            scaledImage.toComposeImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateOptimalScale(
        pageWidth: Float,
        pageHeight: Float,
        viewSize: IntSize
    ): Float {
        val widthScale = viewSize.width / pageWidth
        val heightScale = viewSize.height / pageHeight
        return minOf(widthScale, heightScale).coerceAtLeast(0.1f)
    }

    private fun scaleImageToSize(
        originalImage: BufferedImage,
        targetSize: IntSize
    ): BufferedImage {
        if (originalImage.width == targetSize.width && originalImage.height == targetSize.height) {
            return originalImage
        }

        val scaledImage = BufferedImage(
            targetSize.width,
            targetSize.height,
            BufferedImage.TYPE_INT_ARGB
        )

        val graphics = scaledImage.createGraphics()
        graphics.drawImage(
            originalImage,
            0, 0, targetSize.width.toInt(), targetSize.height.toInt(),
            null
        )
        graphics.dispose()

        return scaledImage
    }

    override fun close() {
        document.close()
    }
}
