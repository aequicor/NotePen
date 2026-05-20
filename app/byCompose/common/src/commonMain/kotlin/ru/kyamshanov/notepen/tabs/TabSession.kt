package ru.kyamshanov.notepen.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Derives the human-readable label for a tab from its [filePath].
 * Strips both POSIX and Windows path separators so the tab shows the
 * basename regardless of the platform that produced the path. Returns
 * `null` when no usable basename can be extracted — the caller falls
 * back to [FallbackNameCounter].
 */
fun displayNameForFilePath(filePath: String): String? {
    val basename = filePath
        .substringAfterLast('/')
        .substringAfterLast('\\')
    return basename.ifBlank { null }
}

/**
 * Creates and remembers a [TabSession] seeded with one initial tab for
 * [initialFilePath]. [syncDocumentIdFor] resolves the sync identifier
 * for a freshly-opened file (typically wraps
 * [ru.kyamshanov.notepen.sync.domain.documentIdFromFilePath] plus the
 * remote-cached registry lookup); it is captured at session creation.
 */
@Composable
fun rememberTabSession(
    initialFilePath: String,
    syncDocumentIdFor: (String) -> String,
): TabSession = remember(initialFilePath, syncDocumentIdFor) {
    TabSession(
        idGenerator = SequentialIdGenerator(),
        fallbackNameCounter = FallbackNameCounter(),
        syncDocumentIdFor = syncDocumentIdFor,
        initialFilePath = initialFilePath,
        initialDisplayName = displayNameForFilePath(initialFilePath),
    )
}

/**
 * Outcome of [TabSession.closeTab]: tells the host whether the editor
 * still has any open tab or should be dismissed entirely (e.g. pop the
 * Decompose `DetailsChild`).
 */
enum class TabCloseResult { Continue, AllClosed }

/**
 * Compose-side orchestrator for the tabs + split-layout feature.
 *
 * Holds:
 * - the current [PanelLayout] as a Compose `mutableStateOf`, so layout
 *   transitions trigger recomposition;
 * - a registry of [PdfDocumentState] keyed by [DocumentId], so tabs
 *   keep their scroll / zoom / undo / magnifier state across switches
 *   and across layout transitions.
 *
 * Operations are pure transformations on [PanelLayout] (see
 * [transformTabs] / [setRatio] / [openInSplit]) plus side effects on
 * the registry (creating a fresh [PdfDocumentState] when opening a
 * tab, disabling the magnifier of the leaving tab when switching,
 * dropping states whose tabs were closed). Everything runs on the
 * caller's thread — typically the UI thread.
 *
 * Commit 2 only exercises [PanelSide.PRIMARY]; the split-aware
 * branches live behind the same API but are unreachable until the
 * UI in commit 3 issues `openInSplit` events.
 */
class TabSession internal constructor(
    private val idGenerator: IdGenerator,
    private val fallbackNameCounter: FallbackNameCounter,
    private val syncDocumentIdFor: (String) -> String,
    initialFilePath: String,
    initialDisplayName: String?,
) {

    private val documentStatesMap: SnapshotStateMap<DocumentId, PdfDocumentState> = mutableStateMapOf()

    /** Current layout. UI observes this and re-renders on change. */
    var layout: PanelLayout by mutableStateOf(
        PanelLayout.Single(OpenDocuments.of(initialTabInternal(initialFilePath, initialDisplayName))),
    )
        private set

    /**
     * Returns the [PdfDocumentState] for the tab identified by [id].
     * Creates one lazily on first access — that's how additional tabs
     * (added by [openTab] or [openInSplit]) get a state without
     * forcing the call sites to be Composables.
     */
    fun stateOf(tab: DocumentTab): PdfDocumentState =
        documentStatesMap.getOrPut(tab.id) {
            PdfDocumentState.create(
                filePath = tab.filePath,
                documentId = syncDocumentIdFor(tab.filePath),
            )
        }

    /**
     * Opens [filePath] in the tab bar of [side] as a new active tab.
     * The new tab is created with a session-unique [DocumentId] —
     * opening the same file twice yields two independent tabs.
     */
    fun openTab(side: PanelSide, filePath: String, displayName: String?): DocumentId {
        val tab = createTab(filePath, displayName)
        // Find a pre-existing state for the same file BEFORE adding the new tab.
        val existingState = documentStatesMap.values.firstOrNull { it.filePath == filePath }
        val next = requireNotNull(
            layout.transformTabs(side) { it.addTab(tab) },
        ) { "addTab cannot produce empty OpenDocuments" }
        layout = next
        // Pre-populate the map so stateOf() finds it on first read.
        // When the same file is already open, share annotation state (strokes,
        // undo/redo, favourites) so edits appear in both tabs immediately.
        // Scroll position is always independent (createSharing gives a fresh PdfViewerState).
        documentStatesMap[tab.id] = if (existingState != null) {
            PdfDocumentState.createSharing(
                filePath = filePath,
                documentId = syncDocumentIdFor(filePath),
                from = existingState,
            )
        } else {
            PdfDocumentState.create(
                filePath = filePath,
                documentId = syncDocumentIdFor(filePath),
            )
        }
        return tab.id
    }

    /**
     * Closes the tab [id] in [side]. When [side] held the last tab and
     * the layout was [PanelLayout.Single], returns
     * [TabCloseResult.AllClosed] — the host should dismiss the editor.
     * When [side] held the last tab in a [PanelLayout.Split], the
     * surviving side collapses to a [PanelLayout.Single] and
     * [TabCloseResult.Continue] is returned.
     */
    fun closeTab(side: PanelSide, id: DocumentId): TabCloseResult {
        val newLayout = layout.transformTabs(side) { it.closeTab(id) }
        return if (newLayout == null) {
            // Whole workspace empty. Drop every state and signal pop.
            documentStatesMap.clear()
            layout = PanelLayout.Single(OpenDocuments.Empty)
            TabCloseResult.AllClosed
        } else {
            val kept = collectTabIds(newLayout)
            documentStatesMap.keys.retainAll(kept)
            layout = newLayout
            TabCloseResult.Continue
        }
    }

    /**
     * Activates the tab [id] in [side]. If a different tab was active,
     * its magnifier is closed first — per the spec, the magnifier is
     * panel-scoped and shouldn't follow tab switches.
     */
    fun setActiveTab(side: PanelSide, id: DocumentId) {
        val previouslyActive = layout.tabsOf(side)?.activeId
        if (previouslyActive == id) return
        if (previouslyActive != null) {
            documentStatesMap[previouslyActive]?.magnifierState?.disable()
        }
        val next = requireNotNull(
            layout.transformTabs(side) { it.setActive(id) },
        ) { "setActive cannot produce empty OpenDocuments" }
        layout = next
    }

    /**
     * Splits the current layout: the existing tabs stay on
     * [PanelSide.PRIMARY]; a new single-tab [OpenDocuments] containing
     * [filePath] becomes [PanelSide.SECONDARY]. Throws when the layout
     * is already split — the spec forbids deeper than one level.
     */
    fun openInSplit(
        orientation: PanelOrientation,
        filePath: String,
        displayName: String?,
    ): DocumentId {
        val tab = createTab(filePath, displayName)
        layout = layout.openInSplit(
            orientation = orientation,
            secondaryTabs = OpenDocuments.of(tab),
        )
        stateOf(tab)
        return tab.id
    }

    /** Updates the splitter ratio (clamped to [PanelLayout.MIN_RATIO]..[PanelLayout.MAX_RATIO]). */
    fun setSplitRatio(ratio: Float) {
        layout = layout.setRatio(ratio)
    }

    private fun createTab(filePath: String, displayName: String?): DocumentTab {
        val name = if (displayName.isNullOrBlank()) fallbackNameCounter.next() else displayName
        return DocumentTab(id = idGenerator.next(), filePath = filePath, displayName = name)
    }

    private fun initialTabInternal(filePath: String, displayName: String?): DocumentTab {
        val tab = createTab(filePath, displayName)
        documentStatesMap[tab.id] = PdfDocumentState.create(
            filePath = tab.filePath,
            documentId = syncDocumentIdFor(tab.filePath),
        )
        return tab
    }

    /**
     * Closes the [PdfDocument] of every tracked tab. Intended for the
     * `onDispose` of a [androidx.compose.runtime.DisposableEffect] keyed
     * on this session: when the editor leaves composition no tab is
     * left holding an open file handle.
     */
    fun disposeAll() {
        documentStatesMap.values.forEach { it.closeDocument() }
    }

    private fun collectTabIds(layout: PanelLayout): Set<DocumentId> = when (layout) {
        is PanelLayout.Single -> layout.tabs.tabs.map { it.id }.toSet()
        is PanelLayout.Split -> buildSet {
            layout.left.tabs.tabs.forEach { add(it.id) }
            layout.right.tabs.tabs.forEach { add(it.id) }
        }
    }
}
