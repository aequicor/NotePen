package ru.kyamshanov.notepen.sync.infrastructure

import ru.kyamshanov.notepen.sync.domain.model.ConnectFailureKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectFailureClassifierTest {
    @Test
    fun `EPERM maps to LOCAL_NETWORK_BLOCKED`() {
        assertEquals(
            ConnectFailureKind.LOCAL_NETWORK_BLOCKED,
            classifyConnectFailure(Exception("sendto failed: EPERM (Operation not permitted)")),
        )
    }

    @Test
    fun `permission denied maps to LOCAL_NETWORK_BLOCKED`() {
        assertEquals(
            ConnectFailureKind.LOCAL_NETWORK_BLOCKED,
            classifyConnectFailure(Exception("socket failed: Permission denied")),
        )
    }

    @Test
    fun `connection refused maps to CONNECTION_REFUSED`() {
        assertEquals(
            ConnectFailureKind.CONNECTION_REFUSED,
            classifyConnectFailure(Exception("Connection refused: connect")),
        )
    }

    @Test
    fun `timeout message maps to TIMED_OUT`() {
        assertEquals(
            ConnectFailureKind.TIMED_OUT,
            classifyConnectFailure(Exception("connect timed out")),
        )
    }

    @Test
    fun `cause chain is traversed`() {
        val wrapped = Exception("websocket failed", Exception("Connection refused"))
        assertEquals(ConnectFailureKind.CONNECTION_REFUSED, classifyConnectFailure(wrapped))
    }

    @Test
    fun `unrecognized and null map to UNKNOWN`() {
        assertEquals(ConnectFailureKind.UNKNOWN, classifyConnectFailure(Exception("boom")))
        assertEquals(ConnectFailureKind.UNKNOWN, classifyConnectFailure(null))
    }

    @Test
    fun `blocked wins over timeout when both markers present`() {
        // An Android 17 denial often surfaces as a timeout wrapping an EPERM cause.
        assertEquals(
            ConnectFailureKind.LOCAL_NETWORK_BLOCKED,
            classifyConnectFailure(Exception("timeout: EPERM")),
        )
        assertEquals(
            ConnectFailureKind.LOCAL_NETWORK_BLOCKED,
            classifyConnectFailure(Exception("connect timed out", Exception("sendto failed: EPERM"))),
        )
    }
}
