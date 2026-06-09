package ru.kyamshanov.notepen.qrconnect.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingUriTest {
    @Test
    fun roundTripPreservesAllFields() {
        val original =
            PairingUri(
                host = "192.168.1.5",
                port = 43211,
                code = CODE_A,
                deviceName = "Konstantin's MacBook Pro",
            )
        val parsed = PairingUri.parse(original.encode())
        assertEquals(original, parsed)
    }

    @Test
    fun roundTripWithCyrillicDeviceName() {
        val original =
            PairingUri(
                host = "10.0.0.42",
                port = 8080,
                code = CODE_B,
                deviceName = "Ноутбук Кости",
            )
        val parsed = PairingUri.parse(original.encode())
        assertEquals(original, parsed)
    }

    @Test
    fun roundTripWithLibraryFields() {
        val original =
            PairingUri(
                host = "192.168.1.5",
                port = 43211,
                code = CODE_A,
                deviceName = "Konstantin's MacBook Pro",
                libraryId = "local:/Users/k/NotePen Library/Math",
                libraryName = "Математика 101",
            )
        val parsed = PairingUri.parse(original.encode())
        assertEquals(original, parsed)
    }

    @Test
    fun parsesUriWithoutLibraryFieldsAsBlank() {
        val parsed = PairingUri.parse("notepen://pair?h=1.2.3.4&p=80&c=$CODE_A&n=x")
        assertEquals("", parsed?.libraryId)
        assertEquals("", parsed?.libraryName)
    }

    @Test
    fun encodeOmitsBlankLibraryFields() {
        val encoded = PairingUri("1.2.3.4", 80, CODE_A, "x").encode()
        assertEquals(false, encoded.contains("&l="))
        assertEquals(false, encoded.contains("&ln="))
    }

    @Test
    fun rejectsWrongScheme() {
        assertNull(PairingUri.parse("https://pair?h=1.2.3.4&p=80&c=$CODE_A&n=x"))
    }

    @Test
    fun rejectsWrongHost() {
        assertNull(PairingUri.parse("notepen://other?h=1.2.3.4&p=80&c=$CODE_A&n=x"))
    }

    @Test
    fun rejectsMissingCode() {
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=80&n=x"))
    }

    @Test
    fun rejectsNonNumericPort() {
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=abc&c=$CODE_A&n=x"))
    }

    @Test
    fun rejectsOutOfRangePort() {
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=70000&c=$CODE_A&n=x"))
    }

    @Test
    fun rejectsBlankHost() {
        assertNull(PairingUri.parse("notepen://pair?h=&p=80&c=$CODE_A&n=x"))
    }

    @Test
    fun rejectsRandomString() {
        assertNull(PairingUri.parse("hello world"))
        assertNull(PairingUri.parse(""))
        assertNull(PairingUri.parse("notepen://pair"))
    }

    @Test
    fun rejectsTooShortCode() {
        // A legacy 6-digit code is below the new length and must be refused.
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=80&c=482193&n=x"))
    }

    @Test
    fun rejectsTooLongCode() {
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=80&c=${CODE_A}0&n=x"))
    }

    @Test
    fun rejectsCodeWithOutOfAlphabetChar() {
        // 'I' (and L/O/U) are excluded from Crockford base32; lowercase is also invalid.
        val withI = "I" + CODE_A.substring(1)
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=80&c=$withI&n=x"))
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=80&c=${CODE_A.lowercase()}&n=x"))
    }

    @Test
    fun acceptsFullLengthCrockfordCode() {
        val parsed = PairingUri.parse("notepen://pair?h=1.2.3.4&p=80&c=$CODE_A&n=x")
        assertNotNull(parsed)
        assertEquals(CODE_A, parsed.code)
        assertTrue(CODE_A.length == PairingUri.CODE_LENGTH)
    }

    @Test
    fun toServerDeviceInfoFillsHostPortName() {
        val uri = PairingUri("192.168.1.1", 5000, CODE_B, "Desktop")
        val info = uri.toServerDeviceInfo()
        assertEquals("192.168.1.1", info.host)
        assertEquals(5000, info.port)
        assertEquals("Desktop", info.name)
    }

    private companion object {
        // Two valid 26-char Crockford base32 codes (alphabet 0-9 A-Z minus I,L,O,U).
        const val CODE_A = "0123456789ABCDEFGHJKMNPQRS"
        const val CODE_B = "TVWXYZ0123456789ABCDEFGHJK"
    }
}
