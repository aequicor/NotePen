package ru.kyamshanov.notepen

import ru.kyamshanov.notepen.tabs.DocumentId
import ru.kyamshanov.notepen.tabs.DocumentTab
import ru.kyamshanov.notepen.tabs.OpenDocuments
import ru.kyamshanov.notepen.tabs.Panel
import ru.kyamshanov.notepen.tabs.PanelId
import ru.kyamshanov.notepen.tabs.PdfDocumentState
import ru.kyamshanov.notepen.tabs.WorkspaceLayout
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailsOpenDocumentsBroadcastTest {
    @Test
    fun openDocumentsBroadcastUsesExistingTabDocumentId() {
        val tab =
            DocumentTab(
                id = DocumentId(1),
                filePath = "/docs/tablet.pdf",
                displayName = "tablet.pdf",
            )
        val layout =
            WorkspaceLayout.single(
                Panel(
                    id = PanelId(1),
                    tabs = OpenDocuments.of(tab),
                ),
            )

        val infos =
            openDocumentInfosForBroadcast(
                layout = layout,
                stateOf = { PdfDocumentState.create(filePath = it.filePath, documentId = "tab-doc-id") },
                receivedPdfDir = null,
            )

        assertEquals("tab-doc-id", infos.single().documentId)
        assertEquals("tablet.pdf", infos.single().displayName)
        assertEquals("/docs/tablet.pdf", infos.single().absolutePath)
    }

    @Test
    fun openDocumentsBroadcastDoesNotReadvertiseRemoteCachedFiles() {
        val localTab =
            DocumentTab(
                id = DocumentId(1),
                filePath = "/docs/local.pdf",
                displayName = "local.pdf",
            )
        val cachedTab =
            DocumentTab(
                id = DocumentId(2),
                filePath = "/cache/sync/remote.pdf",
                displayName = "remote.pdf",
            )
        val layout =
            WorkspaceLayout.single(
                Panel(
                    id = PanelId(1),
                    tabs = OpenDocuments.of(localTab).addTab(cachedTab),
                ),
            )

        val infos =
            openDocumentInfosForBroadcast(
                layout = layout,
                stateOf = { tab -> PdfDocumentState.create(filePath = tab.filePath, documentId = "doc-${tab.id.value}") },
                receivedPdfDir = "/cache/sync",
            )

        assertEquals(listOf("doc-1"), infos.map { it.documentId })
    }
}
