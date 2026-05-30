package ru.kyamshanov.notepen.annotation.domain.model

import kotlinx.serialization.Serializable

/**
 * A text note attached to document content, visible in both the PDF-raster
 * editor and the reflow reading mode.
 *
 * **Geometry is the source of truth.** Like [StickyHighlight], a note anchors to
 * a set of word-aligned normalized page rectangles ([rects]); the editor renders
 * the note directly from them. A note created in reading mode is born from a text
 * selection immediately projected back to page rects, after which it behaves
 * exactly like a highlight.
 *
 * It additionally carries a [body] (the user's text) and a stored [quote] +
 * [context] (a W3C TextQuoteSelector) — a fallback for re-anchoring in reading
 * mode when rect-based re-anchoring fails after a reflow re-extraction.
 *
 * Coordinates are normalized to the page `[0..1]`, origin top-left, Y down.
 *
 * @property noteId stable sync id (reuses the stroke-id scheme); "" = local-only
 * @property rects source-of-truth normalized areas (see [NormalizedRect])
 * @property pageIndex primary zero-based page (derived from the first rect's page)
 * @property quote highlighted text (TextQuoteSelector); "" for image-only pages
 * @property context small prefix/suffix around [quote] for robust re-find
 * @property body the note's text content
 * @property colorArgb packed ARGB of the note accent (alpha included)
 * @property createdAt creation time, epoch millis (0 if unset)
 * @property updatedAt last-edit time, epoch millis (0 if unset)
 */
@Serializable
data class PageNote(
    val noteId: String = "",
    val rects: List<NormalizedRect> = emptyList(),
    val pageIndex: Int = 0,
    val quote: String = "",
    val context: String = "",
    val body: String = "",
    val colorArgb: Long = MarkerSettings.PRESET_COLORS[0],
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
