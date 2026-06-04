package ru.kyamshanov.notepen.sync.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentIdTest {
    @Test
    fun safeFileNameComponent_stripsDirectoryTraversal() {
        // A classic path-traversal display name must collapse to its last
        // component with no separators — it can never address a parent dir.
        val result = safeFileNameComponent("../../evil")
        assertEquals("evil", result)
        assertFalse(result.contains('/'))
        assertFalse(result.contains('\\'))
    }

    @Test
    fun safeFileNameComponent_stripsWindowsAndPosixSeparators() {
        assertEquals("c", safeFileNameComponent("a/b/c"))
        assertEquals("c", safeFileNameComponent("a\\b\\c"))
        assertEquals("x", safeFileNameComponent("/etc/x"))
    }

    @Test
    fun safeFileNameComponent_mapsDangerousResultsToFallback() {
        assertEquals("_", safeFileNameComponent(""))
        assertEquals("_", safeFileNameComponent("."))
        assertEquals("_", safeFileNameComponent(".."))
        // Trailing separator leaves an empty last component → fallback.
        assertEquals("_", safeFileNameComponent("foo/"))
        assertEquals("_", safeFileNameComponent("a/b/.."))
    }

    @Test
    fun safeFileNameComponent_replacesControlCharacters() {
        // NUL (0x00), DEL (0x7F) and unit-separator (0x1F) each collapse to '_'.
        val result = safeFileNameComponent("ab" + 0.toChar() + "cd" + 0x7F.toChar() + "ef" + 0x1F.toChar() + "gh")
        assertEquals("ab_cd_ef_gh", result)
    }

    @Test
    fun documentIdToCacheFileName_neverContainsSeparators() {
        // Both inputs are network-supplied; neither may inject a separator into
        // the produced cache filename.
        val name =
            documentIdToCacheFileName(
                documentId = "../../book.pdf#dead",
                displayName = "../../../etc/passwd",
            )
        assertFalse(name.contains('/'), "produced name leaked a '/': $name")
        assertFalse(name.contains('\\'), "produced name leaked a '\\': $name")
        // '#' from the documentId is still normalised to '_'.
        assertFalse(name.contains('#'))
        assertTrue(name.contains("__"))
    }

    @Test
    fun documentIdToCacheFileName_preservesBenignNames() {
        val name = documentIdToCacheFileName(documentId = "book.pdf#abcd1234", displayName = "book.pdf")
        assertEquals("book.pdf_abcd1234__book.pdf", name)
    }
}
