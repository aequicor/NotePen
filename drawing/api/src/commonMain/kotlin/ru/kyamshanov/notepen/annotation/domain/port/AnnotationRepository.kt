package ru.kyamshanov.notepen.annotation.domain.port

import ru.kyamshanov.notepen.annotation.domain.model.AnnotationBundle
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationViewState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight

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
        highlights: Map<Int, List<StickyHighlight>> = emptyMap(),
        notes: Map<Int, List<PageNote>> = emptyMap(),
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

    /**
     * Сохраняет только лёгкое [viewState] (масштаб/страница/режим чтения),
     * не трогая тяжёлый набор штрихов. Предназначен для частого автосейва
     * навигации (зум/скролл) — запись большого файла аннотаций на каждый
     * скролл была бы лишним I/O.
     */
    suspend fun saveViewState(
        pdfPath: String,
        viewState: AnnotationViewState,
    ): Result<Unit>
}
