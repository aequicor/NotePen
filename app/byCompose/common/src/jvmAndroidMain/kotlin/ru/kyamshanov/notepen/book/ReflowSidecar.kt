package ru.kyamshanov.notepen.book

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.ReflowBlock
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.ReflowRect
import ru.kyamshanov.notepen.reflow.api.SourceSpan
import java.io.File

/**
 * Хранит reflow-документ книги в JSON-файле рядом с кешированным PDF
 * (`<pdf>.reflow.json`). Документ собирается рендерером при верстке книги в PDF
 * (см. [BookRenderResult]) и читается при включении режима чтения — это избавляет
 * от обратного выскребания текста из PDF и эвристик восстановления структуры.
 *
 * Сериализация изолирована в DTO-зеркале (см. ниже), чтобы не тащить
 * kotlinx.serialization в чистый модуль `reflow/api`. Ошибки чтения/записи
 * проглатываются — сайдкар необязателен: при его отсутствии вызывающая сторона
 * откатывается на извлечение из PDF.
 */
internal object ReflowSidecar {
    private val json = Json { ignoreUnknownKeys = true }

    fun pathFor(pdfPath: String): File = File("$pdfPath.reflow.json")

    fun write(
        pdfPath: String,
        document: ReflowDocument,
    ) {
        runCatching { pathFor(pdfPath).writeText(json.encodeToString(ReflowDocDto.serializer(), document.toDto())) }
    }

    fun read(pdfPath: String): ReflowDocument? =
        runCatching {
            val file = pathFor(pdfPath)
            if (file.exists()) json.decodeFromString(ReflowDocDto.serializer(), file.readText()).toModel() else null
        }.getOrNull()
}

@Serializable
private data class ReflowDocDto(
    val kind: String,
    val blocks: List<ReflowBlockDto>,
)

@Serializable
private sealed interface ReflowBlockDto {
    @Serializable
    @SerialName("heading")
    data class Heading(
        val text: String,
        val level: Int,
        val source: List<SourceSpanDto> = emptyList(),
    ) : ReflowBlockDto

    @Serializable
    @SerialName("paragraph")
    data class Paragraph(
        val text: String,
        val source: List<SourceSpanDto> = emptyList(),
    ) : ReflowBlockDto

    @Serializable
    @SerialName("blockquote")
    data class Blockquote(
        val text: String,
        val source: List<SourceSpanDto> = emptyList(),
    ) : ReflowBlockDto

    @Serializable
    @SerialName("listItem")
    data class ListItem(
        val text: String,
        val source: List<SourceSpanDto> = emptyList(),
    ) : ReflowBlockDto

    @Serializable
    @SerialName("table")
    data class Table(
        val rows: List<List<CellDto>>,
    ) : ReflowBlockDto

    @Serializable
    @SerialName("figure")
    data class Figure(
        val pageIndex: Int,
        val bounds: RectDto,
    ) : ReflowBlockDto

    @Serializable
    @SerialName("divider")
    data object Divider : ReflowBlockDto
}

@Serializable
private data class CellDto(
    val text: String,
    val source: List<SourceSpanDto> = emptyList(),
)

@Serializable
private data class SourceSpanDto(
    val pageIndex: Int,
    val charStart: Int,
    val charEnd: Int,
    val bounds: RectDto,
    val bold: Boolean = false,
    val monospace: Boolean = false,
)

@Serializable
private data class RectDto(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

private fun ReflowDocument.toDto(): ReflowDocDto = ReflowDocDto(kind = kind.name, blocks = blocks.map { it.toDto() })

private fun ReflowDocDto.toModel(): ReflowDocument =
    ReflowDocument(
        kind = runCatching { PdfContentKind.valueOf(kind) }.getOrDefault(PdfContentKind.TEXT_BASED),
        blocks = blocks.map { it.toModel() },
    )

private fun ReflowBlock.toDto(): ReflowBlockDto =
    when (this) {
        is ReflowBlock.Heading -> ReflowBlockDto.Heading(text, level, source.map { it.toDto() })
        is ReflowBlock.Paragraph -> ReflowBlockDto.Paragraph(text, source.map { it.toDto() })
        is ReflowBlock.Blockquote -> ReflowBlockDto.Blockquote(text, source.map { it.toDto() })
        is ReflowBlock.ListItem -> ReflowBlockDto.ListItem(text, source.map { it.toDto() })
        is ReflowBlock.Table ->
            ReflowBlockDto.Table(rows.map { row -> row.cells.map { CellDto(it.text, it.source.map { s -> s.toDto() }) } })
        is ReflowBlock.Figure -> ReflowBlockDto.Figure(pageIndex, bounds.toDto())
        ReflowBlock.Divider -> ReflowBlockDto.Divider
    }

private fun ReflowBlockDto.toModel(): ReflowBlock =
    when (this) {
        is ReflowBlockDto.Heading -> ReflowBlock.Heading(text, level, source.map { it.toModel() })
        is ReflowBlockDto.Paragraph -> ReflowBlock.Paragraph(text, source.map { it.toModel() })
        is ReflowBlockDto.Blockquote -> ReflowBlock.Blockquote(text, source.map { it.toModel() })
        is ReflowBlockDto.ListItem -> ReflowBlock.ListItem(text, source.map { it.toModel() })
        is ReflowBlockDto.Table ->
            ReflowBlock.Table(
                rows.map { row -> ReflowBlock.TableRow(row.map { ReflowBlock.TableCell(it.text, it.source.map { s -> s.toModel() }) }) },
            )
        is ReflowBlockDto.Figure -> ReflowBlock.Figure(pageIndex, bounds.toModel())
        ReflowBlockDto.Divider -> ReflowBlock.Divider
    }

private fun SourceSpan.toDto(): SourceSpanDto = SourceSpanDto(pageIndex, charStart, charEnd, bounds.toDto(), bold, monospace)

private fun SourceSpanDto.toModel(): SourceSpan = SourceSpan(pageIndex, charStart, charEnd, bounds.toModel(), bold, monospace)

private fun ReflowRect.toDto(): RectDto = RectDto(left, top, right, bottom)

private fun RectDto.toModel(): ReflowRect = ReflowRect(left, top, right, bottom)
