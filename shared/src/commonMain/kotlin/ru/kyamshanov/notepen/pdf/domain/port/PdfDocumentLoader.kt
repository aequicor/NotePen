package ru.kyamshanov.notepen.pdf.domain.port

import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument

/**
 * Порт для открытия PDF-документа по платформенному пути к файлу.
 *
 * Реализации обязаны быть main-safe: всё блокирующее IO должно выполняться
 * через инжектируемый [kotlinx.coroutines.CoroutineDispatcher], а не через
 * `Dispatchers.IO` напрямую.
 */
interface PdfDocumentLoader {

    /**
     * Открывает PDF-файл по заданному пути.
     *
     * @param path абсолютный платформенный путь к файлу
     * @return открытый [PdfDocument]; вызывающая сторона обязана вызвать [PdfDocument.close]
     * @throws IllegalArgumentException если файл не найден или не является валидным PDF
     */
    suspend fun load(path: String): PdfDocument
}
