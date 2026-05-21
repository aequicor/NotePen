package ru.kyamshanov.notepen.annotation.domain.port

import ru.kyamshanov.notepen.annotation.domain.model.AnnotationBundle
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationViewState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
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
        pageExtents: Map<Int, PageExtent> = emptyMap(),
    ): Result<Unit>

    suspend fun load(pdfPath: String): Result<AnnotationBundle>

    /**
     * Быстро читает только лёгкое [AnnotationViewState] (масштаб/страница),
     * не парся весь набор штрихов. Используется при открытии документа, чтобы
     * восстановить зум до загрузки аннотаций и избежать позднего «доворота».
     *
     * @return состояние вида или `null`, если оно ещё не сохранялось.
     */
    suspend fun loadViewState(pdfPath: String): Result<AnnotationViewState?>
}
