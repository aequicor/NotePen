package ru.kyamshanov.notepen

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import kotlin.math.roundToInt

internal data class BroadcastViewportCommand(
    val page: Int,
    val pageOffsetPx: Int,
    val targetScalePercent: Int?,
    val targetPanX: Float?,
)

internal sealed class BroadcastDocumentOpenAction {
    data object Ignore : BroadcastDocumentOpenAction()

    data class FocusExisting(
        val documentId: String,
    ) : BroadcastDocumentOpenAction()

    data class OpenResolved(
        val documentId: String,
        val uri: String,
    ) : BroadcastDocumentOpenAction()
}

internal fun broadcastDocumentId(frame: NetworkMessage.ProjectionFrame?): String? = frame?.documentId?.takeIf { it.isNotBlank() }

internal fun hasActiveBroadcastConnection(
    connectedPeers: Set<DeviceInfo>,
    connectedHosts: Set<DeviceInfo>,
): Boolean = connectedPeers.isNotEmpty() || connectedHosts.isNotEmpty()

internal fun activeBroadcastConnection(
    peerServer: PeerServer?,
    peerClient: SyncClient?,
): Flow<Boolean> =
    combine(
        peerServer?.connectedPeers ?: flowOf(emptySet()),
        peerClient?.connectedHosts ?: flowOf(emptySet()),
    ) { connectedPeers, connectedHosts ->
        hasActiveBroadcastConnection(
            connectedPeers = connectedPeers,
            connectedHosts = connectedHosts,
        )
    }

internal fun shouldAutoEnableLiveSync(
    hasBroadcastConnection: Boolean,
    userPausedSync: Boolean,
): Boolean = hasBroadcastConnection && !userPausedSync

internal fun shouldPublishBroadcastFrame(
    hasPeerClient: Boolean,
    hasPeerServer: Boolean,
    isFocused: Boolean,
    liveSyncEnabled: Boolean,
): Boolean = hasPeerClient && !hasPeerServer && isFocused && liveSyncEnabled

internal fun shouldApplyBroadcastFrame(
    hasPeerServer: Boolean,
    isFocused: Boolean,
    hasBroadcastConnection: Boolean,
): Boolean = hasPeerServer && isFocused && hasBroadcastConnection

internal fun broadcastFrameForDocument(
    frame: NetworkMessage.ProjectionFrame?,
    documentId: String?,
): NetworkMessage.ProjectionFrame? {
    val id = documentId?.takeIf { it.isNotBlank() } ?: return null
    return frame?.takeIf { it.documentId == id }
}

internal fun broadcastToolModeForDocument(
    frame: NetworkMessage.ProjectionFrame?,
    documentId: String?,
): ToolMode? = broadcastFrameForDocument(frame, documentId)?.toolMode

internal fun displayedToolMode(
    localToolMode: ToolMode,
    broadcastToolMode: ToolMode?,
): ToolMode = broadcastToolMode ?: localToolMode

internal fun broadcastViewportCommandForDocument(
    frame: NetworkMessage.ProjectionFrame?,
    documentId: String?,
    currentScalePercent: Int,
    currentPanX: Float,
): BroadcastViewportCommand? {
    val target = broadcastFrameForDocument(frame, documentId) ?: return null
    val targetScalePercent =
        target.viewportScale
            .takeIf { it.isFinite() && it > 0f }
            ?.let { (it * 100f).roundToInt() }
    val scaleChange = targetScalePercent?.takeIf { it != currentScalePercent } != null
    val targetPanX =
        target.viewportOffsetX
            .takeIf { it.isFinite() }
            ?.takeIf { panX ->
                scaleChange ||
                    !currentPanX.isFinite() ||
                    panX - currentPanX > 0.5f ||
                    currentPanX - panX > 0.5f
            }
    return BroadcastViewportCommand(
        page = target.page.coerceAtLeast(0),
        pageOffsetPx =
            target.viewportOffsetY
                .takeIf { it.isFinite() }
                ?.roundToInt()
                ?.coerceAtLeast(0)
                ?: 0,
        targetScalePercent =
            targetScalePercent?.takeIf { it != currentScalePercent },
        targetPanX = targetPanX,
    )
}

internal fun broadcastDocumentOpenAction(
    frame: NetworkMessage.ProjectionFrame?,
    documentAlreadyOpen: Boolean,
    resolvedUri: String?,
): BroadcastDocumentOpenAction =
    broadcastDocumentId(frame)
        ?.let { documentId ->
            when {
                documentAlreadyOpen -> BroadcastDocumentOpenAction.FocusExisting(documentId)
                resolvedUri.isNullOrBlank() -> BroadcastDocumentOpenAction.Ignore
                else ->
                    BroadcastDocumentOpenAction.OpenResolved(
                        documentId = documentId,
                        uri = resolvedUri,
                    )
            }
        }
        ?: BroadcastDocumentOpenAction.Ignore
