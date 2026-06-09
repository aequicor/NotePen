package ru.kyamshanov.notepen.sync

import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.PdfDrawingState
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.sync.domain.SyncEngine
import ru.kyamshanov.notepen.sync.domain.model.DrawingPathDto
import ru.kyamshanov.notepen.sync.domain.model.PointDto
import ru.kyamshanov.notepen.sync.domain.model.StrokeDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncBridgeTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `peer add and remove deltas update drawing state`() =
        runTest {
            val drawingStates = mutableMapOf<Int, PdfDrawingState>()
            val engine =
                SyncEngine(
                    deviceId = "desktop",
                    documentId = "doc-1",
                    scope = backgroundScope,
                )
            SyncBridge(
                engine = engine,
                drawingStates = drawingStates,
                notes = mutableStateMapOf<Int, List<PageNote>>(),
                scope = backgroundScope,
            ).start()
            runCurrent()

            engine.processPeer(remoteAdded())
            runCurrent()
            advanceUntilIdle()

            val pageState = drawingStates.getValue(2)
            assertEquals(listOf("stroke-1"), pageState.currentPaths.map { it.strokeId })
            assertTrue(pageState.historyVersion.value > 0)

            engine.processPeer(remoteRemoved())
            runCurrent()
            advanceUntilIdle()

            assertTrue(pageState.currentPaths.isEmpty())
        }

    private fun remoteAdded(): StrokeDelta.Added =
        StrokeDelta.Added(
            strokeId = "stroke-1",
            pageIndex = 2,
            authorDeviceId = "tablet",
            clock = 1,
            path =
                DrawingPathDto(
                    strokeId = "stroke-1",
                    colorArgb = 0xFF000000,
                    strokeWidth = 0.002f,
                    points =
                        listOf(
                            PointDto(x = 0.1f, y = 0.2f, isNewPath = true),
                            PointDto(x = 0.3f, y = 0.4f),
                        ),
                ),
        )

    private fun remoteRemoved(): StrokeDelta.Removed =
        StrokeDelta.Removed(
            strokeId = "stroke-1",
            pageIndex = 2,
            authorDeviceId = "tablet",
            clock = 2,
        )
}
