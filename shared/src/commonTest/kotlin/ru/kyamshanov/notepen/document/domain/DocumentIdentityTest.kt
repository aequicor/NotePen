package ru.kyamshanov.notepen.document.domain

import ru.kyamshanov.notepen.document.domain.model.WIRE_ID_HASH_PREFIX_LENGTH
import ru.kyamshanov.notepen.document.domain.model.basenameOf
import ru.kyamshanov.notepen.document.domain.model.wireIdOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DocumentIdentityTest {
    @Test
    fun wireIdHasBasenameAnd16HexPrefix() {
        val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        val wireId = wireIdOf("book.pdf", sha)

        assertEquals("book.pdf#0123456789abcdef", wireId)
        val suffix = wireId.substringAfterLast('#')
        assertEquals(WIRE_ID_HASH_PREFIX_LENGTH, suffix.length)
        assertTrue(suffix.all { it in "0123456789abcdef" })
    }

    @Test
    fun basenameStripsBothSeparators() {
        assertEquals("book.pdf", basenameOf("/home/user/docs/book.pdf"))
        assertEquals("book.pdf", basenameOf("C:\\Users\\me\\book.pdf"))
        assertEquals("book.pdf", basenameOf("book.pdf"))
    }

    @Test
    fun sameBytesYieldSameDigestDifferentBytesDiffer() {
        val a = sha256Hex("hello world".encodeToByteArray())
        val aAgain = sha256Hex("hello world".encodeToByteArray())
        val b = sha256Hex("hello WORLD".encodeToByteArray())

        assertEquals(a, aAgain)
        assertNotEquals(a, b)
    }

    @Test
    fun sha256MatchesKnownVector() {
        // Reference SHA-256 of the empty input — guards the hex-encoding too.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
        assertEquals(64, sha256Hex("anything".encodeToByteArray()).length)
    }
}
