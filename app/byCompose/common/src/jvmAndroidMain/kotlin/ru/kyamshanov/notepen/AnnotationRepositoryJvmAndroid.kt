package ru.kyamshanov.notepen

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationBundle
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationViewState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserMode
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.NormalizedRect
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import java.io.File
import java.io.IOException
import java.io.OutputStream

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
    val sticky: Boolean = true,
)

@Serializable
private data class NormalizedRectDto(
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
)

@Serializable
private data class StickyHighlightDto(
    val rects: List<NormalizedRectDto> = emptyList(),
    val colorArgb: Long = MarkerSettings.PRESET_COLORS[0],
    val strokeId: String = "",
)

// Дисковый DTO заметки. Самостоятельный тип (не путать с проводным
// ru.kyamshanov.notepen.sync.domain.model.PageNoteDto) — переиспользует
// существующий NormalizedRectDto этого файла. Геометрия (rects) — источник
// истины; quote/context — запасной TextQuoteSelector для ре-анкоринга.
@Serializable
private data class PageNoteDto(
    val noteId: String = "",
    val rects: List<NormalizedRectDto> = emptyList(),
    val pageIndex: Int = 0,
    val quote: String = "",
    val context: String = "",
    val body: String = "",
    val colorArgb: Long = MarkerSettings.PRESET_COLORS[0],
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
private data class PageExtentDto(
    val l: Float = 0f,
    val t: Float = 0f,
    val r: Float = 1f,
    val b: Float = 1f,
)

@Serializable
private data class AnnotationViewStateDto(
    val scale: Int = 100,
    val currentPage: Int = 0,
    val currentPageOffset: Int = 0,
    val readingMode: Boolean = false,
    // Дефолты сохраняют BC с легаси-сайдкарами без reflow-якоря: read даст 0/0,
    // что эквивалентно «открыть с начала» (так же, как для свежего документа).
    val reflowAnchorBlockIndex: Int = 0,
    val reflowAnchorCharStart: Int = 0,
    // Пользовательский поворот страниц (четверти CW) по индексу. Отсутствие ключа
    // у легаси-сайдкаров даёт пустую карту — повороты добавлены позже.
    val pageRotations: Map<String, Int> = emptyMap(),
    // Разделение разворотов (FEATURE #4). Отсутствие поля у легаси-сайдкаров даёт
    // false — обычное отображение 1:1.
    val spreadSplit: Boolean = false,
    // Явный выбор пользователя по книжному развороту (FEATURE #5): null — авто
    // (по ширине экрана), true/false — принудительно. Отсутствие у легаси-сайдкаров
    // даёт null = авто, что эквивалентно поведению до появления фичи.
    val spreadViewOverride: Boolean? = null,
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
    // Отсутствие ключа у легаси-файлов даёт пустую карту — выделения добавлены позже штрихов.
    val highlights: Map<String, List<StickyHighlightDto>> = emptyMap(),
    // Отсутствие ключа у легаси-файлов даёт пустую карту — заметки добавлены позже выделений.
    val notes: Map<String, List<PageNoteDto>> = emptyMap(),
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

private fun MarkerSettings.toDto() = MarkerSettingsDto(colorArgb, strokeWidth, sticky)

private fun MarkerSettingsDto.toDomain() = MarkerSettings(colorArgb, strokeWidth, sticky)

private fun PageExtentDto.toDomain() = PageExtent(l, t, r, b)

private fun PageExtent.toDto() = PageExtentDto(left, top, right, bottom)

private fun NormalizedRectDto.toDomain() = NormalizedRect(l, t, r, b)

private fun NormalizedRect.toDto() = NormalizedRectDto(left, top, right, bottom)

private fun StickyHighlightDto.toDomain() = StickyHighlight(rects.map { it.toDomain() }, colorArgb, strokeId)

private fun StickyHighlight.toDto() = StickyHighlightDto(rects.map { it.toDto() }, colorArgb, strokeId)

// Позиционные мапперы: порядок полей обязан совпадать с PageNote (см. план §0.1).
private fun PageNoteDto.toDomain() =
    PageNote(noteId, rects.map { it.toDomain() }, pageIndex, quote, context, body, colorArgb, createdAt, updatedAt)

private fun PageNote.toDto() =
    PageNoteDto(noteId, rects.map { it.toDto() }, pageIndex, quote, context, body, colorArgb, createdAt, updatedAt)

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
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    // Запись без encodeDefaults: не пишем isNewPath=false/pressure=1.0/tilt=0.0 у каждой
    // точки — заметно ужимает файл. Чтение терпимо к отсутствующим полям (дефолты в DTO).
    private val writeJson =
        Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

    @OptIn(ExperimentalSerializationApi::class)
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
        highlights: Map<Int, List<StickyHighlight>>,
        notes: Map<Int, List<PageNote>>,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val dto =
                    AnnotationDataDto(
                        pages =
                            annotations.mapKeys { it.key.toString() }
                                .mapValues { (_, paths) -> paths.map { it.toDto() } },
                        scale = scale,
                        pen = pen.toDto(),
                        marker = marker.toDto(),
                        eraser = eraser.toDto(),
                        currentPage = currentPage,
                        currentPageOffset = currentPageOffset,
                        favoritePageIndices = favoritePageIndices.toList(),
                        pageExtents =
                            pageExtents
                                .filterValues { it != PageExtent.Pdf }
                                .mapKeys { it.key.toString() }
                                .mapValues { (_, e) -> e.toDto() },
                        highlights =
                            highlights
                                .filterValues { it.isNotEmpty() }
                                .mapKeys { it.key.toString() }
                                .mapValues { (_, hs) -> hs.map { it.toDto() } },
                        notes =
                            notes
                                .filterValues { it.isNotEmpty() }
                                .mapKeys { it.key.toString() }
                                .mapValues { (_, ns) -> ns.map { it.toDto() } },
                    )
                val file = storeFileFor(pdfPath)
                file.parentFile?.mkdirs()
                // Поток + временный файл: не строим гигантскую String в памяти (был OOM при
                // сотнях тысяч точек), и прерывание записи не оставляет битый JSON.
                writeAtomically(file) { out ->
                    writeJson.encodeToStream(AnnotationDataDto.serializer(), dto, out)
                }
                // Лёгкий сайдкар с состоянием вида — читается при открытии отдельно и быстро,
                // чтобы зум/страница восстанавливались до парсинга всех штрихов. readingMode
                // тут не передаётся (это сейв штрихов) — сохраняем уже записанный, чтобы не
                // затереть режим чтения; его пишет отдельный saveViewState.
                val viewFile = viewFileFor(file)
                val preserved = readPreservedReadingState(viewFile)
                writeAtomically(viewFile) { out ->
                    val viewDto =
                        AnnotationViewStateDto(
                            scale = scale,
                            currentPage = currentPage,
                            currentPageOffset = currentPageOffset,
                            readingMode = preserved.readingMode,
                            reflowAnchorBlockIndex = preserved.reflowAnchorBlockIndex,
                            reflowAnchorCharStart = preserved.reflowAnchorCharStart,
                            // Поворот принадлежит сайдкару вида (его пишет saveViewState);
                            // при перезаписи штрихов сохраняем уже записанные повороты.
                            pageRotations = preserved.pageRotations,
                            // Разделение разворотов тоже принадлежит сайдкару вида —
                            // сохраняем уже записанное при перезаписи штрихов.
                            spreadSplit = preserved.spreadSplit,
                            // Книжный разворот (FEATURE #5) — тоже поле вида; сохраняем
                            // явный выбор пользователя при перезаписи штрихов.
                            spreadViewOverride = preserved.spreadViewOverride,
                        )
                    writeJson.encodeToStream(AnnotationViewStateDto.serializer(), viewDto, out)
                }
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun load(pdfPath: String): Result<AnnotationBundle> =
        withContext(ioDispatcher) {
            try {
                val file = storeFileFor(pdfPath)
                if (!file.exists()) {
                    Result.success(AnnotationBundle())
                } else {
                    // Потоковый декод: не держим весь файл в String (был риск OOM и
                    // лишняя задержка парсинга на больших документах).
                    val dto =
                        file.inputStream().buffered().use { input ->
                            json.decodeFromStream(AnnotationDataDto.serializer(), input)
                        }
                    val pages =
                        dto.pages.mapNotNull { (k, paths) ->
                            k.toIntOrNull()?.let { it to paths.map { p -> p.toDomain() } }
                        }.toMap()
                    val extents =
                        dto.pageExtents.mapNotNull { (k, v) ->
                            k.toIntOrNull()?.let { it to v.toDomain() }
                        }.toMap()
                    val highlights =
                        dto.highlights.mapNotNull { (k, hs) ->
                            k.toIntOrNull()?.let { it to hs.map { h -> h.toDomain() } }
                        }.toMap()
                    val notes =
                        dto.notes.mapNotNull { (k, ns) ->
                            k.toIntOrNull()?.let { it to ns.map { n -> n.toDomain() } }
                        }.toMap()
                    Result.success(
                        AnnotationBundle(
                            pages = pages,
                            highlights = highlights,
                            notes = notes,
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

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun loadViewState(pdfPath: String): Result<AnnotationViewState?> =
        withContext(ioDispatcher) {
            try {
                val viewFile = viewFileFor(storeFileFor(pdfPath))
                if (!viewFile.exists()) {
                    Result.success(null)
                } else {
                    val dto =
                        viewFile.inputStream().buffered().use { input ->
                            json.decodeFromStream(AnnotationViewStateDto.serializer(), input)
                        }
                    Result.success(
                        AnnotationViewState(
                            scale = dto.scale,
                            currentPage = dto.currentPage,
                            currentPageOffset = dto.currentPageOffset,
                            readingMode = dto.readingMode,
                            reflowAnchorBlockIndex = dto.reflowAnchorBlockIndex,
                            reflowAnchorCharStart = dto.reflowAnchorCharStart,
                            pageRotations =
                                dto.pageRotations.mapNotNull { (k, v) ->
                                    k.toIntOrNull()?.let { it to v }
                                }.toMap(),
                            spreadSplit = dto.spreadSplit,
                            spreadViewOverride = dto.spreadViewOverride,
                        ),
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun saveViewState(
        pdfPath: String,
        viewState: AnnotationViewState,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                val viewFile = viewFileFor(storeFileFor(pdfPath))
                writeAtomically(viewFile) { out ->
                    val viewDto =
                        AnnotationViewStateDto(
                            scale = viewState.scale,
                            currentPage = viewState.currentPage,
                            currentPageOffset = viewState.currentPageOffset,
                            readingMode = viewState.readingMode,
                            reflowAnchorBlockIndex = viewState.reflowAnchorBlockIndex,
                            reflowAnchorCharStart = viewState.reflowAnchorCharStart,
                            pageRotations =
                                viewState.pageRotations
                                    .filterValues { it != 0 }
                                    .mapKeys { it.key.toString() },
                            spreadSplit = viewState.spreadSplit,
                            spreadViewOverride = viewState.spreadViewOverride,
                        )
                    writeJson.encodeToStream(AnnotationViewStateDto.serializer(), viewDto, out)
                }
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }

    /** Имя лёгкого сайдкара состояния вида рядом с основным файлом аннотаций. */
    private fun viewFileFor(annotationFile: File): File = File(annotationFile.parentFile, "${annotationFile.name}.view")

    /**
     * Состояние чтения, которое нужно сохранить при перезаписи [save] (там пишутся
     * масштаб/страница/смещение, но не reflow-поля и режим — они принадлежат
     * отдельному [saveViewState]). Возвращает дефолты, если файла/полей нет.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun readPreservedReadingState(viewFile: File): PreservedReadingState =
        if (viewFile.exists()) {
            runCatching {
                viewFile.inputStream().buffered().use { input ->
                    val dto = json.decodeFromStream(AnnotationViewStateDto.serializer(), input)
                    PreservedReadingState(
                        readingMode = dto.readingMode,
                        reflowAnchorBlockIndex = dto.reflowAnchorBlockIndex,
                        reflowAnchorCharStart = dto.reflowAnchorCharStart,
                        pageRotations = dto.pageRotations,
                        spreadSplit = dto.spreadSplit,
                        spreadViewOverride = dto.spreadViewOverride,
                    )
                }
            }.getOrDefault(PreservedReadingState.Empty)
        } else {
            PreservedReadingState.Empty
        }

    private data class PreservedReadingState(
        val readingMode: Boolean,
        val reflowAnchorBlockIndex: Int,
        val reflowAnchorCharStart: Int,
        val pageRotations: Map<String, Int>,
        val spreadSplit: Boolean,
        val spreadViewOverride: Boolean?,
    ) {
        companion object {
            val Empty = PreservedReadingState(false, 0, 0, emptyMap(), false, null)
        }
    }

    /** Пишет [file] через временный файл + rename, чтобы прерывание не оставило битый JSON. */
    private fun writeAtomically(
        file: File,
        write: (OutputStream) -> Unit,
    ) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().buffered().use(write)
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }
}
