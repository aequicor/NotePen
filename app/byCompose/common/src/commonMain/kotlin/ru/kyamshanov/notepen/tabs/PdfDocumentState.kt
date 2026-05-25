package ru.kyamshanov.notepen.tabs

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import ru.kyamshanov.notepen.PdfDrawingState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.book.TocEntry
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
 * **Shared vs. per-tab state**: when two tabs open the same file [TabSession]
 * passes the same [sharedDrawingStates], [sharedFavoritePageIndices],
 * [sharedUndoStack], [sharedRedoStack] and [sharedAnnotationsLoaded]
 * instances to both [PdfDocumentState] objects. Because these are Compose
 * snapshot-state containers, a stroke drawn on Tab A is immediately
 * observable on Tab B without any extra plumbing. [pdfViewerState] and
 * [magnifierState] remain per-tab so scroll / zoom positions stay
 * independent.
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
    /** Single source of truth for scroll / zoom / page position. Always per-tab. */
    val pdfViewerState: PdfViewerState,
    /**
     * Ink and per-page drawing state, keyed by page index. Shared across
     * all tabs that open the same file so strokes are visible everywhere.
     */
    sharedDrawingStates: SnapshotStateMap<Int, PdfDrawingState> = mutableStateMapOf(),
    /**
     * Page indices marked as favourites. Shared across same-file tabs.
     */
    sharedFavoritePageIndices: SnapshotStateList<Int> = mutableStateListOf(),
    /**
     * Undo stack shared across same-file tabs — undoing on Tab A undoes
     * strokes regardless of which tab produced them.
     */
    sharedUndoStack: ArrayDeque<UndoEntry> = ArrayDeque(),
    /** Redo counterpart of [sharedUndoStack]. Shared across same-file tabs. */
    sharedRedoStack: ArrayDeque<UndoEntry> = ArrayDeque(),
    /**
     * Whether the persisted annotation bundle has been merged into the shared
     * state. Shared so the second tab does not redundantly reload from disk.
     */
    sharedAnnotationsLoaded: MutableState<Boolean> = mutableStateOf(false),
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

    private val outlineState = mutableStateOf<List<TocEntry>>(emptyList())

    /**
     * Оглавление документа (главы EPUB/FB2), загружаемое асинхронно через
     * [ru.kyamshanov.notepen.book.DocumentOutlineProvider] после открытия. Для
     * обычных PDF и комиксов остаётся пустым. Наблюдаемое — обновление перерисует
     * сайдбар оглавления.
     */
    var outline: List<TocEntry>
        get() = outlineState.value
        set(value) {
            outlineState.value = value
        }

    /** Ink and per-page state, keyed by page index. */
    val drawingStates: SnapshotStateMap<Int, PdfDrawingState> = sharedDrawingStates

    /** Page indices marked as favourites; persisted to the annotation bundle. */
    val favoritePageIndices: SnapshotStateList<Int> = sharedFavoritePageIndices

    /**
     * Undo stack: each entry is a snapshot of strokes on a specific page
     * taken just before a gesture that mutates them.
     */
    val undoStack: ArrayDeque<UndoEntry> = sharedUndoStack

    /** Redo counterpart of [undoStack]. */
    val redoStack: ArrayDeque<UndoEntry> = sharedRedoStack

    /** Magnifier overlay state. One instance per tab — always independent. */
    val magnifierState: MagnifierState = MagnifierState()

    /**
     * Включён ли режим чтения (reflow) для этого таба. Per-tab (как
     * [pdfViewerState]) и персистится в сайдкар вида документа, поэтому
     * восстанавливается при повторном открытии.
     */
    var readingMode: Boolean by mutableStateOf(false)

    private val annotationsLoadedState: MutableState<Boolean> = sharedAnnotationsLoaded

    /**
     * `true` once the persisted annotation bundle (strokes, scroll/zoom
     * snapshot, favourites) has been merged into this tab. Shared across
     * same-file tabs so only the first tab to activate actually reads from
     * the repository — subsequent tabs find the state already populated.
     */
    var annotationsLoaded: Boolean
        get() = annotationsLoadedState.value
        set(value) {
            annotationsLoadedState.value = value
        }

    /**
     * When `true`, the annotation-restore step skips the saved page / scroll
     * position so the tab opens at page 0. Set by [TabSession] for tabs that
     * open a file already open in another tab.
     */
    var skipPageRestore: Boolean = false

    /**
     * `true` while [pdfDocument] is being loaded. Guards against two
     * coroutines (the active-tab effect and the background preloader) both
     * calling [ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader.load]
     * for the same tab simultaneously. All reads and writes happen on the
     * main dispatcher so no atomics are needed.
     */
    var isPdfLoading: Boolean = false
        internal set

    /**
     * Pushes the current snapshot of [pageIndex] onto [undoStack] and
     * clears [redoStack]. Called at the start of each stroke / erase
     * gesture (mirrors the previous `onGestureStart` body in
     * `DetailsContent`).
     */
    fun pushUndoSnapshot(
        pageIndex: Int,
        snapshot: List<DrawingPath>,
    ) {
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
         * Creates a [PdfDocumentState] with fresh annotation state. Used by
         * [TabSession] when opening the first tab for a file.
         */
        internal fun create(
            filePath: String,
            documentId: String,
        ): PdfDocumentState =
            PdfDocumentState(
                filePath = filePath,
                documentId = documentId,
                pdfViewerState = createPdfViewerState(),
            )

        /**
         * Creates a [PdfDocumentState] that shares annotation data with [from].
         * Used by [TabSession] when opening a second tab for the same file:
         * strokes, undo/redo and favourites are shared so edits appear in both
         * tabs simultaneously. [pdfViewerState] is fresh so scroll / zoom are
         * independent. [skipPageRestore] is set so the new tab starts at page 0
         * rather than the saved position (which the primary tab already occupies).
         */
        internal fun createSharing(
            filePath: String,
            documentId: String,
            from: PdfDocumentState,
        ): PdfDocumentState =
            PdfDocumentState(
                filePath = filePath,
                documentId = documentId,
                pdfViewerState = createPdfViewerState(),
                sharedDrawingStates = from.drawingStates,
                sharedFavoritePageIndices = from.favoritePageIndices,
                sharedUndoStack = from.undoStack,
                sharedRedoStack = from.redoStack,
                sharedAnnotationsLoaded = from.annotationsLoadedState,
            ).also { it.skipPageRestore = true }
    }
}
