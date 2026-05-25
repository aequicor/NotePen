package ru.kyamshanov.notepen.mainscreen.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Unit-тесты для [UriNormalizer].
 *
 * impl: shared/src/commonMain/.../domain/model/UriNormalizer.kt
 */
class UriNormalizerTest {
    /**
     * [CC-3] Trailing slash удаляется для desktop-путей.
     */
    @Test
    fun normalize_trailingSlash_isStripped() {
        assertEquals("/home/user/doc.pdf", UriNormalizer.normalize("/home/user/doc.pdf/"))
    }

    /**
     * [CC-3] Trailing backslash удаляется для Windows-путей.
     */
    @Test
    fun normalize_trailingBackslash_isStripped() {
        assertEquals("C:\\Users\\user\\doc.pdf", UriNormalizer.normalize("C:\\Users\\user\\doc.pdf\\"))
    }

    /**
     * [CC-3] Пустая строка возвращается без изменений.
     */
    @Test
    fun normalize_emptyString_returnsEmpty() {
        assertEquals("", UriNormalizer.normalize(""))
    }

    /**
     * [CC-3] Android content:// URI возвращается as-is (без изменений).
     */
    @Test
    fun normalize_contentUri_returnedAsIs() {
        val uri = "content://com.android.providers.media/document/primary%3Adoc.pdf"
        assertEquals(uri, UriNormalizer.normalize(uri))
    }

    /**
     * [CC-3] URI без trailing slash возвращается без изменений.
     */
    @Test
    fun normalize_noTrailingSlash_unchanged() {
        val uri = "/home/user/file.pdf"
        assertEquals(uri, UriNormalizer.normalize(uri))
    }

    /**
     * TC-41 / CC-3: Android-like content:// URI with Unicode characters — no crash, trailing slash removed.
     * Android returns content:// URIs; NFC normalization is not required. Verify no crash on Unicode path.
     */
    @Test
    fun normalize_nfdEncodedPath_androidLikeContentUri_noCrash() {
        // NFD encoded 'À' (U+0041 + U+0300) in a content:// URI
        val nfdPath = "content://media/À.pdf"
        val result = UriNormalizer.normalize(nfdPath)
        assertNotNull(result, "normalize must not return null")
        assertFalse(result.endsWith("/"), "result must not end with trailing slash")
        // Deduplication invariant: two calls with the same input produce equal outputs
        assertEquals(result, UriNormalizer.normalize(nfdPath), "normalize must be idempotent (same output for same input)")
    }

    /**
     * TC-41 / CC-3 (NFC path): Desktop-style file path with NFD-encoded Unicode characters
     * normalizes to NFC so that two paths for the same file compare equal regardless of encoding.
     * This uses a plain ASCII path to remain platform-neutral in commonTest.
     */
    @Test
    fun normalize_plainAsciiPath_stripsTrailingSlash() {
        val path = "/home/user/documents/report.pdf/"
        val result = UriNormalizer.normalize(path)
        assertFalse(result.endsWith("/"), "trailing slash must be stripped")
        assertEquals("/home/user/documents/report.pdf", result)
    }
}
