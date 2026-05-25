package ru.kyamshanov.notepen.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookFileTypesTest {
    @Test
    fun detects_epub_by_extension_case_insensitively() {
        assertEquals(BookFormat.EPUB, detectBookFormat("/books/book.epub"))
        assertEquals(BookFormat.EPUB, detectBookFormat("file:///books/Book.EPUB"))
        assertEquals(BookFormat.EPUB, detectBookFormat("/books/book.epub?download=1"))
    }

    @Test
    fun returns_null_for_unsupported_paths() {
        assertNull(detectBookFormat("/books/file.pdf"))
        assertNull(detectBookFormat("/books/image.png"))
        assertNull(detectBookFormat("/books/no-extension"))
    }

    @Test
    fun detects_epub_by_mime_case_insensitively() {
        assertEquals(BookFormat.EPUB, detectBookFormat("/x", "application/epub+zip"))
        assertEquals(BookFormat.EPUB, detectBookFormat("/x", "APPLICATION/EPUB+ZIP"))
        assertNull(detectBookFormat("/x", "application/pdf"))
    }

    @Test
    fun falls_back_to_extension_when_mime_is_generic() {
        assertEquals(BookFormat.EPUB, detectBookFormat("/books/book.epub", "application/octet-stream"))
        assertNull(detectBookFormat("/books/file.pdf", null))
    }

    @Test
    fun detects_fb2_by_extension_and_zip_wrapper() {
        assertEquals(BookFormat.FB2, detectBookFormat("/books/book.fb2"))
        assertEquals(BookFormat.FB2, detectBookFormat("/books/Book.FB2"))
        assertEquals(BookFormat.FB2, detectBookFormat("/books/book.fb2.zip"))
    }

    @Test
    fun detects_fb2_by_mime() {
        assertEquals(BookFormat.FB2, detectBookFormat("/x", "application/x-fictionbook+xml"))
    }

    @Test
    fun detects_comic_formats_by_extension_and_mime() {
        assertEquals(BookFormat.CBZ, detectBookFormat("/c/book.cbz"))
        assertEquals(BookFormat.CBR, detectBookFormat("/c/book.CBR"))
        assertEquals(BookFormat.CBZ, detectBookFormat("/x", "application/vnd.comicbook+zip"))
        assertEquals(BookFormat.CBR, detectBookFormat("/x", "application/vnd.comicbook-rar"))
    }
}
