package ru.kyamshanov.notepen.pdf.infrastructure

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpubFileTypesTest {

    @Test
    fun detects_epub_by_extension_case_insensitively() {
        assertTrue(isEpubPath("/books/book.epub"))
        assertTrue(isEpubPath("file:///books/Book.EPUB"))
        assertTrue(isEpubPath("/books/book.epub?download=1"))
    }

    @Test
    fun rejects_non_epub_paths() {
        assertFalse(isEpubPath("/books/file.pdf"))
        assertFalse(isEpubPath("/books/image.png"))
        assertFalse(isEpubPath("/books/no-extension"))
    }

    @Test
    fun detects_epub_by_mime() {
        assertTrue(isEpubMime("application/epub+zip"))
        assertTrue(isEpubMime("APPLICATION/EPUB+ZIP"))
        assertFalse(isEpubMime("application/pdf"))
        assertFalse(isEpubMime(null))
    }
}
