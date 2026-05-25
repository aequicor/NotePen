package ru.kyamshanov.notepen.qrconnect.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingUriTest {
    @Test
    fun roundTripPreservesAllFields() {
        val original =
            PairingUri(
                host = "192.168.1.5",
                port = 43211,
                code = "482193",
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
                code = "100200",
                deviceName = "Ноутбук Кости",
            )
        val parsed = PairingUri.parse(original.encode())
        assertEquals(original, parsed)
    }

    @Test
    fun rejectsWrongScheme() {
        assertNull(PairingUri.parse("https://pair?h=1.2.3.4&p=80&c=111111&n=x"))
    }

    @Test
    fun rejectsWrongHost() {
        assertNull(PairingUri.parse("notepen://other?h=1.2.3.4&p=80&c=111111&n=x"))
    }

    @Test
    fun rejectsMissingCode() {
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=80&n=x"))
    }

    @Test
    fun rejectsNonNumericPort() {
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=abc&c=111111&n=x"))
    }

    @Test
    fun rejectsOutOfRangePort() {
        assertNull(PairingUri.parse("notepen://pair?h=1.2.3.4&p=70000&c=111111&n=x"))
    }

    @Test
    fun rejectsBlankHost() {
        assertNull(PairingUri.parse("notepen://pair?h=&p=80&c=111111&n=x"))
    }

    @Test
    fun rejectsRandomString() {
        assertNull(PairingUri.parse("hello world"))
        assertNull(PairingUri.parse(""))
        assertNull(PairingUri.parse("notepen://pair"))
    }

    @Test
    fun toServerDeviceInfoFillsHostPortName() {
        val uri = PairingUri("192.168.1.1", 5000, "123456", "Desktop")
        val info = uri.toServerDeviceInfo()
        assertEquals("192.168.1.1", info.host)
        assertEquals(5000, info.port)
        assertEquals("Desktop", info.name)
    }
}
