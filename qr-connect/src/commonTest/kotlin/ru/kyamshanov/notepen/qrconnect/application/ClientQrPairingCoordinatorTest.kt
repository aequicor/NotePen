package ru.kyamshanov.notepen.qrconnect.application

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.qrconnect.domain.PairingUri
import ru.kyamshanov.notepen.qrconnect.domain.port.QrScanner
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.HostMessage
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.model.PairingState
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ClientQrPairingCoordinatorTest {
    @Test
    fun defaultConnectTimeoutIs60Seconds() {
        assertEquals(60.seconds, ClientQrPairingCoordinator.DEFAULT_CONNECT_TIMEOUT)
    }

    @Test
    fun defaultTimeoutAllowsHumanApprovalDelay() =
        runTest {
            val server = DeviceInfo(id = "desktop", name = "Desktop", host = "192.168.3.5", port = 44321)
            val uri =
                PairingUri(
                    host = server.host,
                    port = server.port,
                    code = CODE,
                    deviceName = server.name,
                )
            val syncClient = DelayedSuccessSyncClient(delayBeforeConnect = 13.seconds, server = server)
            val coordinator =
                ClientQrPairingCoordinator(
                    syncClient = syncClient,
                    scanner = QrScanner { flowOf(uri.encode()) },
                    selfInfo = DeviceInfo(id = "tablet", name = "Tablet", host = "", port = 0),
                )

            val states = coordinator.run().toList()

            assertIs<ClientQrPairingCoordinator.State.Scanning>(states[0])
            assertEquals(ClientQrPairingCoordinator.State.Connecting(uri), states[1])
            assertEquals(ClientQrPairingCoordinator.State.Connected(server), states[2])
        }

    private companion object {
        const val CODE = "0123456789ABCDEFGHJKMNPQRS"
    }
}

private class DelayedSuccessSyncClient(
    private val delayBeforeConnect: Duration,
    private val server: DeviceInfo,
) : SyncClient {
    override val pairingStates: Flow<Map<String, PairingState>> = flowOf(emptyMap())
    override val connectedHosts: Flow<Set<DeviceInfo>> = flowOf(emptySet())
    override val incomingMessages: Flow<HostMessage> = flowOf()

    override suspend fun connect(
        server: DeviceInfo,
        pairingCode: String,
        selfInfo: DeviceInfo,
    ): Result<DeviceInfo> {
        delay(delayBeforeConnect)
        return Result.success(this.server)
    }

    override suspend fun send(
        hostId: String,
        message: NetworkMessage,
    ) = Unit

    override suspend fun broadcast(message: NetworkMessage) = Unit

    override suspend fun disconnect(hostId: String) = Unit

    override suspend fun disconnectAll() = Unit
}
