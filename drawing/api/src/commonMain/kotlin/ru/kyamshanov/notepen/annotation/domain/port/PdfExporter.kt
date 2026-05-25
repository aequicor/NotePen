package ru.kyamshanov.notepen.annotation.domain.port

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath

/**
 * Port for flattening handwritten annotations into a new PDF file.
 *
 * Implementations must be main-safe: all blocking work is dispatched on an
 * injected [kotlinx.coroutines.CoroutineDispatcher].
 */
interface PdfExporter {
    /**
     * Renders [annotations] on top of the source PDF and writes the result to [outputPath].
     *
     * @param sourcePdfPath absolute path to the original PDF
     * @param annotations page-index → stroke list; only annotated pages are modified
     * @param outputPath destination file path; created or overwritten
     * @return [Result.success] on success, [Result.failure] with the cause otherwise
     */
    suspend fun export(
        sourcePdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        outputPath: String,
    ): Result<Unit>
}
