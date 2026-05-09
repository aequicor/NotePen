package ru.kyamshanov.notepen

import kotlinx.serialization.Serializable

@Serializable
data class AnnotationData(
    val pages: Map<String, List<DrawingPath>>,
    val scale: Int = 100,
)

data class AnnotationBundle(
    val pages: Map<Int, List<DrawingPath>> = emptyMap(),
    val scale: Int = 100,
)

interface AnnotationRepository {
    suspend fun save(pdfPath: String, annotations: Map<Int, List<DrawingPath>>, scale: Int): Result<Unit>
    suspend fun load(pdfPath: String): Result<AnnotationBundle>
}

expect fun createAnnotationRepository(): AnnotationRepository
