package ru.kyamshanov.notepen.session

import ru.kyamshanov.notepen.tabs.TabSession
import ru.kyamshanov.notepen.tabs.TabViewState
import ru.kyamshanov.notepen.tabs.toSnapshot

/**
 * Captures the live workspace ([TabSession.layout] plus each open tab's view
 * position) into a [SessionData] suitable for autosave or a named session.
 *
 * Each tab's view state is read straight from its `PdfDocumentState`: a tab that
 * still carries a `pendingViewOverride` (restored but not yet composed) reports
 * the restored position rather than its uninitialised live viewer — so a
 * background autosave can't clobber a not-yet-applied restore with defaults.
 *
 * Reads only Compose snapshot state, so a `snapshotFlow { captureSession() }`
 * re-emits whenever the layout or any tab's scroll / zoom changes.
 */
fun TabSession.captureSession(): SessionData {
    val currentLayout = layout
    val viewStates =
        currentLayout.panels.map { panel ->
            panel.tabs.tabs.map { tab ->
                val state = stateOf(tab)
                state.pendingViewOverride
                    ?: TabViewState(
                        scalePercent = state.pdfViewerState.scalePercent,
                        pageIndex = state.pdfViewerState.firstVisiblePageIndex,
                        pageOffsetPx = state.pdfViewerState.firstVisiblePageOffsetPx,
                    )
            }
        }
    return SessionData(layout = currentLayout.toSnapshot(), tabViewStates = viewStates)
}

/**
 * Restores [data] into this session: rebuilds the panel/tab split via
 * [TabSession.restore], then seeds each tab's `pendingViewOverride` so it reopens
 * at its saved scroll / zoom / page. View states are paired with tabs by position
 * (`tabViewStates[panelIndex][tabIndex]`); a missing entry leaves the tab to fall
 * back to its per-file sidecar position.
 */
fun TabSession.restoreSession(data: SessionData) {
    restore(data.layout)
    layout.panels.forEachIndexed { panelIndex, panel ->
        val panelViews = data.tabViewStates.getOrNull(panelIndex) ?: return@forEachIndexed
        panel.tabs.tabs.forEachIndexed { tabIndex, tab ->
            panelViews.getOrNull(tabIndex)?.let { view ->
                stateOf(tab).pendingViewOverride = view
            }
        }
    }
}
