package ru.kyamshanov.notepen

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationBundle
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import java.io.File
import java.io.IOException

// ── JSON DTO ─────────────────────────────────────────────────────────────────

@Serializable
private data class DrawingPointDto(
    val x: Float,
    val y: Float,
    val isNewPath: Boolean = false,
    // Defaults preserve backward compatibility with strokes serialised before
    // pressure/tilt were persisted (they load as mouse-like 1f/0f).
    val pressure: Float = 1f,
    val tilt: Float = 0f,
)

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
private enum class EraserModeDto { POINT, OBJECT }

@Serializable
private data class EraserSettingsDto(
    val shape: EraserShapeDto = EraserShapeDto.CIRCLE,
    val sizeNormalized: Float = EraserSettings.DEFAULT_SIZE_NORMALIZED,
    val mode: EraserModeDto = EraserModeDto.POINT,
)

@Serializable
private data class MarkerSettingsDto(
    val colorArgb: Long = MarkerSettings.PRESET_COLORS[0],
    val strokeWidth: Float = MarkerSettings.DEFAULT_STROKE_WIDTH,
)

@Serializable
private data class PageExtentDto(
    val l: Float = 0f,
    val t: Float = 0f,
    val r: Float = 1f,
    val b: Float = 1f,
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
    val favoritePageIndices: List<Int> = emptyList(),
    val pageExtents: Map<String, PageExtentDto> = emptyMap(),
)

// ── Mappers ──────────────────────────────────────────────────────────────────

private fun DrawingPointDto.toDomain() = DrawingPoint(x, y, isNewPath, pressure, tilt)
private fun DrawingPathDto.toDomain() = DrawingPath(points.map { it.toDomain() }, colorArgb, strokeWidth)
private fun PenSettingsDto.toDomain() = PenSettings(colorArgb, strokeWidth, alpha)
private fun EraserShapeDto.toDomain() = if (this == EraserShapeDto.CIRCLE) EraserShape.CIRCLE else EraserShape.SQUARE
private fun EraserModeDto.toDomain() = if (this == EraserModeDto.OBJECT) EraserMode.OBJECT else EraserMode.POINT
private fun EraserSettingsDto.toDomain() = EraserSettings(shape.toDomain(), sizeNormalized, mode.toDomain())

private fun DrawingPoint.toDto() = DrawingPointDto(x, y, isNewPath, pressure, tilt)
private fun DrawingPath.toDto() = DrawingPathDto(points.map { it.toDto() }, colorArgb, strokeWidth)
private fun PenSettings.toDto() = PenSettingsDto(colorArgb, strokeWidth, alpha)
private fun EraserShape.toDto() = if (this == EraserShape.CIRCLE) EraserShapeDto.CIRCLE else EraserShapeDto.SQUARE
private fun EraserMode.toDto() = if (this == EraserMode.OBJECT) EraserModeDto.OBJECT else EraserModeDto.POINT
private fun EraserSettings.toDto() = EraserSettingsDto(shape.toDto(), sizeNormalized, mode.toDto())
private fun MarkerSettings.toDto() = MarkerSettingsDto(colorArgb, strokeWidth)
private fun MarkerSettingsDto.toDomain() = MarkerSettings(colorArgb, strokeWidth)
private fun PageExtentDto.toDomain() = PageExtent(l, t, r, b)
private fun PageExtent.toDto() = PageExtentDto(left, top, right, bottom)

// ── Repository ───────────────────────────────────────────────────────────────

/**
 * @param storeFileFor resolves the on-disk JSON sidecar for a given document path.
 *   Desktop maps it next to the PDF (`"$path.notepen.json"`); Android can't write
 *   next to a `content://` URI, so it maps to app-private storage keyed by a hash
 *   of the URI.
 */
class AnnotationRepositoryJvmAndroid(
    private val storeFileFor: (pdfPath: String) -> File = { File("$it.notepen.json") },
    // Дисковый IO должен идти не на main/AWT-потоке: запись JSON с сотнями штрихов
    // блокировала UI и давала лаг рисования (перо «замирает» и скачком дорисовывает).
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AnnotationRepository {

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
        favoritePageIndices: Set<Int>,
        pageExtents: Map<Int, PageExtent>,
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val dto = AnnotationDataDto(
                pages = annotations.mapKeys { it.key.toString() }
                    .mapValues { (_, paths) -> paths.map { it.toDto() } },
                scale = scale,
                pen = pen.toDto(),
                marker = marker.toDto(),
                eraser = eraser.toDto(),
                currentPage = currentPage,
                currentPageOffset = currentPageOffset,
                favoritePageIndices = favoritePageIndices.toList(),
                pageExtents = pageExtents
                    .filterValues { it != PageExtent.Pdf }
                    .mapKeys { it.key.toString() }
                    .mapValues { (_, e) -> e.toDto() },
            )
            val file = storeFileFor(pdfPath)
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(AnnotationDataDto.serializer(), dto))
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override suspend fun load(pdfPath: String): Result<AnnotationBundle> = withContext(ioDispatcher) {
        try {
            val file = storeFileFor(pdfPath)
            if (!file.exists()) {
                Result.success(AnnotationBundle())
            } else {
                val dto = json.decodeFromString(AnnotationDataDto.serializer(), file.readText())
                val pages = dto.pages.mapNotNull { (k, paths) ->
                    k.toIntOrNull()?.let { it to paths.map { p -> p.toDomain() } }
                }.toMap()
                val extents = dto.pageExtents.mapNotNull { (k, v) ->
                    k.toIntOrNull()?.let { it to v.toDomain() }
                }.toMap()
                Result.success(
                    AnnotationBundle(
                        pages = pages,
                        scale = dto.scale,
                        pen = dto.pen?.toDomain() ?: PenSettings(),
                        marker = dto.marker?.toDomain() ?: MarkerSettings(),
                        eraser = dto.eraser?.toDomain() ?: EraserSettings(),
                        currentPage = dto.currentPage,
                        currentPageOffset = dto.currentPageOffset,
                        favoritePageIndices = dto.favoritePageIndices.toSet(),
                        pageExtents = extents,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
