package ru.kyamshanov.notepen

import kotlinx.serialization.Serializable

@Serializable
data class AnnotationData(
    val pages: Map<String, List<DrawingPath>>,
)

interface AnnotationRepository {
    suspend fun save(pdfPath: String, annotations: Map<Int, List<DrawingPath>>): Result<Unit>
}

expect fun createAnnotationRepository(): AnnotationRepository
