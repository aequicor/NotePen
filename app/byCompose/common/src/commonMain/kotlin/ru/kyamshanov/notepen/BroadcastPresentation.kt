package ru.kyamshanov.notepen

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import ru.kyamshanov.notepen.sync.domain.port.PeerServer
import ru.kyamshanov.notepen.sync.domain.port.SyncClient
import kotlin.math.abs
import kotlin.math.roundToInt

private const val VIEWPORT_OFFSET_EPSILON_PX = 8
private const val VIEWPORT_CENTER_X_EPSILON = 0.002f

private data class VerticalViewportRow(
    val startIndex: Int,
    val topPx: Float,
    val heightPx: Float,
)

internal data class BroadcastViewportCommand(
    val page: Int,
    val pageOffsetPx: Int,
    val shouldScroll: Boolean,
    val targetScalePercent: Int?,
    val targetPanX: Float?,
    val targetPanY: Float?,
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

internal fun projectedToolMode(
    localToolMode: ToolMode,
    eraserOverride: Boolean,
): ToolMode = if (eraserOverride) ToolMode.ERASER else localToolMode

internal fun broadcastViewportCommandForDocument(
    frame: NetworkMessage.ProjectionFrame?,
    documentId: String?,
    previousFrame: NetworkMessage.ProjectionFrame? = null,
    currentPage: Int,
    currentPageOffsetPx: Int,
    currentScalePercent: Int,
    currentPanX: Float,
    currentPanY: Float,
    currentViewportWidthPx: Float,
    currentViewportHeightPx: Float,
    currentRowWidthPx: Float,
    currentPageTopsPx: FloatArray,
    currentPageHeightsPx: FloatArray,
    applyPanDuringScaleChange: Boolean = false,
): BroadcastViewportCommand? {
    val target = broadcastFrameForDocument(frame, documentId) ?: return null
    val targetScale =
        target.viewportScale
            .takeIf { it.isFinite() && it > 0f }
    val targetScalePercent =
        targetScale
            ?.let { (it * 100f).roundToInt() }
    val commandScale = targetScalePercent?.let { it / 100f } ?: targetScale ?: currentScalePercent / 100f
    val scaleChange = targetScalePercent?.takeIf { it != currentScalePercent } != null
    val pageOffsetPx =
        target.viewportOffsetY
            .takeIf { it.isFinite() }
            ?.roundToInt()
            ?.coerceAtLeast(0)
            ?: 0
    val verticalChange =
        target.page.coerceAtLeast(0) != currentPage ||
            abs(pageOffsetPx - currentPageOffsetPx) > VIEWPORT_OFFSET_EPSILON_PX
    val remoteHorizontalChange = target.remoteHorizontalChangeFrom(previousFrame) ?: !verticalChange
    val normalizedTargetPanX =
        target.viewportCenterX
            ?.takeIf { it.isFinite() }
            ?.let { centerX ->
                panXForViewportCenter(
                    centerX = centerX,
                    viewportWidthPx = currentViewportWidthPx,
                    rowWidthPx = currentRowWidthPx,
                    scale = commandScale,
                )
            }
    val normalizedTargetPanY =
        target.viewportCenterY
            ?.takeIf { it.isFinite() && it >= 0f }
            ?.let { centerY ->
                panYForViewportCenter(
                    centerY = centerY,
                    viewportHeightPx = currentViewportHeightPx,
                    pageTopsPx = currentPageTopsPx,
                    pageHeightsPx = currentPageHeightsPx,
                    scale = commandScale,
                )
            }
    val hasResolvableCenterY = target.viewportCenterY?.let { it.isFinite() && it >= 0f } == true
    val targetPanX =
        when {
            normalizedTargetPanX != null ->
                normalizedTargetPanX.takeIf {
                    (applyPanDuringScaleChange || !scaleChange) &&
                        it.hasMeaningfulDeltaFrom(currentPanX)
                }
            else ->
                target.viewportOffsetX
                    .takeIf { it.isFinite() }
                    ?.takeIf { panX ->
                        !scaleChange &&
                            remoteHorizontalChange &&
                            panX.hasMeaningfulDeltaFrom(currentPanX)
                    }
        }
    val targetPanY =
        normalizedTargetPanY?.takeIf {
            (applyPanDuringScaleChange || !scaleChange) &&
                it.hasMeaningfulDeltaFrom(currentPanY)
        }
    return BroadcastViewportCommand(
        page = target.page.coerceAtLeast(0),
        pageOffsetPx = pageOffsetPx,
        shouldScroll = !hasResolvableCenterY && normalizedTargetPanY == null && (verticalChange || scaleChange),
        targetScalePercent =
            targetScalePercent?.takeIf { it != currentScalePercent },
        targetPanX = targetPanX,
        targetPanY = targetPanY,
    )
}

private fun NetworkMessage.ProjectionFrame.remoteHorizontalChangeFrom(previousFrame: NetworkMessage.ProjectionFrame?): Boolean? {
    val previous = previousFrame?.takeIf { it.documentId == documentId }
    if (previous == null) return null
    val currentCenter = viewportCenterX
    val previousCenter = previous.viewportCenterX
    if (currentCenter != null && previousCenter != null) {
        return abs(currentCenter - previousCenter) > VIEWPORT_CENTER_X_EPSILON
    }
    return viewportOffsetX.hasMeaningfulDeltaFrom(previous.viewportOffsetX)
}

internal fun viewportCenterX(
    panX: Float,
    viewportWidthPx: Float,
    rowWidthPx: Float,
    zoom: Float,
): Float? {
    if (!panX.isFinite() || viewportWidthPx <= 0f || rowWidthPx <= 0f || !zoom.isFinite() || zoom <= 0f) {
        return null
    }
    return ((viewportWidthPx / 2f - panX) / (rowWidthPx * zoom)).coerceIn(0f, 1f)
}

internal fun viewportCenterY(
    panY: Float,
    viewportHeightPx: Float,
    pageTopsPx: FloatArray,
    pageHeightsPx: FloatArray,
    zoom: Float,
): Float? {
    if (!panY.isFinite() || viewportHeightPx <= 0f || !zoom.isFinite() || zoom <= 0f) {
        return null
    }
    val docY = ((viewportHeightPx / 2f - panY) / zoom).coerceAtLeast(0f)
    val row = viewportRowForDocY(pageTopsPx, pageHeightsPx, docY) ?: return null
    val normalizedY = ((docY - row.topPx) / row.heightPx).coerceIn(0f, 1f)
    return row.startIndex + normalizedY
}

private fun panXForViewportCenter(
    centerX: Float,
    viewportWidthPx: Float,
    rowWidthPx: Float,
    scale: Float,
): Float? {
    if (!centerX.isFinite() || viewportWidthPx <= 0f || rowWidthPx <= 0f || !scale.isFinite() || scale <= 0f) {
        return null
    }
    return viewportWidthPx / 2f - centerX.coerceIn(0f, 1f) * rowWidthPx * scale
}

private fun panYForViewportCenter(
    centerY: Float,
    viewportHeightPx: Float,
    pageTopsPx: FloatArray,
    pageHeightsPx: FloatArray,
    scale: Float,
): Float? {
    if (pageTopsPx.isEmpty()) return null
    if (!centerY.isFinite() || centerY < 0f || viewportHeightPx <= 0f || !scale.isFinite() || scale <= 0f) {
        return null
    }
    val pageIndex = centerY.toInt().coerceIn(0, pageTopsPx.lastIndex)
    val row = viewportRowForPageIndex(pageTopsPx, pageHeightsPx, pageIndex) ?: return null
    val normalizedY = (centerY - pageIndex).coerceIn(0f, 1f)
    val docY = row.topPx + normalizedY * row.heightPx
    return viewportHeightPx / 2f - docY * scale
}

private fun Float.hasMeaningfulDeltaFrom(other: Float): Boolean = !other.isFinite() || this - other > 0.5f || other - this > 0.5f

private fun viewportRowForDocY(
    pageTopsPx: FloatArray,
    pageHeightsPx: FloatArray,
    docY: Float,
): VerticalViewportRow? {
    if (pageTopsPx.isEmpty()) return null
    var lo = 0
    var hi = pageTopsPx.lastIndex
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (pageTopsPx[mid] <= docY) {
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return viewportRowForPageIndex(pageTopsPx, pageHeightsPx, hi.coerceIn(0, pageTopsPx.lastIndex))
}

private fun viewportRowForPageIndex(
    pageTopsPx: FloatArray,
    pageHeightsPx: FloatArray,
    pageIndex: Int,
): VerticalViewportRow? {
    val top = pageTopsPx.getOrNull(pageIndex) ?: return null
    var start = pageIndex
    while (start > 0 && pageTopsPx[start - 1] == top) {
        start--
    }
    var end = pageIndex
    while (end < pageTopsPx.lastIndex && pageTopsPx[end + 1] == top) {
        end++
    }
    var height = 0f
    for (index in start..end) {
        height = maxOf(height, pageHeightsPx.getOrNull(index) ?: 0f)
    }
    if (height <= 0f) return null
    return VerticalViewportRow(
        startIndex = start,
        topPx = top,
        heightPx = height,
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
