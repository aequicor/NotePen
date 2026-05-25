package ru.kyamshanov.notepen.mainscreen.domain.port

import ru.kyamshanov.notepen.mainscreen.domain.exception.ThumbnailGenerationException

/**
 * Порт для генерации миниатюры первой страницы PDF.
 * Декларируется в `:shared`. Реализуется в инфраструктурном слое.
 */
interface PdfThumbnailGenerator {
    /**
     * Генерирует миниатюру первой страницы PDF по указанному URI.
     *
     * OOM и исключения рендеринга перехватываются внутри и возвращаются
     * как [Result.failure] с [ThumbnailGenerationException] (CC-11).
     *
     * @param uri Нормализованный URI PDF-файла.
     * @param widthPx Ширина целевого изображения в пикселях.
     * @param heightPx Высота целевого изображения в пикселях.
     * @return [Result.success] с байтами изображения или [Result.failure] с [ThumbnailGenerationException].
     */
    suspend fun generate(
        uri: String,
        widthPx: Int,
        heightPx: Int,
    ): Result<ByteArray>
}
