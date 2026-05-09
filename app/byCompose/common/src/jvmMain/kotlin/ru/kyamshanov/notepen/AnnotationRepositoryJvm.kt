package ru.kyamshanov.notepen

import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

actual fun createAnnotationRepository(): AnnotationRepository = AnnotationRepositoryJvm()

class AnnotationRepositoryJvm : AnnotationRepository {

    private val json = Json { encodeDefaults = true }

    override suspend fun save(pdfPath: String, annotations: Map<Int, List<DrawingPath>>): Result<Unit> {
        return try {
            val data = AnnotationData(pages = annotations.mapKeys { it.key.toString() })
            File("$pdfPath.notepen.json").writeText(json.encodeToString(AnnotationData.serializer(), data))
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }
}
