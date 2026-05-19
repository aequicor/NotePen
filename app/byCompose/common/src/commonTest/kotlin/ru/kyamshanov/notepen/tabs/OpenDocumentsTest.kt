package ru.kyamshanov.notepen.tabs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenDocumentsTest {

    private fun tab(id: Long, path: String = "/p/$id.pdf", name: String = "doc-$id") =
        DocumentTab(id = DocumentId(id), filePath = path, displayName = name)

    // -- addTab --

    @Test
    fun `addTab into empty makes new tab active`() {
        val a = tab(1)
        val docs = OpenDocuments.Empty.addTab(a)
        assertEquals(listOf(a), docs.tabs)
        assertEquals(a.id, docs.activeId)
        assertEquals(a, docs.activeTab)
    }

    @Test
    fun `addTab keeps existing active when makeActive is false`() {
        val a = tab(1)
        val b = tab(2)
        val docs = OpenDocuments.of(a).addTab(b, makeActive = false)
        assertEquals(listOf(a, b), docs.tabs)
        assertEquals(a.id, docs.activeId)
    }

    @Test
    fun `addTab default makes new tab active`() {
        val a = tab(1)
        val b = tab(2)
        val docs = OpenDocuments.of(a).addTab(b)
        assertEquals(b.id, docs.activeId)
    }

    @Test
    fun `addTab rejects duplicate id`() {
        val a = tab(1)
        assertFails {
            OpenDocuments.of(a).addTab(a)
        }
    }

    @Test
    fun `addTab opens same file path twice as independent tabs`() {
        // Spec: "open the same PDF twice = new independent tab with its own state"
        val first = tab(id = 1, path = "/shared.pdf", name = "shared.pdf")
        val second = tab(id = 2, path = "/shared.pdf", name = "shared.pdf")
        val docs = OpenDocuments.of(first).addTab(second)

        assertEquals(2, docs.tabs.size)
        assertEquals(first.filePath, second.filePath)
        // …but their DocumentIds are distinct, which is how the state
        // registry keeps the two tabs' Compose state independent.
        assertNotEquals(first.id, second.id)
    }

    // -- closeTab --

    @Test
    fun `closeTab of non-active leaves active unchanged`() {
        val a = tab(1)
        val b = tab(2)
        val before = OpenDocuments.of(a).addTab(b) // active=b
        val after = before.closeTab(a.id)
        assertEquals(listOf(b), after?.tabs)
        assertEquals(b.id, after?.activeId)
    }

    @Test
    fun `closeTab of active moves active to next tab`() {
        val a = tab(1)
        val b = tab(2)
        val c = tab(3)
        val before = OpenDocuments.of(a).addTab(b).addTab(c) // active=c
        val docs = before.setActive(b.id) // active=b
        val after = docs.closeTab(b.id)
        // b removed, next tab at the same index is c — that's the new active.
        assertEquals(listOf(a, c), after?.tabs)
        assertEquals(c.id, after?.activeId)
    }

    @Test
    fun `closeTab of last index falls back to previous`() {
        val a = tab(1)
        val b = tab(2)
        val docs = OpenDocuments.of(a).addTab(b) // active=b
        val after = docs.closeTab(b.id)
        assertEquals(listOf(a), after?.tabs)
        assertEquals(a.id, after?.activeId)
    }

    @Test
    fun `closeTab of only tab returns null`() {
        val a = tab(1)
        val after = OpenDocuments.of(a).closeTab(a.id)
        assertNull(after)
    }

    @Test
    fun `closeTab of unknown id is a no-op`() {
        val a = tab(1)
        val docs = OpenDocuments.of(a)
        val after = docs.closeTab(DocumentId(999))
        assertEquals(docs, after)
    }

    // -- setActive --

    @Test
    fun `setActive points active at the given id`() {
        val a = tab(1)
        val b = tab(2)
        val docs = OpenDocuments.of(a).addTab(b) // active=b
        val switched = docs.setActive(a.id)
        assertEquals(a.id, switched.activeId)
        assertEquals(a, switched.activeTab)
    }

    @Test
    fun `setActive rejects unknown id`() {
        val a = tab(1)
        assertFails {
            OpenDocuments.of(a).setActive(DocumentId(999))
        }
    }

    // -- invariants & helpers --

    @Test
    fun `Empty has no tabs and no active`() {
        assertTrue(OpenDocuments.Empty.tabs.isEmpty())
        assertNull(OpenDocuments.Empty.activeId)
        assertNull(OpenDocuments.Empty.activeTab)
        assertTrue(OpenDocuments.Empty.isEmpty)
    }

    @Test
    fun `constructor rejects activeId pointing to missing tab`() {
        assertFails {
            OpenDocuments(tabs = listOf(tab(1)), activeId = DocumentId(2))
        }
    }

    @Test
    fun `constructor rejects activeId when tabs is empty`() {
        assertFails {
            OpenDocuments(tabs = emptyList(), activeId = DocumentId(1))
        }
    }
}

class FallbackNameCounterTest {

    @Test
    fun `next returns Document 1 first then increments`() {
        val counter = FallbackNameCounter()
        assertEquals("Document 1", counter.next())
        assertEquals("Document 2", counter.next())
        assertEquals("Document 3", counter.next())
    }

    @Test
    fun `next is monotonic across an arbitrary number of calls`() {
        val counter = FallbackNameCounter()
        val names = (1..50).map { counter.next() }
        assertEquals((1..50).map { "Document $it" }, names)
    }

    @Test
    fun `initial seed offsets the counter`() {
        val counter = FallbackNameCounter(initial = 10)
        assertEquals("Document 11", counter.next())
    }
}

class SequentialIdGeneratorTest {

    @Test
    fun `next produces strictly increasing ids`() {
        val gen = SequentialIdGenerator()
        val ids = (1..10).map { gen.next() }
        assertEquals(ids.sortedBy { it.value }, ids)
        assertEquals(ids.distinct().size, ids.size)
    }
}
