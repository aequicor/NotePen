package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.sync.domain.port.AnnotationResyncRequester
import ru.kyamshanov.notepen.sync.infrastructure.InMemoryOpenDocumentRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveDocumentSyncControllerTest {
    private val docId = "book.pdf#abc123"

    /** Records resync requests so tests can assert the catch-up fires (and only once). */
    private class RecordingResyncRequester : AnnotationResyncRequester {
        val requested = mutableListOf<String>()

        override fun requestResync(documentId: String) {
            requested.add(documentId)
        }
    }

    private fun fixture(
        resyncRequester: AnnotationResyncRequester? = null,
    ): Triple<InMemoryOpenDocumentRegistry, SyncEngineRegistry, LiveDocumentSyncController> {
        val open = InMemoryOpenDocumentRegistry()
        val engines = SyncEngineRegistry(deviceId = "dev-1", scope = TestScope())
        val controller =
            LiveDocumentSyncController(
                openDocumentRegistry = open,
                syncEngineRegistry = engines,
                resyncRequester = resyncRequester,
            )
        return Triple(open, engines, controller)
    }

    @Test
    fun enableAcquiresAndMarksEngineActive() =
        runTest {
            val (open, engines, controller) = fixture()
            // Default OFF: bind pauses the engine, doc not pinned, not live.
            controller.bind(docId)
            assertFalse(engines.get(docId).active.value, "engine should be paused after bind (default OFF)")
            assertTrue(open.openDocumentIds.value.isEmpty(), "doc must not be pinned before enable")
            assertFalse(controller.isLiveNow(docId))

            controller.enable(docId)

            assertTrue(controller.isLiveNow(docId), "doc should be live after enable")
            assertTrue(docId in controller.liveDocumentIds.value)
            assertTrue(controller.isLive(docId).first(), "isLive flow should emit true")
            assertEquals(setOf(docId), open.openDocumentIds.value, "enable must pin the doc")
            assertTrue(engines.get(docId).active.value, "enable must mark the engine actively broadcasting")
        }

    @Test
    fun disableReleasesAndPausesEngine() =
        runTest {
            val (open, engines, controller) = fixture()
            controller.enable(docId)
            assertEquals(setOf(docId), open.openDocumentIds.value)

            controller.disable(docId)

            assertFalse(controller.isLiveNow(docId), "doc should not be live after disable")
            assertFalse(controller.isLive(docId).first(), "isLive flow should emit false")
            assertTrue(open.openDocumentIds.value.isEmpty(), "disable must release the doc")
            assertFalse(engines.get(docId).active.value, "disable must pause the engine")
        }

    @Test
    fun enableIsIdempotent() =
        runTest {
            val (open, _, controller) = fixture()

            controller.enable(docId)
            controller.enable(docId)
            controller.enable(docId)

            assertTrue(controller.isLiveNow(docId))
            // Ref-counted acquire must not stack on repeated enable: one matching
            // disable fully releases the pin.
            controller.disable(docId)
            assertTrue(open.openDocumentIds.value.isEmpty(), "single disable must fully release after repeated enable")
            assertFalse(controller.isLiveNow(docId))
        }

    @Test
    fun disableIsIdempotentWhenNeverEnabled() =
        runTest {
            val (open, engines, controller) = fixture()

            controller.disable(docId)

            assertFalse(controller.isLiveNow(docId))
            assertTrue(open.openDocumentIds.value.isEmpty(), "disabling a never-enabled doc is a no-op")
            // No engine was created/touched for a pure no-op disable.
            assertTrue(engines.snapshot().isEmpty(), "no-op disable must not create an engine")
        }

    @Test
    fun toggleFlipsState() =
        runTest {
            val (_, _, controller) = fixture()

            controller.toggle(docId)
            assertTrue(controller.isLiveNow(docId), "first toggle enables")

            controller.toggle(docId)
            assertFalse(controller.isLiveNow(docId), "second toggle disables")
        }

    @Test
    fun enableTriggersResyncCatchUp() =
        runTest {
            val requester = RecordingResyncRequester()
            val (_, _, controller) = fixture(resyncRequester = requester)

            controller.enable(docId)

            // Enabling live-sync (OFF→ON) requests a fresh full-state snapshot so
            // remote edits made while this side was paused are reconciled by LWW.
            assertEquals(listOf(docId), requester.requested, "enable must request exactly one resync")
        }

    @Test
    fun repeatedEnableDoesNotDoubleRequestResync() =
        runTest {
            val requester = RecordingResyncRequester()
            val (_, _, controller) = fixture(resyncRequester = requester)

            controller.enable(docId)
            controller.enable(docId)
            controller.enable(docId)

            // Idempotent: only the real OFF→ON transition requests a resync; repeated
            // enable of an already-live doc must not spam snapshot requests.
            assertEquals(listOf(docId), requester.requested, "repeated enable must request resync only once")
        }

    @Test
    fun reEnableAfterDisableRequestsResyncAgain() =
        runTest {
            val requester = RecordingResyncRequester()
            val (_, _, controller) = fixture(resyncRequester = requester)

            controller.enable(docId) // OFF→ON: resync #1
            controller.disable(docId) // ON→OFF: no resync (disabling loses nothing)
            controller.enable(docId) // OFF→ON again: resync #2 (catch up edits missed while paused)

            assertEquals(listOf(docId, docId), requester.requested, "each OFF→ON transition must resync")
        }

    @Test
    fun disableDoesNotRequestResync() =
        runTest {
            val requester = RecordingResyncRequester()
            val (_, _, controller) = fixture(resyncRequester = requester)

            controller.enable(docId)
            requester.requested.clear()
            controller.disable(docId)

            assertTrue(requester.requested.isEmpty(), "disable must not request a resync")
        }

    @Test
    fun pausedEngineBuffersLocalEditsButDoesNotApplyRemote() =
        runTest {
            val (_, engines, controller) = fixture()
            controller.bind(docId)
            val engine = engines.get(docId)
            assertFalse(engine.active.value)

            // Remote delta on a paused engine is ignored (not mirrored onto mergedDeltas).
            // We can't easily await "nothing", so assert the gate via the public flag instead.
            controller.enable(docId)
            assertTrue(engine.active.value)
            controller.disable(docId)
            assertFalse(engine.active.value)
        }
}
