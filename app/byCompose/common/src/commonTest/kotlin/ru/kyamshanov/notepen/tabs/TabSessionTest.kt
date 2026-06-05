package ru.kyamshanov.notepen.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabSessionTest {
    @Test
    fun `focusDocument activates tab and panel matching sync document id`() {
        val session = newSession()
        session.restore(
            WorkspaceSnapshot(
                template = LayoutTemplate.COLUMNS_2.name,
                focusedPanelIndex = 0,
                ratios = WorkspaceLayout.defaultRatios(LayoutTemplate.COLUMNS_2),
                panels =
                    listOf(
                        WorkspaceSnapshot.PanelSnapshot(
                            activeTabIndex = 0,
                            tabs =
                                listOf(
                                    WorkspaceSnapshot.TabSnapshot(filePath = "/docs/a.pdf", displayName = "a.pdf"),
                                ),
                        ),
                        WorkspaceSnapshot.PanelSnapshot(
                            activeTabIndex = 0,
                            tabs =
                                listOf(
                                    WorkspaceSnapshot.TabSnapshot(filePath = "/docs/b.pdf", displayName = "b.pdf"),
                                ),
                        ),
                    ),
            ),
        )

        assertTrue(session.focusDocument("doc-b"))

        assertEquals(1, session.layout.panels.indexOfFirst { it.id == session.layout.focusedPanelId })
        assertEquals("doc-b", session.focusedActiveState?.documentId)
    }

    @Test
    fun `focusDocument ignores unknown or blank document id`() {
        val session = newSession()
        val focusedPanelId = session.layout.focusedPanelId

        assertFalse(session.focusDocument(""))
        assertFalse(session.focusDocument("doc-missing"))

        assertEquals(focusedPanelId, session.layout.focusedPanelId)
    }

    private fun newSession(): TabSession =
        TabSession(
            idGenerator = SequentialIdGenerator(),
            fallbackNameCounter = FallbackNameCounter(),
            syncDocumentIdFor = { path -> "doc-${path.substringAfterLast('/').substringBefore('.')}" },
            initialFilePath = "/docs/initial.pdf",
            initialDisplayName = "initial.pdf",
        )
}
