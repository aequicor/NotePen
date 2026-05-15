package ru.kyamshanov.notepen.annotation.domain.model

/** In-memory result of loading annotations for a document. */
data class AnnotationBundle(
    val pages: Map<Int, List<DrawingPath>> = emptyMap(),
    val scale: Int = 100,
    val pen: PenSettings = PenSettings(),
    val marker: MarkerSettings = MarkerSettings(),
    val eraser: EraserSettings = EraserSettings(),
    val currentPage: Int = 0,
    val currentPageOffset: Int = 0,
)
