package ru.kyamshanov.notepen.qrconnect.application

import ru.kyamshanov.notepen.sync.domain.exception.SyncConnectException
import ru.kyamshanov.notepen.sync.domain.model.ConnectFailureKind
import ru.kyamshanov.notepen.sync.domain.model.LocalNetworkHealth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectFailureMessagesTest {
    private val allClear = LocalNetworkHealth(localNetworkPermissionGranted = true, vpnActive = false)
    private val vpnOn = LocalNetworkHealth(localNetworkPermissionGranted = true, vpnActive = true)
    private val permissionDenied = LocalNetworkHealth(localNetworkPermissionGranted = false, vpnActive = false)

    @Test
    fun `vpn hint appended when vpn active`() {
        val msg =
            ConnectFailureMessages.describe(
                SyncConnectException(ConnectFailureKind.TIMED_OUT, "timed out"),
                vpnOn,
            )
        assertTrue(msg.contains("VPN"), msg)
    }

    @Test
    fun `permission hint appended when local network permission denied`() {
        val msg =
            ConnectFailureMessages.describe(
                SyncConnectException(ConnectFailureKind.TIMED_OUT, "timed out"),
                permissionDenied,
            )
        assertTrue(msg.contains("доступ к локальной сети"), msg)
    }

    @Test
    fun `no hints when environment is clear`() {
        val msg =
            ConnectFailureMessages.describe(
                SyncConnectException(ConnectFailureKind.TIMED_OUT, "timed out"),
                allClear,
            )
        assertFalse(msg.contains("VPN"), msg)
        assertFalse(msg.contains("Разрешите"), msg)
    }

    @Test
    fun `pairing rejection keeps reason and skips network hints`() {
        val msg =
            ConnectFailureMessages.describe(
                SyncConnectException(ConnectFailureKind.PAIRING_REJECTED, "invalid code"),
                vpnOn,
            )
        assertEquals("invalid code", msg)
    }

    @Test
    fun `unknown error falls back to its message`() {
        val msg = ConnectFailureMessages.describe(Exception("boom"), null)
        assertEquals("boom", msg)
    }

    @Test
    fun `timedOut includes hints`() {
        val msg = ConnectFailureMessages.timedOut(vpnOn)
        assertTrue(msg.contains("Таймаут"), msg)
        assertTrue(msg.contains("VPN"), msg)
    }
}
