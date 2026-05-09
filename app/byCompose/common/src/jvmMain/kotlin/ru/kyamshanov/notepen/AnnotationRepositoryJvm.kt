package ru.kyamshanov.notepen

import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

actual fun createAnnotationRepository(): AnnotationRepository = AnnotationRepositoryJvm()

class AnnotationRepositoryJvm : AnnotationRepository {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun save(
        pdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        scale: Int,
        pen: PenSettings,
        eraser: EraserSettings,
        currentPage: Int,
    ): Result<Unit> {
        return try {
            val data = AnnotationData(
                pages = annotations.mapKeys { it.key.toString() },
                scale = scale,
                tools = ToolsBundle(pen = pen, eraser = eraser),
                currentPage = currentPage,
            )
            File("$pdfPath.notepen.json").writeText(json.encodeToString(AnnotationData.serializer(), data))
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun load(pdfPath: String): Result<AnnotationBundle> {
        return try {
            val file = File("$pdfPath.notepen.json")
            if (!file.exists()) return Result.success(AnnotationBundle())
            val data = json.decodeFromString(AnnotationData.serializer(), file.readText())
            val pages = data.pages.mapKeys { it.key.toIntOrNull() ?: return Result.success(AnnotationBundle()) }
            val tools = data.tools ?: ToolsBundle()
            Result.success(
                AnnotationBundle(
                    pages = pages,
                    scale = data.scale,
                    pen = tools.pen,
                    eraser = tools.eraser,
                    currentPage = data.currentPage,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
