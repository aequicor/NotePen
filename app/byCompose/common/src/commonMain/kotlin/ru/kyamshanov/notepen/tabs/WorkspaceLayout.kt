package ru.kyamshanov.notepen.tabs

/**
 * Session-unique identifier of a workspace panel. Stable across layout
 * template changes — used by the unified toolbar to address the focused
 * panel and by the grid renderer to keep panel state across re-tiling.
 */
@JvmInline
value class PanelId(val value: Long)

/**
 * One panel of the workspace grid: a stack of [OpenDocuments] (browser-style
 * tabs) plus its identity. Visual / per-document state lives in
 * [PdfDocumentState] keyed by [DocumentId]; this class is Compose-free.
 */
data class Panel(
    val id: PanelId,
    val tabs: OpenDocuments,
)

/**
 * Grid arrangement template. [capacity] is the exact number of panels the
 * template hosts — a [WorkspaceLayout] always satisfies
 * `panels.size == template.capacity`.
 *
 * Slot order matches [WorkspaceLayout.panels] order:
 * - [COLUMNS_2]: left, right.
 * - [COLUMNS_3]: left, middle, right.
 * - [LEFT_PLUS_STACK]: large left, right-top, right-bottom.
 * - [GRID_2X2]: top-left, top-right, bottom-left, bottom-right.
 */
enum class LayoutTemplate(val capacity: Int) {
    /** One panel filling the whole workspace. */
    FULL(1),

    /** Two equal columns (1×2). */
    COLUMNS_2(2),

    /** Three columns (1×3). */
    COLUMNS_3(3),

    /** One large panel on the left, two stacked on the right ("2+1"). */
    LEFT_PLUS_STACK(3),

    /** Two-by-two grid. */
    GRID_2X2(4),
}

/**
 * Immutable layout of the editor workspace: an ordered list of [panels]
 * arranged by [template], the [focusedPanelId] the unified toolbar acts on,
 * and the draggable divider positions in [ratios].
 *
 * Mutation methods return a new instance — the mutable container
 * (`mutableStateOf<WorkspaceLayout>`) lives one level up, in [TabSession].
 *
 * ### [ratios] semantics (per [template], each clamped to [MIN_RATIO]..[MAX_RATIO])
 * - [LayoutTemplate.FULL]: empty.
 * - [LayoutTemplate.COLUMNS_2]: `[leftWidth]`.
 * - [LayoutTemplate.COLUMNS_3]: `[divider1, divider2]` — cumulative positions
 *   of the two vertical dividers (`0 < divider1 < divider2 < 1`); column
 *   widths are `divider1`, `divider2 - divider1`, `1 - divider2`.
 * - [LayoutTemplate.LEFT_PLUS_STACK]: `[leftWidth, rightTopHeight]`.
 * - [LayoutTemplate.GRID_2X2]: `[columnSplit, rowSplit]` (shared by both rows / columns).
 */
data class WorkspaceLayout(
    val panels: List<Panel>,
    val template: LayoutTemplate,
    val focusedPanelId: PanelId,
    val ratios: List<Float> = defaultRatios(template),
) {

    init {
        require(panels.size == template.capacity) {
            "panels.size ${panels.size} != template.capacity ${template.capacity} ($template)"
        }
        require(panels.any { it.id == focusedPanelId }) {
            "focusedPanelId $focusedPanelId not present in panels"
        }
        require(panels.map { it.id }.toSet().size == panels.size) {
            "duplicate PanelId in $panels"
        }
    }

    /** The currently focused panel. */
    val focusedPanel: Panel
        get() = panels.first { it.id == focusedPanelId }

    /** `true` when more than one panel is open (i.e. the workspace is split). */
    val isSplit: Boolean
        get() = panels.size > 1

    /** Returns the panel with [id], or `null` if absent. */
    fun panelOf(id: PanelId): Panel? = panels.firstOrNull { it.id == id }

    /**
     * Replaces the [OpenDocuments] of the panel [id] via [transform]. When
     * [transform] returns `null` the panel is removed:
     * - removing the last panel returns `null` (whole workspace empty —
     *   caller pops the editor);
     * - otherwise the template downgrades to the canonical template for the
     *   new panel count and focus moves to a surviving panel.
     */
    fun withPanelTabs(id: PanelId, transform: (OpenDocuments) -> OpenDocuments?): WorkspaceLayout? {
        val target = panelOf(id) ?: return this
        val newTabs = transform(target.tabs)
        if (newTabs != null) {
            val newPanels = panels.map { if (it.id == id) it.copy(tabs = newTabs) else it }
            return copy(panels = newPanels)
        }
        // Panel removed.
        val remaining = panels.filterNot { it.id == id }
        if (remaining.isEmpty()) return null
        val newTemplate = templateForCount(remaining.size)
        val newFocus = if (focusedPanelId == id) remaining.first().id else focusedPanelId
        return WorkspaceLayout(
            panels = remaining,
            template = newTemplate,
            focusedPanelId = newFocus,
            ratios = defaultRatios(newTemplate),
        )
    }

    /**
     * Adds [panel] under [template]. The new panel becomes focused. Requires
     * `template.capacity == panels.size + 1` — i.e. exactly one more slot than
     * currently open (see [TabSession.availableTemplatesForAdd]).
     */
    fun addPanel(template: LayoutTemplate, panel: Panel): WorkspaceLayout {
        require(template.capacity == panels.size + 1) {
            "template $template capacity ${template.capacity} != panels.size+1 ${panels.size + 1}"
        }
        return WorkspaceLayout(
            panels = panels + panel,
            template = template,
            focusedPanelId = panel.id,
            ratios = defaultRatios(template),
        )
    }

    /** Returns a copy with [id] focused (no-op if [id] is already focused or absent). */
    fun focusPanel(id: PanelId): WorkspaceLayout =
        if (id == focusedPanelId || panelOf(id) == null) this else copy(focusedPanelId = id)

    /**
     * Returns a copy with divider [index] set to [value] (clamped to
     * [MIN_RATIO]..[MAX_RATIO]). Out-of-range [index] returns the receiver.
     */
    fun setRatio(index: Int, value: Float): WorkspaceLayout {
        if (index !in ratios.indices) return this
        val clamped = value.coerceIn(MIN_RATIO, MAX_RATIO)
        return copy(ratios = ratios.toMutableList().apply { this[index] = clamped })
    }

    companion object {
        /** A divider never lets a panel shrink below 20% of the cross axis. */
        const val MIN_RATIO: Float = 0.2f

        /** A divider never lets a panel exceed 80% of the cross axis. */
        const val MAX_RATIO: Float = 0.8f

        /** Single-panel workspace seeded with [panel]. */
        fun single(panel: Panel): WorkspaceLayout =
            WorkspaceLayout(
                panels = listOf(panel),
                template = LayoutTemplate.FULL,
                focusedPanelId = panel.id,
            )

        /** Canonical template for [count] panels (used when removing a panel). */
        fun templateForCount(count: Int): LayoutTemplate = when (count) {
            1 -> LayoutTemplate.FULL
            2 -> LayoutTemplate.COLUMNS_2
            3 -> LayoutTemplate.COLUMNS_3
            4 -> LayoutTemplate.GRID_2X2
            else -> error("unsupported panel count $count")
        }

        /** Default divider positions for [template]. See [WorkspaceLayout] for semantics. */
        fun defaultRatios(template: LayoutTemplate): List<Float> = when (template) {
            LayoutTemplate.FULL -> emptyList()
            LayoutTemplate.COLUMNS_2 -> listOf(0.5f)
            LayoutTemplate.COLUMNS_3 -> listOf(1f / 3f, 2f / 3f)
            LayoutTemplate.LEFT_PLUS_STACK -> listOf(0.5f, 0.5f)
            LayoutTemplate.GRID_2X2 -> listOf(0.5f, 0.5f)
        }
    }
}
