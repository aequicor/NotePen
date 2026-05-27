package ru.kyamshanov.notepen.tabs

/**
 * Session-unique identifier of an open tab. Two tabs that show the same
 * file have *different* [DocumentId]s — that's what lets the user keep
 * the same PDF open on two pages independently.
 */
@JvmInline
value class DocumentId(
    val value: Long,
)

/**
 * Metadata for one tab. Visual state and per-document Compose state
 * live in [PdfDocumentState] keyed by [id]; this class is intentionally
 * Compose-free.
 *
 * @property id session-unique tab identifier
 * @property filePath path / URI passed to the PDF loader
 * @property displayName label shown on the tab chip
 */
data class DocumentTab(
    val id: DocumentId,
    val filePath: String,
    val displayName: String,
)

/**
 * Immutable list of open tabs plus the currently active tab id.
 * Mutation methods return a new instance — the mutable container
 * (`mutableStateOf<PanelLayout>`) lives one level up, in
 * [TabSession].
 *
 * Invariants:
 * - if [tabs] is non-empty, [activeId] points at some entry in [tabs];
 * - if [tabs] is empty, [activeId] is `null`.
 */
data class OpenDocuments(
    val tabs: List<DocumentTab> = emptyList(),
    val activeId: DocumentId? = null,
) {
    init {
        require(activeId == null || tabs.any { it.id == activeId }) {
            "activeId $activeId not present in tabs"
        }
        require(tabs.isNotEmpty() || activeId == null) {
            "activeId must be null when tabs is empty"
        }
    }

    /** The currently-active tab, or `null` if there are no tabs. */
    val activeTab: DocumentTab?
        get() = tabs.firstOrNull { it.id == activeId }

    /** `true` when no tabs are open. */
    val isEmpty: Boolean
        get() = tabs.isEmpty()

    /**
     * Returns a copy with [tab] appended. The new tab becomes active
     * when [makeActive] is `true` (default), otherwise the active tab
     * is unchanged.
     */
    fun addTab(
        tab: DocumentTab,
        makeActive: Boolean = true,
    ): OpenDocuments {
        require(tabs.none { it.id == tab.id }) { "duplicate tab id ${tab.id}" }
        val newTabs = tabs + tab
        val newActive = if (makeActive) tab.id else activeId ?: tab.id
        return copy(tabs = newTabs, activeId = newActive)
    }

    /**
     * Returns a copy with the tab identified by [id] removed. If the
     * removed tab was active, the new active tab is the one that took
     * the closed tab's position (or the previous tab if the closed one
     * was last). Returns `null` when the result would be empty —
     * callers use that to decide whether the panel should collapse.
     */
    fun closeTab(id: DocumentId): OpenDocuments? {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx < 0) return this
        val newTabs = tabs.toMutableList().apply { removeAt(idx) }
        if (newTabs.isEmpty()) return null
        val newActive =
            when {
                activeId != id -> activeId
                idx < newTabs.size -> newTabs[idx].id
                else -> newTabs.last().id
            }
        return copy(tabs = newTabs, activeId = newActive)
    }

    /** Returns a copy whose [activeId] is [id]. Throws if [id] is not present. */
    fun setActive(id: DocumentId): OpenDocuments {
        require(tabs.any { it.id == id }) { "activeId $id not present in tabs" }
        return copy(activeId = id)
    }

    companion object {
        /** Empty state — no tabs, no active id. */
        val Empty: OpenDocuments = OpenDocuments()

        /** Convenience: a single-tab state seeded with [tab]. */
        fun of(tab: DocumentTab): OpenDocuments = OpenDocuments(tabs = listOf(tab), activeId = tab.id)
    }
}
