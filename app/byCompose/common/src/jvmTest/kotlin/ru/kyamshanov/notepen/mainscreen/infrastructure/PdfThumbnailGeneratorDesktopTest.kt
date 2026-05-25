package ru.kyamshanov.notepen.mainscreen.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.book.JvmEbookToPdfConverter
import ru.kyamshanov.notepen.mainscreen.domain.exception.ThumbnailGenerationException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Тесты [PdfThumbnailGeneratorDesktop].
 * TC-44 (CC-4): пустой PDF (0 страниц) → Result.failure(ThumbnailGenerationException)
 */
class PdfThumbnailGeneratorDesktopTest {
    private val generator = PdfThumbnailGeneratorDesktop(converter = JvmEbookToPdfConverter(Dispatchers.IO))

    // TC-44: несуществующий файл → Result.failure с ThumbnailGenerationException
    @Test
    fun `non-existent file returns failure with ThumbnailGenerationException`() =
        runTest {
            val result = generator.generate("/tmp/nonexistent_pdf_12345.pdf", 100, 100)
            assertTrue(result.isFailure)
            assertIs<ThumbnailGenerationException>(result.exceptionOrNull())
        }

    // TC-44: пустой PDF → failure
    @Test
    fun `empty path returns failure`() =
        runTest {
            val result = generator.generate("", 100, 100)
            assertTrue(result.isFailure)
            assertIs<ThumbnailGenerationException>(result.exceptionOrNull())
        }

    // H-1: widthPx = 0 — некорректные размеры → failure с ThumbnailGenerationException
    @Test
    fun generate_invalidDimensions_returnsFailure() =
        runTest {
            val result = generator.generate("any.pdf", widthPx = 0, heightPx = 100)
            assertTrue(result.isFailure)
            assertIs<ThumbnailGenerationException>(result.exceptionOrNull())
        }

    // H-1: widthPx = 5000 — превышение лимита 4096 → failure с ThumbnailGenerationException
    @Test
    fun generate_dimensionsTooLarge_returnsFailure() =
        runTest {
            val result = generator.generate("any.pdf", widthPx = 5000, heightPx = 100)
            assertTrue(result.isFailure)
            assertIs<ThumbnailGenerationException>(result.exceptionOrNull())
        }
}
