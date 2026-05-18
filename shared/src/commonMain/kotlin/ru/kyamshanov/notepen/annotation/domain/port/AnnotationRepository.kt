package ru.kyamshanov.notepen.annotation.domain.port

import ru.kyamshanov.notepen.annotation.domain.model.AnnotationBundle
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings

/** Persistence port for per-document handwritten annotations. */
interface AnnotationRepository {
    suspend fun save(
        pdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        scale: Int,
        pen: PenSettings = PenSettings(),
        marker: MarkerSettings = MarkerSettings(),
        eraser: EraserSettings = EraserSettings(),
        currentPage: Int = 0,
        currentPageOffset: Int = 0,
        favoritePageIndices: Set<Int> = emptySet(),
    ): Result<Unit>

    suspend fun load(pdfPath: String): Result<AnnotationBundle>
}
