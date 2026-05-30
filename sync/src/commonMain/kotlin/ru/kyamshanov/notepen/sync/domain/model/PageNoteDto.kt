package ru.kyamshanov.notepen.sync.domain.model

import kotlinx.serialization.Serializable
import ru.kyamshanov.notepen.annotation.domain.model.NormalizedRect
import ru.kyamshanov.notepen.annotation.domain.model.PageNote

/**
 * Serialisation-safe rectangle in normalised page coordinates `[0..1]`, mirroring
 * [NormalizedRect]. Distinct from [RectDto], whose [RectDto.toDomain] returns a
 * [ru.kyamshanov.notepen.annotation.domain.model.PageExtent], not a page rect.
 */
@Serializable
data class NormalizedRectDto(
    val l: Float,
    val t: Float,
    val r: Float,
    val b: Float,
) {
    fun toDomain(): NormalizedRect = NormalizedRect(left = l, top = t, right = r, bottom = b)

    companion object {
        fun fromDomain(rect: NormalizedRect): NormalizedRectDto =
            NormalizedRectDto(l = rect.left, t = rect.top, r = rect.right, b = rect.bottom)
    }
}

/**
 * Wire DTO for [PageNote]. Geometry ([rects]) is the source of truth; [quote]/[context]
 * are a TextQuoteSelector fallback. Defaults mirror [PageNote] for forward/backward
 * compatibility; the [colorArgb] default of 0 is harmless because DTOs are always
 * built via [fromDomain], which carries the real value.
 */
@Serializable
data class PageNoteDto(
    val noteId: String = "",
    val rects: List<NormalizedRectDto> = emptyList(),
    val pageIndex: Int = 0,
    val quote: String = "",
    val context: String = "",
    val body: String = "",
    val colorArgb: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    fun toDomain(): PageNote =
        PageNote(
            noteId = noteId,
            rects = rects.map { it.toDomain() },
            pageIndex = pageIndex,
            quote = quote,
            context = context,
            body = body,
            colorArgb = colorArgb,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        fun fromDomain(note: PageNote): PageNoteDto =
            PageNoteDto(
                noteId = note.noteId,
                rects = note.rects.map { NormalizedRectDto.fromDomain(it) },
                pageIndex = note.pageIndex,
                quote = note.quote,
                context = note.context,
                body = note.body,
                colorArgb = note.colorArgb,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
            )
    }
}

/** Top-level alias so editor glue can `import ...toDto` (see plan §0.4). */
fun PageNote.toDto(): PageNoteDto = PageNoteDto.fromDomain(this)
