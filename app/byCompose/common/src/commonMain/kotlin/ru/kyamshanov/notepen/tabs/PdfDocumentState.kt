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
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.annotation.domain.model.PageRotation
import ru.kyamshanov.notepen.annotation.domain.model.SpreadSplit
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import ru.kyamshanov.notepen.annotation.domain.model.mergeHighlightsByPage
import ru.kyamshanov.notepen.annotation.domain.model.mergeNotesByPage
import ru.kyamshanov.notepen.annotation.domain.model.mergeRotations
import ru.kyamshanov.notepen.annotation.domain.model.mergeStrokesByPage
import ru.kyamshanov.notepen.annotation.domain.model.splitHighlightsByPage
import ru.kyamshanov.notepen.annotation.domain.model.splitNotesByPage
import ru.kyamshanov.notepen.annotation.domain.model.splitRotations
import ru.kyamshanov.notepen.annotation.domain.model.splitStrokesByPage
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
     * Текстовые заметки ([PageNote]) по индексу страницы. Shared across same-file
     * tabs exactly like [sharedHighlights] so a note made in one tab shows in the other.
     */
    sharedNotes: SnapshotStateMap<Int, List<PageNote>> = mutableStateMapOf(),
    /**
     * Пользовательский поворот страницы (четверти CW, `[0, 3]`) по индексу.
     * Shared across same-file tabs — поворот, сделанный в одной вкладке,
     * виден в другой. Отсутствие ключа эквивалентно `0`.
     */
    sharedPageRotations: SnapshotStateMap<Int, Int> = mutableStateMapOf(),
    /**
     * Включено ли разделение разворотов (FEATURE #4) — обёрнуто в [MutableState],
     * чтобы делиться между вкладками одного файла: переключение в одной вкладке
     * видно в другой (как штрихи/повороты). `false` по умолчанию.
     */
    sharedSpreadSplit: MutableState<Boolean> = mutableStateOf(false),
    /**
     * Явный выбор пользователя по книжному развороту (FEATURE #5): `null` — авто
     * (по ширине экрана), `true`/`false` — принудительно вкл/выкл. Обёрнут в
     * [MutableState] и shared между вкладками одного файла (как [sharedSpreadSplit]).
     */
    sharedSpreadViewOverride: MutableState<Boolean?> = mutableStateOf(null),
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

    private val spreadSplitState: MutableState<Boolean> = sharedSpreadSplit

    /**
     * Включено ли ручное разделение разворотов (FEATURE #4): каждая исходная
     * страница делится на левую/правую логические половины, удваивая число
     * страниц. Shared между вкладками одного файла; персистится в сайдкаре вида.
     * Наблюдаемое: смена пере-строит [pages] и дёргает релэйаут/ре-рендер.
     */
    var spreadSplit: Boolean
        get() = spreadSplitState.value
        set(value) {
            spreadSplitState.value = value
        }

    private val spreadViewOverrideState: MutableState<Boolean?> = sharedSpreadViewOverride

    /**
     * Явный выбор пользователя по книжному развороту (FEATURE #5, «Две страницы»):
     * `null` — авто (включается на широких экранах), `true`/`false` — принудительно
     * вкл/выкл независимо от ширины. Shared между вкладками одного файла;
     * персистится в сайдкаре вида. Отдельный и независимый от [spreadSplit]
     * (FEATURE #4) и [readingMode] (reflow) переключатель. Наблюдаемое.
     */
    var spreadViewOverride: Boolean?
        get() = spreadViewOverrideState.value
        set(value) {
            spreadViewOverrideState.value = value
        }

    /**
     * ЛОГИЧЕСКИЕ страницы документа — то, что видит вьювер, навигация, штрихи и
     * счётчик. При выключенном [spreadSplit] совпадают с исходными страницами
     * [pdfDocument]. При включённом — каждая исходная страница `S` даёт две
     * половины (`2S` — левая, `2S+1` — правая) с тем же [PdfPageInfo.pageIndex],
     * что и логический индекс, и aspect'ом половинной ВИЗУАЛЬНОЙ ширины.
     * [PdfPageInfo.rotation] оставляем собственным (рендерер крутит половину
     * отдельно). Делим именно ту сторону mediabox, что даёт ВИЗУАЛЬНУЮ ширину:
     * у `/Rotate 90/270` страница повёрнута, поэтому визуальная ширина = `heightPt`
     * (иначе [PdfPageInfo.aspectRatio] = `heightPt/widthPt` у повёрнутых **удвоится**
     * вместо деления → половина выйдет широкой короткой полосой). Сами `widthPt`/
     * `heightPt` влияют лишь на aspect раскладки и подбор DPI; рендерер берёт
     * исходную страницу по `sourceIndex` и применяет вырезку отдельно.
     */
    val pages by derivedStateOf {
        val source = pdfDocument?.info?.pages.orEmpty()
        if (!spreadSplit) {
            source
        } else {
            buildList(source.size * SpreadSplit.HALVES_PER_PAGE) {
                source.forEach { info ->
                    val left = SpreadSplit.leftLogical(info.pageIndex)
                    val right = SpreadSplit.rightLogical(info.pageIndex)
                    val rotated = info.rotation == ROTATE_90 || info.rotation == ROTATE_270
                    val half =
                        if (rotated) {
                            info.copy(heightPt = info.heightPt * SpreadSplit.GUTTER_X)
                        } else {
                            info.copy(widthPt = info.widthPt * SpreadSplit.GUTTER_X)
                        }
                    add(half.copy(pageIndex = left))
                    add(half.copy(pageIndex = right))
                }
            }
        }
    }

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

    /** Text notes ([PageNote]) per page index; persisted to the annotation bundle. */
    val notes: SnapshotStateMap<Int, List<PageNote>> = sharedNotes

    /**
     * Пользовательский поворот каждой страницы в четвертях CW (`[0, 3]`),
     * персистится в лёгкий сайдкар вида. Отсутствие ключа — `0` (без доворота).
     * Наблюдаемое: смена дёргает релэйаут вьюера и ре-рендер растра.
     */
    val pageRotations: SnapshotStateMap<Int, Int> = sharedPageRotations

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
     * Pushes the current snapshot of [pageIndex] (strokes plus the page's
     * sticky-marker [highlights] and text [notes]) onto [undoStack] and
     * clears [redoStack]. Called at the start of each stroke / erase
     * gesture (mirrors the previous `onGestureStart` body in
     * `DetailsContent`) and before each note create / edit / remove.
     */
    fun pushUndoSnapshot(
        pageIndex: Int,
        snapshot: List<DrawingPath>,
    ) {
        undoStack.addLast(UndoEntry(pageIndex, snapshot, highlights[pageIndex].orEmpty(), notes[pageIndex].orEmpty()))
        redoStack.clear()
        undoVersionState.value++
    }

    /**
     * Pops the most recent [UndoEntry] off [undoStack], pushes the current
     * strokes + highlights + notes of that page onto [redoStack], and restores
     * the snapshot (strokes via [PdfDrawingState.restoreSnapshot], sticky-marker
     * [highlights] and text [notes]) so a sticky-marker swipe or note mutation
     * reverts as one step. No-op when [undoStack] is empty. Touch toolbars and
     * the Ctrl+Z key handler share this.
     */
    fun undo() {
        if (undoStack.isEmpty()) return
        val entry = undoStack.removeLast()
        val current = drawingStates[entry.pageIndex]?.currentPaths?.toList() ?: emptyList()
        val currentHighlights = highlights[entry.pageIndex].orEmpty()
        val currentNotes = notes[entry.pageIndex].orEmpty()
        redoStack.addLast(UndoEntry(entry.pageIndex, current, currentHighlights, currentNotes))
        drawingStates[entry.pageIndex]?.restoreSnapshot(entry.paths)
        highlights[entry.pageIndex] = entry.highlights
        notes[entry.pageIndex] = entry.notes
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
        undoStack.addLast(UndoEntry(entry.pageIndex, entry.paths, entry.highlights, entry.notes))
        drawingStates[entry.pageIndex]?.restoreSnapshot(entry.paths)
        highlights[entry.pageIndex] = entry.highlights
        notes[entry.pageIndex] = entry.notes
        undoVersionState.value++
    }

    /**
     * Поворачивает страницу [pageIndex] на +90° по часовой стрелке кумулятивно
     * (`0 → 1 → 2 → 3 → 0` четверти). Перемещает все штрихи, липкие выделения и
     * рисуемую область ([PdfDrawingState.extent]) этой страницы в новую систему
     * координат, чтобы они остались под тем же содержимым после поворота растра
     * (см. [PageRotation.rotatePointCw] — направление совпадает с поворотом
     * растра в рендерере).
     *
     * [pageAspectBeforeRotation] — соотношение сторон страницы (ширина/высота)
     * **до** этого поворота: на четверть-обороте ширина и высота меняются
     * местами, поэтому толщина штриха (нормирована к ширине) домножается на это
     * значение, сохраняя видимую толщину.
     *
     * **Не undoable.** Поворот не кладётся в [undoStack]: это геометрическая
     * операция над всей страницей (растр + раскладка + штрихи), которую модель
     * пер-страничных снапшотов штрихов не отслеживает целиком. Отмена —
     * повторный поворот до возврата к `0`.
     */
    fun rotatePageClockwise(
        pageIndex: Int,
        pageAspectBeforeRotation: Float,
    ) {
        val drawing = drawingStates[pageIndex]
        if (drawing != null && drawing.currentPaths.isNotEmpty()) {
            val rotated = drawing.currentPaths.map { PageRotation.rotatePathCw(it, pageAspectBeforeRotation) }
            drawing.restoreSnapshot(rotated)
        }
        drawing?.let {
            val ext = it.extent.value
            if (ext != PageExtent.Pdf) {
                it.setExtent(PageRotation.rotateExtentCw(ext))
            }
        }
        highlights[pageIndex]?.let { hs ->
            if (hs.isNotEmpty()) {
                highlights[pageIndex] = hs.map { PageRotation.rotateHighlightCw(it) }
            }
        }
        notes[pageIndex]?.let { ns ->
            if (ns.isNotEmpty()) {
                notes[pageIndex] = ns.map { it.copy(rects = it.rects.map(PageRotation::rotateRectCw)) }
            }
        }
        // Снапшоты undo/redo сделаны в старой системе координат и после поворота
        // визуально некорректны — чистим, чтобы «отмена» не вернула неповёрнутые
        // штрихи поверх повёрнутого растра.
        undoStack.clear()
        redoStack.clear()
        undoVersionState.value++

        val current = pageRotations[pageIndex] ?: 0
        pageRotations[pageIndex] = PageRotation.nextQuarter(current)
    }

    /**
     * Переключает разделение разворотов (FEATURE #4). При включении каждая
     * исходная страница `S` делится на логические половины `2S`/`2S+1`; при
     * выключении половины объединяются обратно. Атомарно мигрирует ВСЁ
     * пер-страничное состояние между исходным и логическим пространством индексов:
     * штрихи ([drawingStates]), липкие выделения ([highlights]), повороты
     * ([pageRotations]) и рисуемую область ([PdfDrawingState.extent]).
     *
     * Координаты штрихов/выделений пересчитываются: при включении точка с `x<0.5`
     * исходной страницы уходит в левую половину (`x'=x*2`), `x>=0.5` — в правую
     * (`x'=(x-0.5)*2`), Y не меняется; штрих целиком относится к половине по X его
     * первой точки (штрих через корешок не дробится). При выключении —
     * обратное преобразование. См. [SpreadSplit].
     *
     * **Не undoable** (как и поворот): это геометрическая операция над всем
     * документом, которую модель пер-страничных снапшотов не отслеживает целиком.
     * Снапшоты undo/redo чистятся — они в старом пространстве индексов/координат.
     */
    fun toggleSpreadSplit() {
        val enabling = !spreadSplit

        // Снимаем текущее состояние, ПЕРЕД тем как менять, чтобы пересобрать карты.
        val strokesByPage = drawingStates.mapValues { (_, s) -> s.currentPaths.toList() }
        val highlightsByPage = highlights.toMap()
        val notesByPage = notes.toMap()
        val extentsByPage = drawingStates.mapValues { (_, s) -> s.extent.value }
        val rotationsByPage = pageRotations.toMap()

        // Штрихи.
        val newStrokes =
            if (enabling) {
                SpreadSplit.splitStrokesByPage(strokesByPage)
            } else {
                SpreadSplit.mergeStrokesByPage(strokesByPage)
            }
        drawingStates.clear()
        newStrokes.forEach { (page, paths) ->
            drawingStates.getOrPut(page) { PdfDrawingState() }.restoreSnapshot(paths)
        }

        // Липкие выделения (только X прямоугольников; Y не меняется).
        val newHighlights =
            if (enabling) {
                SpreadSplit.splitHighlightsByPage(highlightsByPage)
            } else {
                SpreadSplit.mergeHighlightsByPage(highlightsByPage)
            }
        highlights.clear()
        newHighlights.forEach { (page, hs) -> highlights[page] = hs }

        // Текстовые заметки (та же X-математика, что и у выделений; плюс смена pageIndex).
        migrateNotesForSpreadSplit(notesByPage, enabling)

        // Области рисования (extent): при включении обе половины наследуют extent
        // исходной страницы; при слиянии extent берётся из ЛЕВОЙ половины (правая
        // отбрасывается — после слияния extent у объединённой страницы один).
        // Простое корректное правило для редкого случая нестандартного extent + split.
        extentsByPage.forEach { (page, ext) ->
            if (ext == PageExtent.Pdf) return@forEach
            val targets =
                if (enabling) {
                    listOf(SpreadSplit.leftLogical(page), SpreadSplit.rightLogical(page))
                } else if (SpreadSplit.isRightHalf(page)) {
                    emptyList()
                } else {
                    listOf(SpreadSplit.sourceIndexOf(page))
                }
            targets.forEach { t -> drawingStates.getOrPut(t) { PdfDrawingState() }.setExtent(ext) }
        }

        // Повороты.
        val newRotations =
            if (enabling) {
                SpreadSplit.splitRotations(rotationsByPage)
            } else {
                SpreadSplit.mergeRotations(rotationsByPage)
            }
        pageRotations.clear()
        newRotations.forEach { (page, q) -> if (q != 0) pageRotations[page] = q }

        undoStack.clear()
        redoStack.clear()
        undoVersionState.value++

        spreadSplit = enabling
    }

    /**
     * Re-maps text notes for a spread-split toggle and re-publishes them into [notes].
     * Extracted from [toggleSpreadSplit] so the toggle's own cyclomatic complexity stays
     * within the limit; the split/merge rect math lives in [SpreadSplit.splitNotesByPage] /
     * [SpreadSplit.mergeNotesByPage] (twins of the highlight helpers).
     */
    private fun migrateNotesForSpreadSplit(
        notesByPage: Map<Int, List<PageNote>>,
        enabling: Boolean,
    ) {
        val newNotes =
            if (enabling) SpreadSplit.splitNotesByPage(notesByPage) else SpreadSplit.mergeNotesByPage(notesByPage)
        notes.clear()
        newNotes.forEach { (page, ns) -> notes[page] = ns }
    }

    /**
     * Snapshot of one page's strokes (re-applied via [PdfDrawingState.restoreSnapshot])
     * plus its sticky-marker [highlights] and text [notes] — captured together so
     * undo/redo of a sticky-marker swipe (stroke removed, highlight added) or a note
     * create/edit/remove reverts as one step.
     */
    data class UndoEntry(
        val pageIndex: Int,
        val paths: List<DrawingPath>,
        val highlights: List<StickyHighlight> = emptyList(),
        val notes: List<PageNote> = emptyList(),
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
        /** Углы собственного `/Rotate` PDF, при которых mediabox повёрнут на бок. */
        private const val ROTATE_90 = 90
        private const val ROTATE_270 = 270

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
                sharedNotes = from.notes,
                sharedPageRotations = from.pageRotations,
                sharedSpreadSplit = from.spreadSplitState,
                sharedSpreadViewOverride = from.spreadViewOverrideState,
                sharedUndoVersion = from.undoVersionState,
            ).also { it.skipPageRestore = true }
    }
}
