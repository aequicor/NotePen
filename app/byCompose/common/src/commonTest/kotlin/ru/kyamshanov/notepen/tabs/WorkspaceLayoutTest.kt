package ru.kyamshanov.notepen.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceLayoutTest {
    private fun tab(
        id: Long,
        path: String = "/p/$id.pdf",
        name: String = "doc-$id",
    ) = DocumentTab(id = DocumentId(id), filePath = path, displayName = name)

    private fun panel(
        panelId: Long,
        vararg tabIds: Long,
    ): Panel {
        val tabs = tabIds.map { tab(it) }
        return Panel(
            id = PanelId(panelId),
            tabs = OpenDocuments(tabs = tabs, activeId = tabs.firstOrNull()?.id),
        )
    }

    private fun singlePanel(
        panelId: Long = 1L,
        vararg tabIds: Long,
    ): WorkspaceLayout = WorkspaceLayout.single(panel(panelId, *(if (tabIds.isEmpty()) longArrayOf(10L) else tabIds)))

    // -- invariants --

    @Test
    fun `single seeds a FULL one-panel layout focused on it`() {
        val layout = WorkspaceLayout.single(panel(1, 10))
        assertEquals(LayoutTemplate.FULL, layout.template)
        assertEquals(1, layout.panels.size)
        assertEquals(PanelId(1), layout.focusedPanelId)
        assertTrue(!layout.isSplit)
    }

    @Test
    fun `panel count must match template capacity`() {
        assertFails {
            WorkspaceLayout(
                panels = listOf(panel(1, 10)),
                template = LayoutTemplate.COLUMNS_2,
                focusedPanelId = PanelId(1),
            )
        }
    }

    @Test
    fun `focusedPanelId must exist`() {
        assertFails {
            WorkspaceLayout(
                panels = listOf(panel(1, 10)),
                template = LayoutTemplate.FULL,
                focusedPanelId = PanelId(99),
            )
        }
    }

    // -- addPanel --

    @Test
    fun `addPanel to COLUMNS_2 appends and focuses new panel`() {
        val layout =
            WorkspaceLayout.single(panel(1, 10))
                .addPanel(LayoutTemplate.COLUMNS_2, panel(2, 20))
        assertEquals(LayoutTemplate.COLUMNS_2, layout.template)
        assertEquals(listOf(PanelId(1), PanelId(2)), layout.panels.map { it.id })
        assertEquals(PanelId(2), layout.focusedPanelId)
        assertTrue(layout.isSplit)
    }

    @Test
    fun `addPanel rejects wrong capacity`() {
        assertFails {
            WorkspaceLayout.single(panel(1, 10)).addPanel(LayoutTemplate.COLUMNS_3, panel(2, 20))
        }
    }

    @Test
    fun `addPanel supports LEFT_PLUS_STACK three-panel template`() {
        val layout =
            WorkspaceLayout.single(panel(1, 10))
                .addPanel(LayoutTemplate.COLUMNS_2, panel(2, 20))
                .addPanel(LayoutTemplate.LEFT_PLUS_STACK, panel(3, 30))
        assertEquals(LayoutTemplate.LEFT_PLUS_STACK, layout.template)
        assertEquals(3, layout.panels.size)
        assertEquals(WorkspaceLayout.defaultRatios(LayoutTemplate.LEFT_PLUS_STACK), layout.ratios)
    }

    // -- withPanelTabs (close / collapse) --

    @Test
    fun `closing last tab of last panel returns null`() {
        val layout = singlePanel(1, 10)
        val after = layout.withPanelTabs(PanelId(1)) { it.closeTab(DocumentId(10)) }
        assertNull(after)
    }

    @Test
    fun `closing last tab of a panel removes panel and downgrades template`() {
        val layout =
            WorkspaceLayout.single(panel(1, 10))
                .addPanel(LayoutTemplate.COLUMNS_2, panel(2, 20))
        val after = layout.withPanelTabs(PanelId(2)) { it.closeTab(DocumentId(20)) }
        assertEquals(LayoutTemplate.FULL, after?.template)
        assertEquals(listOf(PanelId(1)), after?.panels?.map { it.id })
    }

    @Test
    fun `removing focused panel moves focus to a survivor`() {
        val layout =
            WorkspaceLayout.single(panel(1, 10))
                .addPanel(LayoutTemplate.COLUMNS_2, panel(2, 20)) // focus = panel 2
        val after = layout.withPanelTabs(PanelId(2)) { it.closeTab(DocumentId(20)) }
        assertEquals(PanelId(1), after?.focusedPanelId)
    }

    @Test
    fun `closing a non-last tab keeps the panel`() {
        val layout = WorkspaceLayout.single(panel(1, 10, 11))
        val after = layout.withPanelTabs(PanelId(1)) { it.closeTab(DocumentId(10)) }
        assertEquals(1, after?.panels?.size)
        assertEquals(listOf(DocumentId(11)), after?.panels?.single()?.tabs?.tabs?.map { it.id })
    }

    // -- focus & ratios --

    @Test
    fun `focusPanel changes focus`() {
        val layout =
            WorkspaceLayout.single(panel(1, 10))
                .addPanel(LayoutTemplate.COLUMNS_2, panel(2, 20))
                .focusPanel(PanelId(1))
        assertEquals(PanelId(1), layout.focusedPanelId)
    }

    @Test
    fun `setRatio clamps to bounds`() {
        val layout =
            WorkspaceLayout.single(panel(1, 10))
                .addPanel(LayoutTemplate.COLUMNS_2, panel(2, 20))
        assertEquals(WorkspaceLayout.MIN_RATIO, layout.setRatio(0, 0.01f).ratios[0])
        assertEquals(WorkspaceLayout.MAX_RATIO, layout.setRatio(0, 0.99f).ratios[0])
    }

    @Test
    fun `setRatio ignores out-of-range index`() {
        val layout = WorkspaceLayout.single(panel(1, 10))
        assertEquals(layout, layout.setRatio(0, 0.5f))
    }
}
