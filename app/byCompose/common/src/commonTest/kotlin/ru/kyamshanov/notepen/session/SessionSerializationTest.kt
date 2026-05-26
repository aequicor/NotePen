package ru.kyamshanov.notepen.session

import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.tabs.TabViewState
import ru.kyamshanov.notepen.tabs.WorkspaceSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Round-trip serialization tests for the Sessions model: `decode(encode(x)) == x`
 * for [SessionData] and [NamedSession], including a multi-panel
 * [WorkspaceSnapshot] built with its real constructor.
 */
class SessionSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun multiPanelLayout(): WorkspaceSnapshot =
        WorkspaceSnapshot(
            template = "TWO_COLUMNS",
            focusedPanelIndex = 1,
            ratios = listOf(0.5f, 0.5f),
            panels =
                listOf(
                    WorkspaceSnapshot.PanelSnapshot(
                        activeTabIndex = 0,
                        tabs =
                            listOf(
                                WorkspaceSnapshot.TabSnapshot(filePath = "/docs/a.pdf", displayName = "A"),
                                WorkspaceSnapshot.TabSnapshot(filePath = "/docs/b.pdf", displayName = "B"),
                            ),
                    ),
                    WorkspaceSnapshot.PanelSnapshot(
                        activeTabIndex = 0,
                        tabs = listOf(WorkspaceSnapshot.TabSnapshot(filePath = "/docs/c.pdf", displayName = "C")),
                    ),
                ),
        )

    private fun sampleSessionData(): SessionData =
        SessionData(
            layout = multiPanelLayout(),
            tabViewStates =
                listOf(
                    listOf(
                        TabViewState(scalePercent = 125, pageIndex = 3, pageOffsetPx = 42),
                        TabViewState(),
                    ),
                    listOf(TabViewState(scalePercent = 80, pageIndex = 0, pageOffsetPx = -10)),
                ),
            schemaVersion = 1,
        )

    @Test
    fun `SessionData round-trips through json`() {
        val original = sampleSessionData()
        val decoded =
            json.decodeFromString(
                SessionData.serializer(),
                json.encodeToString(SessionData.serializer(), original),
            )
        assertEquals(original, decoded)
    }

    @Test
    fun `NamedSession round-trips through json`() {
        val original =
            NamedSession(
                id = "session-1",
                name = "Reading session",
                savedAtEpochMs = 1_700_000_000_000L,
                data = sampleSessionData(),
            )
        val decoded =
            json.decodeFromString(
                NamedSession.serializer(),
                json.encodeToString(NamedSession.serializer(), original),
            )
        assertEquals(original, decoded)
    }

    @Test
    fun `TabViewState defaults round-trip`() {
        val original = TabViewState()
        val decoded =
            json.decodeFromString(
                TabViewState.serializer(),
                json.encodeToString(TabViewState.serializer(), original),
            )
        assertEquals(original, decoded)
        assertEquals(100, decoded.scalePercent)
        assertEquals(0, decoded.pageIndex)
        assertEquals(0, decoded.pageOffsetPx)
    }
}
