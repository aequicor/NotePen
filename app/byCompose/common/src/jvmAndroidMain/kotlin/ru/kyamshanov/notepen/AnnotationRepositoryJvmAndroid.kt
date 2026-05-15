package ru.kyamshanov.notepen

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationBundle
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import java.io.File
import java.io.IOException

// ── JSON DTO ─────────────────────────────────────────────────────────────────

@Serializable
private data class DrawingPointDto(val x: Float, val y: Float, val isNewPath: Boolean = false)

@Serializable
private data class DrawingPathDto(
    val points: List<DrawingPointDto> = emptyList(),
    val colorArgb: Long = DrawingPath.BLACK_ARGB,
    val strokeWidth: Float = DrawingPath.DEFAULT_STROKE_WIDTH,
)

@Serializable
private data class PenSettingsDto(
    val colorArgb: Long = DrawingPath.BLACK_ARGB,
    val strokeWidth: Float = PenSettings.DEFAULT_STROKE_WIDTH,
    val alpha: Float = 1f,
)

@Serializable
private enum class EraserShapeDto { CIRCLE, SQUARE }

@Serializable
private data class EraserSettingsDto(
    val shape: EraserShapeDto = EraserShapeDto.CIRCLE,
    val sizeNormalized: Float = EraserSettings.DEFAULT_SIZE_NORMALIZED,
)

@Serializable
private data class MarkerSettingsDto(
    val colorArgb: Long = MarkerSettings.PRESET_COLORS[0],
    val strokeWidth: Float = MarkerSettings.DEFAULT_STROKE_WIDTH,
)

@Serializable
private data class AnnotationDataDto(
    val pages: Map<String, List<DrawingPathDto>>,
    val scale: Int = 100,
    val pen: PenSettingsDto? = null,
    val marker: MarkerSettingsDto? = null,
    val eraser: EraserSettingsDto? = null,
    val currentPage: Int = 0,
    val currentPageOffset: Int = 0,
)

// ── Mappers ──────────────────────────────────────────────────────────────────

private fun DrawingPointDto.toDomain() = DrawingPoint(x, y, isNewPath)
private fun DrawingPathDto.toDomain() = DrawingPath(points.map { it.toDomain() }, colorArgb, strokeWidth)
private fun PenSettingsDto.toDomain() = PenSettings(colorArgb, strokeWidth, alpha)
private fun EraserShapeDto.toDomain() = if (this == EraserShapeDto.CIRCLE) EraserShape.CIRCLE else EraserShape.SQUARE
private fun EraserSettingsDto.toDomain() = EraserSettings(shape.toDomain(), sizeNormalized)

private fun DrawingPoint.toDto() = DrawingPointDto(x, y, isNewPath)
private fun DrawingPath.toDto() = DrawingPathDto(points.map { it.toDto() }, colorArgb, strokeWidth)
private fun PenSettings.toDto() = PenSettingsDto(colorArgb, strokeWidth, alpha)
private fun EraserShape.toDto() = if (this == EraserShape.CIRCLE) EraserShapeDto.CIRCLE else EraserShapeDto.SQUARE
private fun EraserSettings.toDto() = EraserSettingsDto(shape.toDto(), sizeNormalized)
private fun MarkerSettings.toDto() = MarkerSettingsDto(colorArgb, strokeWidth)
private fun MarkerSettingsDto.toDomain() = MarkerSettings(colorArgb, strokeWidth)

// ── Repository ───────────────────────────────────────────────────────────────

class AnnotationRepositoryJvmAndroid : AnnotationRepository {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun save(
        pdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        scale: Int,
        pen: PenSettings,
        marker: MarkerSettings,
        eraser: EraserSettings,
        currentPage: Int,
        currentPageOffset: Int,
    ): Result<Unit> = try {
        val dto = AnnotationDataDto(
            pages = annotations.mapKeys { it.key.toString() }
                .mapValues { (_, paths) -> paths.map { it.toDto() } },
            scale = scale,
            pen = pen.toDto(),
            marker = marker.toDto(),
            eraser = eraser.toDto(),
            currentPage = currentPage,
            currentPageOffset = currentPageOffset,
        )
        File("$pdfPath.notepen.json").writeText(json.encodeToString(AnnotationDataDto.serializer(), dto))
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(e)
    }

    override suspend fun load(pdfPath: String): Result<AnnotationBundle> {
        return try {
            val file = File("$pdfPath.notepen.json")
            if (!file.exists()) return Result.success(AnnotationBundle())
            val dto = json.decodeFromString(AnnotationDataDto.serializer(), file.readText())
            val pages = dto.pages.mapKeys {
                it.key.toIntOrNull() ?: return Result.success(AnnotationBundle())
            }.mapValues { (_, paths) -> paths.map { it.toDomain() } }
            Result.success(
                AnnotationBundle(
                    pages = pages,
                    scale = dto.scale,
                    pen = dto.pen?.toDomain() ?: PenSettings(),
                    marker = dto.marker?.toDomain() ?: MarkerSettings(),
                    eraser = dto.eraser?.toDomain() ?: EraserSettings(),
                    currentPage = dto.currentPage,
                    currentPageOffset = dto.currentPageOffset,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
