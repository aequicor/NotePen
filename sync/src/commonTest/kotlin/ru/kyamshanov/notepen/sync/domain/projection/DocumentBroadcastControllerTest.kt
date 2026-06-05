package ru.kyamshanov.notepen.sync.domain.projection

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.drawing.api.ToolMode
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentBroadcastControllerTest {
    private val json = Json { classDiscriminator = "type" }

    @Test
    fun updateFramePublishesDocumentViewportAndTool() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val sent = mutableListOf<NetworkMessage.ProjectionFrame>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = { sent += it },
                    scope = backgroundScope,
                )
            runCurrent()

            controller.updateFrame(
                documentId = "doc-1",
                page = 2,
                viewportOffsetX = -12f,
                viewportOffsetY = -120f,
                viewportScale = 1.5f,
                viewportCenterX = 0.75f,
                viewportCenterY = 2.64f,
                toolMode = ToolMode.MARKER,
            )
            runCurrent()

            assertEquals(1, sent.size)
            assertEquals("doc-1", sent.single().documentId)
            assertEquals(2, sent.single().page)
            assertEquals(-12f, sent.single().viewportOffsetX)
            assertEquals(-120f, sent.single().viewportOffsetY)
            assertEquals(1.5f, sent.single().viewportScale)
            assertEquals(0.75f, sent.single().viewportCenterX)
            assertEquals(2.64f, sent.single().viewportCenterY)
            assertEquals(ToolMode.MARKER, sent.single().toolMode)
        }

    @Test
    fun blankDocumentIdIsNotPublished() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val sent = mutableListOf<NetworkMessage.ProjectionFrame>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = { sent += it },
                    scope = backgroundScope,
                )
            runCurrent()

            controller.updateFrame(
                documentId = "",
                page = 0,
                viewportOffsetX = 0f,
                viewportOffsetY = 0f,
                viewportScale = 1f,
                toolMode = ToolMode.PEN,
            )
            runCurrent()

            assertEquals(emptyList(), sent)
        }

    @Test
    fun updateFrameSanitizesInvalidViewportValuesBeforePublishing() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val sent = mutableListOf<NetworkMessage.ProjectionFrame>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = { sent += it },
                    scope = backgroundScope,
                )
            runCurrent()

            controller.updateFrame(
                documentId = "doc-1",
                page = -1,
                viewportOffsetX = Float.NaN,
                viewportOffsetY = Float.POSITIVE_INFINITY,
                viewportScale = 0f,
                viewportCenterX = Float.NaN,
                viewportCenterY = Float.NEGATIVE_INFINITY,
                toolMode = ToolMode.PEN,
            )
            runCurrent()

            val frame = sent.single()
            assertEquals(0, frame.page)
            assertEquals(0f, frame.viewportOffsetX)
            assertEquals(0f, frame.viewportOffsetY)
            assertEquals(1f, frame.viewportScale)
            assertNull(frame.viewportCenterX)
            assertNull(frame.viewportCenterY)
        }

    @Test
    fun updatePointerSanitizesInvalidPointerBeforePublishing() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val sent = mutableListOf<NetworkMessage.ProjectionFrame>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = { sent += it },
                    scope = backgroundScope,
                )
            runCurrent()

            controller.updatePointer(documentId = "doc-1", page = -2, pointerX = Float.NaN, pointerY = 1.5f)
            runCurrent()

            val frame = sent.single()
            assertEquals(0, frame.page)
            assertNull(frame.pointerX)
            assertEquals(1f, frame.pointerY)
        }

    @Test
    fun updateFrameDoesNotCarryPointerFromAnotherDocument() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val sent = mutableListOf<NetworkMessage.ProjectionFrame>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = { sent += it },
                    scope = backgroundScope,
                )
            runCurrent()

            controller.updatePointer(documentId = "doc-1", page = 0, pointerX = 0.2f, pointerY = 0.3f)
            runCurrent()
            controller.updateFrame(
                documentId = "doc-2",
                page = 1,
                viewportOffsetX = 10f,
                viewportOffsetY = 20f,
                viewportScale = 1.25f,
                toolMode = ToolMode.PEN,
            )
            advanceTimeBy(PROJECTION_FRAME_INTERVAL_MS)
            runCurrent()

            val frame = sent.last()
            assertEquals("doc-2", frame.documentId)
            assertNull(frame.pointerX)
            assertNull(frame.pointerY)
        }

    @Test
    fun updatePointerDoesNotCarryViewportOrToolFromAnotherDocument() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val sent = mutableListOf<NetworkMessage.ProjectionFrame>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = { sent += it },
                    scope = backgroundScope,
                )
            runCurrent()

            controller.updateFrame(
                documentId = "doc-1",
                page = 5,
                viewportOffsetX = -100f,
                viewportOffsetY = 700f,
                viewportScale = 2f,
                toolMode = ToolMode.ERASER,
            )
            runCurrent()
            controller.updatePointer(documentId = "doc-2", page = 1, pointerX = 0.4f, pointerY = 0.5f)
            advanceTimeBy(PROJECTION_FRAME_INTERVAL_MS)
            runCurrent()

            val frame = sent.last()
            assertEquals("doc-2", frame.documentId)
            assertEquals(1, frame.page)
            assertEquals(0f, frame.viewportOffsetX)
            assertEquals(0f, frame.viewportOffsetY)
            assertEquals(1f, frame.viewportScale)
            assertEquals(ToolMode.NONE, frame.toolMode)
            assertEquals(0.4f, frame.pointerX)
            assertEquals(0.5f, frame.pointerY)
        }

    @Test
    fun sendFailureDoesNotStopLaterProjectionFrames() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val sent = mutableListOf<NetworkMessage.ProjectionFrame>()
            var sendAttempts = 0
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = { frame ->
                        sendAttempts += 1
                        if (sendAttempts == 1) error("transient transport failure")
                        sent += frame
                    },
                    scope = backgroundScope,
                )
            runCurrent()

            controller.updateFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetX = 0f,
                viewportOffsetY = 10f,
                viewportScale = 1f,
                toolMode = ToolMode.PEN,
            )
            runCurrent()
            controller.updateFrame(
                documentId = "doc-1",
                page = 2,
                viewportOffsetX = -20f,
                viewportOffsetY = 30f,
                viewportScale = 1.25f,
                toolMode = ToolMode.MARKER,
            )
            advanceTimeBy(PROJECTION_FRAME_INTERVAL_MS)
            runCurrent()

            assertEquals(2, sendAttempts)
            assertEquals(1, sent.size)
            assertEquals(2, sent.single().page)
            assertEquals(-20f, sent.single().viewportOffsetX)
            assertEquals(ToolMode.MARKER, sent.single().toolMode)
        }

    @Test
    fun rapidFrameUpdatesAreRateLimitedAndConflated() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val sent = mutableListOf<NetworkMessage.ProjectionFrame>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = { sent += it },
                    scope = backgroundScope,
                )
            runCurrent()

            controller.updateFrame(
                documentId = "doc-1",
                page = 1,
                viewportOffsetX = 0f,
                viewportOffsetY = 10f,
                viewportScale = 1f,
                toolMode = ToolMode.PEN,
            )
            runCurrent()
            controller.updateFrame(
                documentId = "doc-1",
                page = 2,
                viewportOffsetX = -20f,
                viewportOffsetY = 20f,
                viewportScale = 1.25f,
                toolMode = ToolMode.MARKER,
            )
            controller.updateFrame(
                documentId = "doc-1",
                page = 3,
                viewportOffsetX = -30f,
                viewportOffsetY = 30f,
                viewportScale = 1.5f,
                toolMode = ToolMode.ERASER,
            )
            runCurrent()

            assertEquals(1, sent.size)
            assertEquals(1, sent.single().page)

            advanceTimeBy(PROJECTION_FRAME_INTERVAL_MS)
            runCurrent()

            assertEquals(2, sent.size)
            assertEquals(3, sent.last().page)
            assertEquals(-30f, sent.last().viewportOffsetX)
            assertEquals(ToolMode.ERASER, sent.last().toolMode)
        }

    @Test
    fun incomingProjectionFrameBecomesCurrentFrame() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = {},
                    scope = backgroundScope,
                )
            runCurrent()
            assertNull(controller.currentFrame.value)

            val frame =
                NetworkMessage.ProjectionFrame(
                    documentId = "doc-1",
                    page = 3,
                    viewportOffsetX = -20f,
                    viewportOffsetY = -400f,
                    viewportScale = 2f,
                    toolMode = ToolMode.ERASER,
                )
            incoming.emit(frame)
            runCurrent()

            assertEquals(frame, controller.currentFrame.value)
        }

    @Test
    fun incomingBlankDocumentIdIsIgnored() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = {},
                    scope = backgroundScope,
                )
            runCurrent()

            incoming.emit(NetworkMessage.ProjectionFrame(page = 0, viewportOffsetY = 0f, viewportScale = 1f))
            runCurrent()

            assertNull(controller.currentFrame.value)
        }

    @Test
    fun incomingProjectionFrameIsSanitizedBeforeItBecomesCurrentFrame() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = {},
                    scope = backgroundScope,
                )
            runCurrent()

            incoming.emit(
                NetworkMessage.ProjectionFrame(
                    documentId = "doc-1",
                    page = -1,
                    viewportOffsetX = Float.NEGATIVE_INFINITY,
                    viewportOffsetY = Float.NaN,
                    viewportScale = -1f,
                    viewportCenterX = Float.POSITIVE_INFINITY,
                    pointerX = 2f,
                    pointerY = Float.NaN,
                    toolMode = ToolMode.MARKER,
                ),
            )
            runCurrent()

            val frame = controller.currentFrame.value
            assertEquals(0, frame?.page)
            assertEquals(0f, frame?.viewportOffsetX)
            assertEquals(0f, frame?.viewportOffsetY)
            assertEquals(1f, frame?.viewportScale)
            assertNull(frame?.viewportCenterX)
            assertEquals(1f, frame?.pointerX)
            assertNull(frame?.pointerY)
            assertEquals(ToolMode.MARKER, frame?.toolMode)
        }

    @Test
    fun clearCurrentFrameDropsLastIncomingProjectionFrame() =
        runTest {
            val incoming = MutableSharedFlow<NetworkMessage>()
            val controller =
                DocumentBroadcastController(
                    incomingMessages = incoming,
                    sendFrame = {},
                    scope = backgroundScope,
                )
            runCurrent()

            incoming.emit(
                NetworkMessage.ProjectionFrame(
                    documentId = "doc-1",
                    page = 1,
                    viewportOffsetY = 20f,
                    viewportScale = 1f,
                ),
            )
            runCurrent()

            controller.clearCurrentFrame()

            assertNull(controller.currentFrame.value)
        }

    @Test
    fun projectionFrameWireRoundTripPreservesDocumentViewportAndTool() {
        val frame: NetworkMessage =
            NetworkMessage.ProjectionFrame(
                documentId = "doc-1",
                page = 4,
                viewportOffsetX = -42f,
                viewportOffsetY = 320f,
                viewportScale = 1.75f,
                pointerX = 0.2f,
                pointerY = 0.8f,
                viewportCenterX = 0.6f,
                viewportCenterY = 4.72f,
                toolMode = ToolMode.ERASER,
            )

        val decoded = json.decodeFromString<NetworkMessage>(json.encodeToString(frame))

        val projection = assertIs<NetworkMessage.ProjectionFrame>(decoded)
        assertEquals("doc-1", projection.documentId)
        assertEquals(4, projection.page)
        assertEquals(-42f, projection.viewportOffsetX)
        assertEquals(320f, projection.viewportOffsetY)
        assertEquals(1.75f, projection.viewportScale)
        assertEquals(0.2f, projection.pointerX)
        assertEquals(0.8f, projection.pointerY)
        assertEquals(0.6f, projection.viewportCenterX)
        assertEquals(4.72f, projection.viewportCenterY)
        assertEquals(ToolMode.ERASER, projection.toolMode)
    }
}
