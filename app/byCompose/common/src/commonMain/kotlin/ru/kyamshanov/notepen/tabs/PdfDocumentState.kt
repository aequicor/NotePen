package ru.kyamshanov.notepen.tabs

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import ru.kyamshanov.notepen.PdfDrawingState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.magnifier.MagnifierState
import ru.kyamshanov.notepen.pdf.domain.model.PdfDocument
import ru.kyamshanov.notepen.pdfviewer.PdfViewerState
import ru.kyamshanov.notepen.pdfviewer.createPdfViewerState

/**
 * Per-document state holder for a single open PDF. Owns everything that
 * belongs to *one* tab: the loaded [PdfDocument], drawing state per page,
 * viewer position/zoom, undo/redo stack, magnifier state and favourite
 * pages.
 *
 * Tool settings, sync banner state, keyboard-modifier tracking and other
 * panel-level UI flags stay outside this class — they live one level up,
 * in the panel that hosts the active tab.
 *
 * The class itself is Compose-aware (uses [mutableStateOf] /
 * [mutableStateMapOf] internally) but does not require any specific
 * composition scope, so an instance can be held in a tab registry and
 * reused across tab switches.
 */
class PdfDocumentState internal constructor(
    /** Path or URI of the underlying PDF file. Identity for sync / persistence. */
    val filePath: String,
    /**
     * Sync document identifier — derived from [filePath] (and possibly the
     * remote-cached registry) by the caller. Multiple tabs that open the
     * same file share this identifier; the per-tab identity is the
     * [ru.kyamshanov.notepen.tabs.DocumentId] kept by [OpenDocuments].
     */
    val documentId: String,
    /** Single source of truth for scroll / zoom / page position. */
    val pdfViewerState: PdfViewerState,
) {

    private val pdfDocumentState = mutableStateOf<PdfDocument?>(null)

    /** Currently loaded PDF, or `null` while loading / on load failure. */
    var pdfDocument: PdfDocument?
        get() = pdfDocumentState.value
        set(value) {
            pdfDocumentState.value = value
        }

    /** Pages of the loaded [pdfDocument], or empty until it loads. */
    val pages by derivedStateOf { pdfDocument?.info?.pages.orEmpty() }

    /** Ink and per-page state, keyed by page index. */
    val drawingStates: SnapshotStateMap<Int, PdfDrawingState> = mutableStateMapOf()

    /** Page indices marked as favourites; persisted to the annotation bundle. */
    val favoritePageIndices: SnapshotStateList<Int> = mutableStateListOf()

    /**
     * Undo stack: each entry is a snapshot of strokes on a specific page
     * taken just before a gesture that mutates them. Renamed from the
     * former `globalUndoStack` — "global" only ever meant "across all
     * pages of this one document".
     */
    val undoStack: ArrayDeque<UndoEntry> = ArrayDeque()

    /** Redo counterpart of [undoStack]. */
    val redoStack: ArrayDeque<UndoEntry> = ArrayDeque()

    /** Magnifier overlay state. One instance per tab — see plan §commit 3. */
    val magnifierState: MagnifierState = MagnifierState()

    private val annotationsLoadedState = mutableStateOf(false)

    /**
     * `true` once the persisted annotation bundle (strokes, scroll/zoom
     * snapshot, favourites) has been merged into this tab. Stops the
     * `LaunchedEffect(pdfState)` reload from firing every time the user
     * switches back to the tab — without this, paths would be appended
     * cumulatively on each visit.
     */
    var annotationsLoaded: Boolean
        get() = annotationsLoadedState.value
        set(value) {
            annotationsLoadedState.value = value
        }

    /**
     * Pushes the current snapshot of [pageIndex] onto [undoStack] and
     * clears [redoStack]. Called at the start of each stroke / erase
     * gesture (mirrors the previous `onGestureStart` body in
     * `DetailsContent`).
     */
    fun pushUndoSnapshot(pageIndex: Int, snapshot: List<DrawingPath>) {
        undoStack.addLast(UndoEntry(pageIndex, snapshot))
        redoStack.clear()
    }

    /** Snapshot of one page's strokes that can be re-applied via [PdfDrawingState.restoreSnapshot]. */
    data class UndoEntry(
        val pageIndex: Int,
        val paths: List<DrawingPath>,
    )

    /**
     * Closes the underlying [PdfDocument] (if any) and clears the
     * holder. Called by [TabSession] when this tab is closed or when
     * the editor is dismissed.
     */
    fun closeDocument() {
        pdfDocument?.close()
        pdfDocument = null
    }

    companion object {
        /**
         * Creates a [PdfDocumentState] eagerly (no Composable scope
         * required). Used by [TabSession] when opening a new tab in
         * response to user input.
         */
        internal fun create(filePath: String, documentId: String): PdfDocumentState =
            PdfDocumentState(
                filePath = filePath,
                documentId = documentId,
                pdfViewerState = createPdfViewerState(),
            )
    }
}
