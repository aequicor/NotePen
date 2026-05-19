package ru.kyamshanov.notepen.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull

class PanelLayoutTest {

    private fun tab(id: Long, path: String = "/p/$id.pdf", name: String = "doc-$id") =
        DocumentTab(id = DocumentId(id), filePath = path, displayName = name)

    private fun singleOf(vararg ids: Long): PanelLayout.Single {
        val tabs = ids.map { tab(it) }
        return PanelLayout.Single(
            OpenDocuments(tabs = tabs, activeId = tabs.firstOrNull()?.id),
        )
    }

    // -- openInSplit --

    @Test
    fun `openInSplit transitions Single to Split with PRIMARY on left`() {
        val original = singleOf(1, 2)
        val newTab = tab(3, path = "/extra.pdf", name = "extra")
        val secondary = OpenDocuments.of(newTab)

        val result = original.openInSplit(PanelOrientation.HORIZONTAL, secondary)

        assertIs<PanelLayout.Split>(result)
        assertEquals(PanelOrientation.HORIZONTAL, result.orientation)
        assertEquals(original, result.left)
        assertEquals(secondary, result.right.tabs)
        assertEquals(PanelLayout.DEFAULT_RATIO, result.ratio)
    }

    @Test
    fun `openInSplit on a Split is rejected (no nested splits)`() {
        val split = singleOf(1).openInSplit(
            PanelOrientation.HORIZONTAL,
            OpenDocuments.of(tab(2)),
        )
        assertFails {
            // Receiver is now PanelLayout.Split — must throw.
            split.openInSplit(PanelOrientation.VERTICAL, OpenDocuments.of(tab(3)))
        }
    }

    @Test
    fun `Split with nested Split children is rejected by the type system`() {
        // PanelLayout.Split.left and .right are typed as PanelLayout.Single,
        // so trying to embed another Split is a compile error — there's
        // nothing to test at runtime. This @Test stands as documentation:
        // see [PanelLayout.Split] for the invariant.
    }

    // -- Split.ratio invariants --

    @Test
    fun `Split rejects ratio below MIN_RATIO`() {
        assertFails {
            PanelLayout.Split(
                orientation = PanelOrientation.HORIZONTAL,
                ratio = 0.1f,
                left = singleOf(1),
                right = singleOf(2),
            )
        }
    }

    @Test
    fun `Split rejects ratio above MAX_RATIO`() {
        assertFails {
            PanelLayout.Split(
                orientation = PanelOrientation.HORIZONTAL,
                ratio = 0.9f,
                left = singleOf(1),
                right = singleOf(2),
            )
        }
    }

    @Test
    fun `setRatio clamps to MIN_RATIO`() {
        val split = singleOf(1).openInSplit(
            PanelOrientation.HORIZONTAL,
            OpenDocuments.of(tab(2)),
        )
        val tight = split.setRatio(0.05f)
        assertIs<PanelLayout.Split>(tight)
        assertEquals(PanelLayout.MIN_RATIO, tight.ratio)
    }

    @Test
    fun `setRatio clamps to MAX_RATIO`() {
        val split = singleOf(1).openInSplit(
            PanelOrientation.HORIZONTAL,
            OpenDocuments.of(tab(2)),
        )
        val wide = split.setRatio(0.99f)
        assertIs<PanelLayout.Split>(wide)
        assertEquals(PanelLayout.MAX_RATIO, wide.ratio)
    }

    @Test
    fun `setRatio is a no-op on Single`() {
        val single = singleOf(1)
        assertEquals(single, single.setRatio(0.7f))
    }

    // -- transformTabs collapse rules --

    @Test
    fun `closeTab in Single returns Single`() {
        val single = singleOf(1, 2)
        val after = single.transformTabs(PanelSide.PRIMARY) { it.closeTab(DocumentId(1)) }
        assertIs<PanelLayout.Single>(after)
        assertEquals(listOf(DocumentId(2)), after.tabs.tabs.map { it.id })
    }

    @Test
    fun `closeTab of last tab in Single returns null`() {
        val single = singleOf(1)
        val after = single.transformTabs(PanelSide.PRIMARY) { it.closeTab(DocumentId(1)) }
        assertNull(after)
    }

    @Test
    fun `closeTab of last tab in Split LEFT collapses to right`() {
        val split = singleOf(1).openInSplit(
            PanelOrientation.HORIZONTAL,
            OpenDocuments.of(tab(2)),
        )
        val collapsed = split.transformTabs(PanelSide.PRIMARY) { it.closeTab(DocumentId(1)) }
        assertIs<PanelLayout.Single>(collapsed)
        assertEquals(listOf(DocumentId(2)), collapsed.tabs.tabs.map { it.id })
    }

    @Test
    fun `closeTab of last tab in Split RIGHT collapses to left`() {
        val split = singleOf(1).openInSplit(
            PanelOrientation.HORIZONTAL,
            OpenDocuments.of(tab(2)),
        )
        val collapsed = split.transformTabs(PanelSide.SECONDARY) { it.closeTab(DocumentId(2)) }
        assertIs<PanelLayout.Single>(collapsed)
        assertEquals(listOf(DocumentId(1)), collapsed.tabs.tabs.map { it.id })
    }

    @Test
    fun `closeTab of non-last tab in Split keeps Split`() {
        val original = PanelLayout.Single(
            OpenDocuments(
                tabs = listOf(tab(1), tab(2)),
                activeId = DocumentId(2),
            ),
        )
        val split = original.openInSplit(
            PanelOrientation.HORIZONTAL,
            OpenDocuments.of(tab(3)),
        )
        val after = split.transformTabs(PanelSide.PRIMARY) { it.closeTab(DocumentId(1)) }
        assertIs<PanelLayout.Split>(after)
        assertEquals(listOf(DocumentId(2)), after.left.tabs.tabs.map { it.id })
        assertEquals(listOf(DocumentId(3)), after.right.tabs.tabs.map { it.id })
    }

    @Test
    fun `addTab in Split PRIMARY only mutates left`() {
        val split = singleOf(1).openInSplit(
            PanelOrientation.HORIZONTAL,
            OpenDocuments.of(tab(2)),
        )
        val newTab = tab(3)
        val after = split.transformTabs(PanelSide.PRIMARY) { it.addTab(newTab) }
        assertIs<PanelLayout.Split>(after)
        assertEquals(listOf(DocumentId(1), DocumentId(3)), after.left.tabs.tabs.map { it.id })
        assertEquals(listOf(DocumentId(2)), after.right.tabs.tabs.map { it.id })
    }

    @Test
    fun `tabsOf returns the matching side's OpenDocuments`() {
        val split = singleOf(1).openInSplit(
            PanelOrientation.HORIZONTAL,
            OpenDocuments.of(tab(2)),
        )
        assertEquals(split.left.tabs, split.tabsOf(PanelSide.PRIMARY))
        assertEquals(split.right.tabs, split.tabsOf(PanelSide.SECONDARY))
    }

    @Test
    fun `tabsOf SECONDARY on Single is null`() {
        val single = singleOf(1)
        assertEquals(single.tabs, single.tabsOf(PanelSide.PRIMARY))
        assertNull(single.tabsOf(PanelSide.SECONDARY))
    }
}
