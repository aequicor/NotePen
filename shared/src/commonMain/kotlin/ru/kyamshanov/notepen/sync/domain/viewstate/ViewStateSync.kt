package ru.kyamshanov.notepen.sync.domain.viewstate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage

/**
 * Peer-agnostic, symmetric viewport synchronisation.
 *
 * Both peers can mutate the local viewport (page index, zoom %, in-page scroll
 * offset) and call [publish]; the value is conflated and forwarded over the
 * wire as [NetworkMessage.ViewStateMessage]. Incoming messages from the peer
 * are exposed via [remoteState].
 *
 * The class is intentionally infrastructure-free: pass a `send` lambda and an
 * incoming-message [Flow] so the same logic works over [PeerServer] and
 * [SyncClient] without duplication.
 *
 * @param incoming raw inbound network messages from the underlying transport
 * @param send suspending sender into the underlying transport
 * @param scope lifecycle scope for the publisher / collector jobs
 */
public class ViewStateSync(
    incoming: Flow<NetworkMessage>,
    private val send: suspend (NetworkMessage) -> Unit,
    scope: CoroutineScope,
) {
    private val _outgoing = MutableStateFlow<NetworkMessage.ViewStateMessage?>(null)
    private val _remoteState = MutableStateFlow<NetworkMessage.ViewStateMessage?>(null)

    /** Last viewport state received from the remote peer; null if none yet. */
    public val remoteState: StateFlow<NetworkMessage.ViewStateMessage?> = _remoteState.asStateFlow()

    init {
        scope.launch {
            _outgoing
                .filterNotNull()
                .conflate()
                .collect { send(it) }
        }
        scope.launch {
            incoming
                .filterIsInstance<NetworkMessage.ViewStateMessage>()
                .collect { _remoteState.value = it }
        }
    }

    /** Emit a new local viewport snapshot. Thread-safe; conflated downstream. */
    public fun publish(page: Int, scale: Int, pageScrollOffsetPx: Int) {
        _outgoing.value = NetworkMessage.ViewStateMessage(
            page = page,
            scale = scale,
            pageScrollOffsetPx = pageScrollOffsetPx,
        )
    }
}
