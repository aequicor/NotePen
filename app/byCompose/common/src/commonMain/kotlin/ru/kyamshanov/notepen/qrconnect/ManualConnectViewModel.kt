package ru.kyamshanov.notepen.qrconnect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.qrconnect.domain.PairingUri
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.port.SyncClient

/**
 * Drives the manual-connect form on the client.
 *
 * The user pastes one string — the canonical `notepen://pair?...` payload the
 * host displays under «Данные для ручного подключения» — and the VM parses it
 * via [PairingUri.parse] before forwarding to [SyncClient].
 */
class ManualConnectViewModel(
    private val syncClient: SyncClient,
    private val selfInfo: DeviceInfo,
    private val scope: CoroutineScope,
) {
    /** Outcome of the latest manual-connect attempt. */
    sealed class Status {
        /** No attempt in progress; ready for input. */
        data object Idle : Status()

        /** Submitting to [SyncClient]. */
        data object Connecting : Status()

        /** Latest attempt succeeded — peer info is the host reported by the server. */
        data class Connected(val peer: DeviceInfo) : Status()

        /** Latest attempt failed with the human-readable [message]. */
        data class Failed(val message: String) : Status()
    }

    private val _payload = MutableStateFlow("")
    val payload: StateFlow<String> = _payload.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    val canConnect: StateFlow<Boolean> =
        combine(_payload, _status) { p, st ->
            st !is Status.Connecting && PairingUri.parse(p.trim()) != null
        }.stateIn(scope, SharingStarted.Eagerly, false)

    private var connectJob: Job? = null

    fun onPayloadChange(value: String) {
        _payload.value = value
        if (_status.value !is Status.Connecting) _status.value = Status.Idle
    }

    fun connect() {
        if (!canConnect.value) return
        val uri = PairingUri.parse(_payload.value.trim()) ?: return
        connectJob?.cancel()
        _status.value = Status.Connecting
        connectJob =
            scope.launch {
                val result =
                    runCatching {
                        syncClient.connect(uri.toServerDeviceInfo(), uri.code, selfInfo)
                    }.getOrElse { Result.failure(it) }
                _status.value =
                    result.fold(
                        onSuccess = { peer ->
                            _payload.value = ""
                            Status.Connected(peer)
                        },
                        onFailure = { e -> Status.Failed(e.message ?: "Не удалось подключиться") },
                    )
            }
    }
}
