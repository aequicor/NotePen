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
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
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
    /**
     * Sticky-marker highlights keyed by page index. Shared across same-file tabs
     * like [sharedDrawingStates] so a highlight made in one tab shows in the other.
     */
    sharedHighlights: SnapshotStateMap<Int, List<StickyHighlight>> = mutableStateMapOf(),
    /**
     * Snapshot-observable counter bumped on every [undoStack] / [redoStack]
     * mutation so Compose-derived `canUndo` / `canRedo` (the toolbar button
     * enabled flags) recompute — the deques themselves are plain (not
     * snapshot) collections. Shared across same-file tabs like the stacks.
     */
    sharedUndoVersion: MutableState<Int> = mutableStateOf(0),
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

    /** Sticky-marker highlights per page index; persisted to the annotation bundle. */
    val highlights: SnapshotStateMap<Int, List<StickyHighlight>> = sharedHighlights

    /**
     * Undo stack: each entry is a snapshot of strokes on a specific page
     * taken just before a gesture that mutates them.
     */
    val undoStack: ArrayDeque<UndoEntry> = sharedUndoStack

    /** Redo counterpart of [undoStack]. */
    val redoStack: ArrayDeque<UndoEntry> = sharedRedoStack

    private val undoVersionState: MutableState<Int> = sharedUndoVersion

    /**
     * `true` when [undo] would do something. Reading this in a composition
     * subscribes to [undoVersionState], so the undo button's `enabled` flag
     * recomputes when a gesture / undo / redo mutates the (non-observable)
     * [undoStack].
     */
    val canUndo: Boolean
        get() {
            undoVersionState.value // establish snapshot read dependency
            return undoStack.isNotEmpty()
        }

    /** Redo counterpart of [canUndo]. */
    val canRedo: Boolean
        get() {
            undoVersionState.value // establish snapshot read dependency
            return redoStack.isNotEmpty()
        }

    /** Magnifier overlay state. One instance per tab — always independent. */
    val magnifierState: MagnifierState = MagnifierState()

    /**
     * Включён ли режим чтения (reflow) для этого таба. Per-tab (как
     * [pdfViewerState]) и персистится в сайдкар вида документа, поэтому
     * восстанавливается при повторном открытии.
     */
    var readingMode: Boolean by mutableStateOf(false)

    /**
     * Видны ли настройки ридера (нижний «airbar») для этого таба. Per-tab: тап по
     * тексту скрывает/возвращает их, а слой панели гасит их ещё и при потере
     * фокуса, поэтому при переключении на другую панель airbar исчезает, а при
     * возврате восстанавливает запомненное состояние (см. `EditorPanel`).
     */
    var readerBarVisible: Boolean by mutableStateOf(true)

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
     * Set by session restore to force this tab's initial scroll / zoom / page,
     * overriding the per-file sidecar position. Consumed (cleared) by
     * `EditorPanel` on first composition — applied independently of the shared
     * annotation load, so a second tab of the same file restores its own
     * position even though the file's annotations are already loaded. `null`
     * outside a restore.
     */
    var pendingViewOverride: TabViewState? by mutableStateOf(null)

    /**
     * `true` while [pdfDocument] is being loaded. Guards against two
     * coroutines (the active-tab effect and the background preloader) both
     * calling [ru.kyamshanov.notepen.pdf.domain.port.PdfDocumentLoader.load]
     * for the same tab simultaneously. All reads and writes happen on the
     * main dispatcher so no atomics are needed.
     *
     * Snapshot-backed so the editor can drive a "preparing document" overlay
     * from it: opening an EPUB first converts it to PDF and rasterises page 0,
     * during which [pdfDocument] is still `null` — without this flag the viewer
     * would render a blank page with no feedback (Defect H).
     */
    var isPdfLoading: Boolean by mutableStateOf(false)
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
        undoStack.addLast(UndoEntry(pageIndex, snapshot, highlights[pageIndex].orEmpty()))
        redoStack.clear()
        undoVersionState.value++
    }

    /**
     * Pops the most recent [UndoEntry] off [undoStack], pushes the current
     * strokes + highlights of that page onto [redoStack], and restores the
     * snapshot (strokes via [PdfDrawingState.restoreSnapshot] and sticky-marker
     * [highlights]) so a sticky-marker swipe reverts as one step. No-op when
     * [undoStack] is empty. Touch toolbars and the Ctrl+Z key handler share this.
     */
    fun undo() {
        if (undoStack.isEmpty()) return
        val entry = undoStack.removeLast()
        val current = drawingStates[entry.pageIndex]?.currentPaths?.toList() ?: emptyList()
        val currentHighlights = highlights[entry.pageIndex].orEmpty()
        redoStack.addLast(UndoEntry(entry.pageIndex, current, currentHighlights))
        drawingStates[entry.pageIndex]?.restoreSnapshot(entry.paths)
        highlights[entry.pageIndex] = entry.highlights
        undoVersionState.value++
    }

    /**
     * Pops the most recent [UndoEntry] off [redoStack], pushes it back onto
     * [undoStack], and re-applies the redone snapshot. Inverse of [undo]; no-op
     * when [redoStack] is empty. (Mirrors the previous inline Ctrl+Shift+Z
     * handler exactly: the entry re-pushed onto [undoStack] is the redone state,
     * not a fresh pre-redo capture.)
     */
    fun redo() {
        if (redoStack.isEmpty()) return
        val entry = redoStack.removeLast()
        undoStack.addLast(UndoEntry(entry.pageIndex, entry.paths, entry.highlights))
        drawingStates[entry.pageIndex]?.restoreSnapshot(entry.paths)
        highlights[entry.pageIndex] = entry.highlights
        undoVersionState.value++
    }

    /**
     * Snapshot of one page's strokes (re-applied via [PdfDrawingState.restoreSnapshot])
     * plus its sticky-marker [highlights] — captured together so undo/redo of a
     * sticky-marker swipe (stroke removed, highlight added) reverts as one step.
     */
    data class UndoEntry(
        val pageIndex: Int,
        val paths: List<DrawingPath>,
        val highlights: List<StickyHighlight> = emptyList(),
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
                sharedHighlights = from.highlights,
                sharedUndoVersion = from.undoVersionState,
            ).also { it.skipPageRestore = true }
    }
}
