package ru.kyamshanov.notepen.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import ru.kyamshanov.notepen.resolveDocumentDisplayName

/** Hard cap on simultaneously open panels. */
const val MAX_PANELS: Int = 4

/**
 * Creates and remembers a [TabSession] seeded with one panel holding a single
 * tab for [initialFilePath]. [syncDocumentIdFor] resolves the sync identifier
 * for a freshly-opened file; it is captured at session creation.
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
        initialDisplayName = resolveDocumentDisplayName(initialFilePath),
    )
}

/**
 * Outcome of [TabSession.closeTab]: tells the host whether the editor still
 * has any open tab or should be dismissed entirely (e.g. pop the Decompose
 * `DetailsChild`).
 */
enum class TabCloseResult { Continue, AllClosed }

/**
 * Compose-side orchestrator for the tabs + grid split-layout feature.
 *
 * Holds:
 * - the current [WorkspaceLayout] as a Compose `mutableStateOf`, so layout
 *   transitions trigger recomposition;
 * - a registry of [PdfDocumentState] keyed by [DocumentId], so tabs keep
 *   their scroll / zoom / undo / magnifier state across switches and across
 *   layout transitions.
 *
 * Layout operations are pure transformations on [WorkspaceLayout] plus side
 * effects on the registry (creating a fresh [PdfDocumentState] when opening a
 * tab, disabling the magnifier of the leaving tab when switching, dropping
 * states whose tabs were closed). Everything runs on the caller's thread —
 * typically the UI thread.
 */
class TabSession internal constructor(
    private val idGenerator: IdGenerator,
    private val fallbackNameCounter: FallbackNameCounter,
    private val syncDocumentIdFor: (String) -> String,
    initialFilePath: String,
    initialDisplayName: String?,
) {

    private val documentStatesMap: SnapshotStateMap<DocumentId, PdfDocumentState> = mutableStateMapOf()
    private var panelCounter: Long = 0L

    /** Current layout. UI observes this and re-renders on change. */
    var layout: WorkspaceLayout by mutableStateOf(
        WorkspaceLayout.single(
            Panel(
                id = nextPanelId(),
                tabs = OpenDocuments.of(initialTabInternal(initialFilePath, initialDisplayName)),
            ),
        ),
    )
        private set

    /**
     * Returns the [PdfDocumentState] for the tab identified by [tab]. Creates
     * one lazily on first access — that's how additional tabs (added by
     * [openTab] or [addPanel]) get a state without forcing the call sites to
     * be Composables.
     */
    fun stateOf(tab: DocumentTab): PdfDocumentState =
        documentStatesMap.getOrPut(tab.id) {
            PdfDocumentState.create(
                filePath = tab.filePath,
                documentId = syncDocumentIdFor(tab.filePath),
            )
        }

    /** The [PdfDocumentState] of the focused panel's active tab, or `null` while none is active. */
    val focusedActiveState: PdfDocumentState?
        get() = layout.focusedPanel.tabs.activeTab?.let { stateOf(it) }

    /**
     * Opens [filePath] in panel [panelId] as a new active tab. The new tab is
     * created with a session-unique [DocumentId] — opening the same file twice
     * yields two independent tabs (shared annotation state, independent scroll).
     */
    fun openTab(panelId: PanelId, filePath: String, displayName: String?): DocumentId {
        val tab = createTab(filePath, displayName)
        val existingState = documentStatesMap.values.firstOrNull { it.filePath == filePath }
        val next = requireNotNull(
            layout.withPanelTabs(panelId) { it.addTab(tab) },
        ) { "addTab cannot produce empty workspace" }
        layout = next
        documentStatesMap[tab.id] = stateForNewTab(filePath, existingState)
        return tab.id
    }

    /**
     * Closes the tab [id] in panel [panelId]. When that was the panel's last
     * tab the panel is removed from the grid; when it was the last panel's
     * last tab returns [TabCloseResult.AllClosed] — the host pops the editor.
     */
    fun closeTab(panelId: PanelId, id: DocumentId): TabCloseResult {
        val newLayout = layout.withPanelTabs(panelId) { it.closeTab(id) }
        return if (newLayout == null) {
            documentStatesMap.clear()
            TabCloseResult.AllClosed
        } else {
            documentStatesMap.keys.retainAll(collectTabIds(newLayout))
            layout = newLayout
            TabCloseResult.Continue
        }
    }

    /**
     * Activates the tab [id] in panel [panelId]. If a different tab was active
     * there, its magnifier is closed first — the magnifier is panel-scoped and
     * shouldn't follow tab switches.
     */
    fun setActiveTab(panelId: PanelId, id: DocumentId) {
        val previouslyActive = layout.panelOf(panelId)?.tabs?.activeId
        if (previouslyActive == id) return
        if (previouslyActive != null) {
            documentStatesMap[previouslyActive]?.magnifierState?.disable()
        }
        val next = requireNotNull(
            layout.withPanelTabs(panelId) { it.setActive(id) },
        ) { "setActive cannot produce empty workspace" }
        layout = next
    }

    /** Marks panel [panelId] as the focused one (toolbar target). */
    fun focusPanel(panelId: PanelId) {
        layout = layout.focusPanel(panelId)
    }

    /** Templates that would host exactly one more panel than is open now (empty at [MAX_PANELS]). */
    fun availableTemplatesForAdd(): List<LayoutTemplate> {
        val target = layout.panels.size + 1
        if (target > MAX_PANELS) return emptyList()
        return LayoutTemplate.entries.filter { it.capacity == target }
    }

    /**
     * Moves tab [tabId] out of panel [fromPanelId] into a brand-new panel
     * under [template]. The tab keeps its [PdfDocumentState] (scroll / zoom /
     * ink), so this is a move, not a copy. No-op when [template] is not in
     * [availableTemplatesForAdd] or when [fromPanelId] holds only this tab
     * (moving it would leave an empty panel).
     */
    fun moveTabToNewPanel(template: LayoutTemplate, fromPanelId: PanelId, tabId: DocumentId) {
        if (template !in availableTemplatesForAdd()) return
        val sourcePanel = layout.panelOf(fromPanelId) ?: return
        if (sourcePanel.tabs.tabs.size <= 1) return
        val tab = sourcePanel.tabs.tabs.firstOrNull { it.id == tabId } ?: return
        // Remove from the source panel WITHOUT touching the registry — the tab's
        // state must survive the move.
        val afterRemoval = layout.withPanelTabs(fromPanelId) { it.closeTab(tabId) } ?: return
        // Keep focus on the source panel — the user is reorganising tabs, not switching
        // their working context to the newly-created panel.
        layout = afterRemoval
            .addPanel(template, Panel(id = nextPanelId(), tabs = OpenDocuments.of(tab)))
            .focusPanel(fromPanelId)
    }

    /** Updates divider [index] to [value] (clamped). See [WorkspaceLayout.setRatio]. */
    fun setRatio(index: Int, value: Float) {
        layout = layout.setRatio(index, value)
    }

    /**
     * Rebuilds the whole workspace from [snapshot], replacing the initial
     * single-panel seed. Used to restore the full split after the editor is
     * recreated (e.g. on return from the library). Fresh [PanelId]s /
     * [DocumentId]s are generated; tabs that point at the same file share a
     * [PdfDocumentState] (shared annotations, independent scroll), mirroring
     * [openTab]. No-op when [snapshot] has no panels.
     */
    fun restore(snapshot: WorkspaceSnapshot) {
        val panelSnapshots = snapshot.panels.filter { it.tabs.isNotEmpty() }
        if (panelSnapshots.isEmpty()) return
        documentStatesMap.clear()
        val stateByPath = mutableMapOf<String, PdfDocumentState>()
        val panels = panelSnapshots.map { ps ->
            val tabs = ps.tabs.map { ts ->
                val tab = DocumentTab(id = idGenerator.next(), filePath = ts.filePath, displayName = ts.displayName)
                val state = stateForNewTab(ts.filePath, stateByPath[ts.filePath])
                documentStatesMap[tab.id] = state
                stateByPath.getOrPut(ts.filePath) { state }
                tab
            }
            val activeIndex = ps.activeTabIndex.coerceIn(0, tabs.lastIndex)
            Panel(id = nextPanelId(), tabs = OpenDocuments(tabs = tabs, activeId = tabs[activeIndex].id))
        }
        val parsed = LayoutTemplate.entries.firstOrNull { it.name == snapshot.template }
        val template = if (parsed?.capacity == panels.size) parsed else WorkspaceLayout.templateForCount(panels.size)
        val focusIndex = snapshot.focusedPanelIndex.coerceIn(0, panels.lastIndex)
        val ratios = if (snapshot.ratios.size == WorkspaceLayout.defaultRatios(template).size) {
            snapshot.ratios
        } else {
            WorkspaceLayout.defaultRatios(template)
        }
        layout = WorkspaceLayout(
            panels = panels,
            template = template,
            focusedPanelId = panels[focusIndex].id,
            ratios = ratios,
        )
    }

    private fun stateForNewTab(filePath: String, existingState: PdfDocumentState?): PdfDocumentState =
        if (existingState != null) {
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

    private fun createTab(filePath: String, displayName: String?): DocumentTab {
        val name = if (displayName.isNullOrBlank()) fallbackNameCounter.next() else displayName
        return DocumentTab(id = idGenerator.next(), filePath = filePath, displayName = name)
    }

    private fun nextPanelId(): PanelId {
        panelCounter += 1L
        return PanelId(panelCounter)
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
     * Closes the [ru.kyamshanov.notepen.pdf.domain.model.PdfDocument] of every
     * tracked tab. Intended for the `onDispose` of a `DisposableEffect` keyed
     * on this session.
     */
    fun disposeAll() {
        documentStatesMap.values.forEach { it.closeDocument() }
    }

    private fun collectTabIds(layout: WorkspaceLayout): Set<DocumentId> =
        layout.panels.flatMapTo(mutableSetOf()) { panel -> panel.tabs.tabs.map { it.id } }
}
