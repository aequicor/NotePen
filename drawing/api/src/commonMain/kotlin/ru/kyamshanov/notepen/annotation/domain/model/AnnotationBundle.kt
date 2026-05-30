package ru.kyamshanov.notepen.annotation.domain.model

/** In-memory result of loading annotations for a document. */
data class AnnotationBundle(
    val pages: Map<Int, List<DrawingPath>> = emptyMap(),
    /** Sticky-marker highlights per zero-based page index (see [StickyHighlight]). */
    val highlights: Map<Int, List<StickyHighlight>> = emptyMap(),
    /** Text notes per zero-based page index, keyed exactly like [highlights] (see [PageNote]). */
    val notes: Map<Int, List<PageNote>> = emptyMap(),
    val scale: Int = 100,
    val pen: PenSettings = PenSettings(),
    val marker: MarkerSettings = MarkerSettings(),
    val eraser: EraserSettings = EraserSettings(),
    val currentPage: Int = 0,
    val currentPageOffset: Int = 0,
    val favoritePageIndices: Set<Int> = emptySet(),
    /**
     * Расширенная рисуемая область каждой страницы (см. [PageExtent]).
     * Отсутствие ключа эквивалентно [PageExtent.Pdf].
     */
    val pageExtents: Map<Int, PageExtent> = emptyMap(),
)
