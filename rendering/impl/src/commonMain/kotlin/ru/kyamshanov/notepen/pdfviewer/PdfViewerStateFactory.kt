package ru.kyamshanov.notepen.pdfviewer

/**
 * Non-composable factory for [PdfViewerState]. Use
 * [rememberPdfViewerState] inside Composables; this overload is for
 * eager state holders that build [PdfViewerState] outside any
 * composition (e.g. the per-tab registry in
 * [ru.kyamshanov.notepen.tabs.TabSession]).
 *
 * The returned instance is a plain object — Compose's
 * [androidx.compose.runtime.saveable.rememberSaveable] integration is
 * intentionally *not* applied here, since the surrounding holder
 * decides on its own persistence strategy.
 */
expect fun createPdfViewerState(): PdfViewerState
