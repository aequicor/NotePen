package ru.kyamshanov.notepen

import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

class AnnotationRepositoryJvmAndroid : AnnotationRepository {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun save(
        pdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        scale: Int,
        pen: PenSettings,
        eraser: EraserSettings,
        currentPage: Int,
        currentPageOffset: Int,
    ): Result<Unit> = try {
        val data = AnnotationData(
            pages = annotations.mapKeys { it.key.toString() },
            scale = scale,
            tools = ToolsBundle(pen = pen, eraser = eraser),
            currentPage = currentPage,
            currentPageOffset = currentPageOffset,
        )
        File("$pdfPath.notepen.json").writeText(json.encodeToString(AnnotationData.serializer(), data))
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(e)
    }

    override suspend fun load(pdfPath: String): Result<AnnotationBundle> {
        return try {
            val file = File("$pdfPath.notepen.json")
            if (!file.exists()) return Result.success(AnnotationBundle())
            val data = json.decodeFromString(AnnotationData.serializer(), file.readText())
            val pages = data.pages.mapKeys {
                it.key.toIntOrNull() ?: return Result.success(AnnotationBundle())
            }
            val tools = data.tools ?: ToolsBundle()
            Result.success(
                AnnotationBundle(
                    pages = pages,
                    scale = data.scale,
                    pen = tools.pen,
                    eraser = tools.eraser,
                    currentPage = data.currentPage,
                    currentPageOffset = data.currentPageOffset,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
