package ru.kyamshanov.notepen

import kotlinx.coroutines.Dispatchers
import ru.kyamshanov.notepen.book.AndroidEbookToPdfConverter
import ru.kyamshanov.notepen.book.EbookAwarePdfReflowExtractor
import ru.kyamshanov.notepen.reflow.AndroidPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.DiskCachingPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.FileSystemReflowDocumentDiskCache
import ru.kyamshanov.notepen.reflow.InFlightDedupPdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor

actual fun createPdfReflowExtractor(): PdfReflowExtractor {
    val converter = AndroidEbookToPdfConverter(AppContextHolder.context, Dispatchers.IO)
    return InFlightDedupPdfReflowExtractor(
        DiskCachingPdfReflowExtractor(
            EbookAwarePdfReflowExtractor(
                AndroidPdfReflowExtractor(AppContextHolder.context, Dispatchers.IO),
                converter,
                converter,
            ),
            FileSystemReflowDocumentDiskCache(),
        ),
    )
}
