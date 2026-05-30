package ru.kyamshanov.notepen.tabs

import ru.kyamshanov.notepen.annotation.domain.model.NormalizedRect
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.annotation.domain.model.PageRotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [PdfDocumentState] remaps text-note geometry the same way it
 * remaps sticky-marker highlights when the page layout changes:
 * spread-split toggling ([PdfDocumentState.toggleSpreadSplit], mirroring
 * `splitHighlightsByPage`) and clockwise rotation
 * ([PdfDocumentState.rotatePageClockwise], mirroring `PageRotation.rotateRectCw`).
 */
class PdfDocumentStateRemapTest {
    private companion object {
        const val TOL = 1e-4f
    }

    private fun newState() = PdfDocumentState.create(filePath = "/tmp/doc.pdf", documentId = "doc")

    @Test
    fun `toggleSpreadSplit moves a left-side note to the left logical page and rescales rects`() {
        val st = newState()
        val note =
            PageNote(
                noteId = "n1",
                pageIndex = 0,
                rects = listOf(NormalizedRect(left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.3f)),
            )
        st.notes[0] = listOf(note)

        st.toggleSpreadSplit()

        val left = st.notes[0].orEmpty()
        assertEquals(1, left.size)
        assertTrue(st.notes[1].orEmpty().isEmpty())
        val r = left.single().rects.single()
        assertEquals(0.2f, r.left, TOL)
        assertEquals(0.6f, r.right, TOL)
        assertEquals(0.2f, r.top, TOL)
        assertEquals(0.3f, r.bottom, TOL)
        assertEquals(0, left.single().pageIndex)
    }

    @Test
    fun `toggleSpreadSplit moves a right-side note to the right logical page`() {
        val st = newState()
        val note =
            PageNote(
                noteId = "n1",
                pageIndex = 0,
                rects = listOf(NormalizedRect(left = 0.6f, top = 0.2f, right = 0.8f, bottom = 0.3f)),
            )
        st.notes[0] = listOf(note)

        st.toggleSpreadSplit()

        assertTrue(st.notes[0].orEmpty().isEmpty())
        val right = st.notes[1].orEmpty()
        assertEquals(1, right.size)
        val r = right.single().rects.single()
        assertEquals(0.2f, r.left, TOL)
        assertEquals(0.6f, r.right, TOL)
        assertEquals(1, right.single().pageIndex)
    }

    @Test
    fun `toggleSpreadSplit round-trips notes (split then merge)`() {
        val st = newState()
        val rect = NormalizedRect(left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.3f)
        val note = PageNote(noteId = "n1", pageIndex = 0, rects = listOf(rect))
        st.notes[0] = listOf(note)

        st.toggleSpreadSplit()
        st.toggleSpreadSplit()

        val restored = st.notes[0].orEmpty()
        assertEquals(1, restored.size)
        val r = restored.single().rects.single()
        assertEquals(rect.left, r.left, TOL)
        assertEquals(rect.right, r.right, TOL)
        assertEquals(rect.top, r.top, TOL)
        assertEquals(rect.bottom, r.bottom, TOL)
        assertEquals(0, restored.single().pageIndex)
    }

    @Test
    fun `rotatePageClockwise rotates note rects but keeps pageIndex`() {
        val st = newState()
        val rect = NormalizedRect(left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.4f)
        val note = PageNote(noteId = "n1", pageIndex = 0, rects = listOf(rect))
        st.notes[0] = listOf(note)

        st.rotatePageClockwise(0, pageAspectBeforeRotation = 1f)

        val rotated = st.notes[0].orEmpty()
        assertEquals(1, rotated.size)
        val expected = PageRotation.rotateRectCw(rect)
        val r = rotated.single().rects.single()
        assertEquals(expected.left, r.left, TOL)
        assertEquals(expected.top, r.top, TOL)
        assertEquals(expected.right, r.right, TOL)
        assertEquals(expected.bottom, r.bottom, TOL)
        assertEquals(0, rotated.single().pageIndex)
    }
}
