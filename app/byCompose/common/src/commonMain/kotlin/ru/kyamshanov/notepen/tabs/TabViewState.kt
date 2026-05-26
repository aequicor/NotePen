package ru.kyamshanov.notepen.tabs

import kotlinx.serialization.Serializable

/**
 * One open tab's view position, restored when a session is reopened or a crashed
 * workspace is recovered.
 *
 * Lives in the `tabs` package (next to [WorkspaceSnapshot]) so the per-tab state
 * holder [PdfDocumentState] can carry a restore override of this type without the
 * `tabs` package depending on `session`. Tool/annotation state is intentionally
 * **not** stored here — it rides each document's existing sidecar file, keyed by
 * file path.
 *
 * @property scalePercent zoom level as a percentage (100 = fit/native scale).
 * @property pageIndex 0-based index of the page in view.
 * @property pageOffsetPx vertical scroll offset within [pageIndex], in pixels.
 */
@Serializable
data class TabViewState(
    val scalePercent: Int = 100,
    val pageIndex: Int = 0,
    val pageOffsetPx: Int = 0,
)
