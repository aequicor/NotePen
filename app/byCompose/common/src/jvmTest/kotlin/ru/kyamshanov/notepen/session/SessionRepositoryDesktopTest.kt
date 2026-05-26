package ru.kyamshanov.notepen.session

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.tabs.TabViewState
import ru.kyamshanov.notepen.tabs.WorkspaceSnapshot
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests [SessionRepositoryDesktop] against a portable temp directory, with a
 * [StandardTestDispatcher] injected as the IO dispatcher.
 */
class SessionRepositoryDesktopTest {
    private lateinit var tmpDir: File
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: SessionRepositoryDesktop

    @BeforeTest
    fun setUp() {
        tmpDir = Files.createTempDirectory("notepen-test-sessions").toFile()
        repository = SessionRepositoryDesktop(appDataDir = tmpDir, ioDispatcher = dispatcher)
    }

    @AfterTest
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun layout(label: String): WorkspaceSnapshot =
        WorkspaceSnapshot(
            template = "SINGLE",
            focusedPanelIndex = 0,
            ratios = listOf(1.0f),
            panels =
                listOf(
                    WorkspaceSnapshot.PanelSnapshot(
                        activeTabIndex = 0,
                        tabs = listOf(WorkspaceSnapshot.TabSnapshot(filePath = "/docs/$label.pdf", displayName = label)),
                    ),
                ),
        )

    private fun sessionData(label: String): SessionData =
        SessionData(
            layout = layout(label),
            tabViewStates = listOf(listOf(TabViewState(scalePercent = 110, pageIndex = 2, pageOffsetPx = 7))),
        )

    private fun named(
        id: String,
        name: String,
    ): NamedSession =
        NamedSession(
            id = id,
            name = name,
            savedAtEpochMs = 1_700_000_000_000L,
            data = sessionData(id),
        )

    @Test
    fun `saveAutosave then loadAutosave returns the saved data`() =
        runTest(dispatcher) {
            val data = sessionData("auto")
            repository.saveAutosave(data)

            assertEquals(data, repository.loadAutosave())
        }

    @Test
    fun `loadAutosave returns null before any save`() =
        runTest(dispatcher) {
            assertNull(repository.loadAutosave())
        }

    @Test
    fun `clearAutosave makes loadAutosave return null`() =
        runTest(dispatcher) {
            repository.saveAutosave(sessionData("auto"))
            repository.clearAutosave()

            assertNull(repository.loadAutosave())
        }

    @Test
    fun `saveNamed inserts and listNamed returns it`() =
        runTest(dispatcher) {
            val session = named("s1", "First")
            repository.saveNamed(session)

            val all = repository.listNamed()
            assertEquals(1, all.size)
            assertEquals(session, all.single())
        }

    @Test
    fun `saveNamed with same id updates in place without duplicating`() =
        runTest(dispatcher) {
            repository.saveNamed(named("s1", "First"))
            val updated = named("s1", "First renamed").copy(savedAtEpochMs = 1_700_000_999_000L)
            repository.saveNamed(updated)

            val all = repository.listNamed()
            assertEquals(1, all.size, "Upsert by id must not create a duplicate")
            assertEquals(updated, all.single())
        }

    @Test
    fun `listNamed returns all inserted sessions`() =
        runTest(dispatcher) {
            repository.saveNamed(named("s1", "First"))
            repository.saveNamed(named("s2", "Second"))

            val ids = repository.listNamed().map { it.id }.toSet()
            assertEquals(setOf("s1", "s2"), ids)
        }

    @Test
    fun `deleteNamed removes only the matching session`() =
        runTest(dispatcher) {
            repository.saveNamed(named("s1", "First"))
            repository.saveNamed(named("s2", "Second"))
            repository.deleteNamed("s1")

            val all = repository.listNamed()
            assertEquals(1, all.size)
            assertEquals("s2", all.single().id)
        }

    @Test
    fun `deleteNamed on absent id is a no-op`() =
        runTest(dispatcher) {
            repository.saveNamed(named("s1", "First"))
            repository.deleteNamed("does-not-exist")

            assertEquals(1, repository.listNamed().size)
        }

    @Test
    fun `named sessions persist across reinstantiation`() =
        runTest(dispatcher) {
            repository.saveNamed(named("s1", "First"))

            val reopened = SessionRepositoryDesktop(appDataDir = tmpDir, ioDispatcher = dispatcher)
            val all = reopened.listNamed()
            assertTrue(all.any { it.id == "s1" }, "Named session must survive reinstantiation")
        }
}
