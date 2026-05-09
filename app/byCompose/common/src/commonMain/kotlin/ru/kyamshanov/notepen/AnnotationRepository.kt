package ru.kyamshanov.notepen

import kotlinx.serialization.Serializable

@Serializable
data class AnnotationData(
    val pages: Map<String, List<DrawingPath>>,
    val scale: Int = 100,
    /**
     * Сериализованные настройки инструментов. `null` для совместимости с файлами
     * аннотаций старого формата (AC-19): при загрузке такого файла
     * `AnnotationBundle` получает дефолтные `PenSettings()` / `EraserSettings()`.
     */
    val tools: ToolsBundle? = null,
)

@Serializable
data class ToolsBundle(
    val pen: PenSettings = PenSettings(),
    val eraser: EraserSettings = EraserSettings(),
)

data class AnnotationBundle(
    val pages: Map<Int, List<DrawingPath>> = emptyMap(),
    val scale: Int = 100,
    val pen: PenSettings = PenSettings(),
    val eraser: EraserSettings = EraserSettings(),
)

interface AnnotationRepository {
    suspend fun save(
        pdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        scale: Int,
        pen: PenSettings = PenSettings(),
        eraser: EraserSettings = EraserSettings(),
    ): Result<Unit>

    suspend fun load(pdfPath: String): Result<AnnotationBundle>
}

expect fun createAnnotationRepository(): AnnotationRepository
