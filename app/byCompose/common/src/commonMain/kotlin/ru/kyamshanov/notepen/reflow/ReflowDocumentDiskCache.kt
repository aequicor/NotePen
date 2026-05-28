package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowDocument

/**
 * Дисковый кэш [ReflowDocument] для пути исходного PDF.
 *
 * Ключ — путь/URI, переданный в [PdfReflowExtractor.extract]. Валидность
 * привязана к размеру+mtime источника (см. [SourceStat]): при их изменении
 * запись считается устаревшей и не возвращается.
 *
 * Все ошибки I/O проглатываются — кэш необязателен; на промахе/ошибке
 * вызывающая сторона возвращается к полной экстракции.
 */
internal interface ReflowDocumentDiskCache {
    suspend fun read(path: String): ReflowDocument?

    suspend fun write(
        path: String,
        document: ReflowDocument,
    )
}
