package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlPhase
import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlPhysics
import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlProfile
import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlState
import ru.kyamshanov.notepen.reflow.ui.bookcurl.bookCurlMeshBuffers
import ru.kyamshanov.notepen.reflow.ui.bookcurl.bookCurlSheetGeometry
import ru.kyamshanov.notepen.reflow.ui.bookcurl.bookCurlUnderPageGeometry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookCurlPhysicsTest {
    @Test
    fun meshHasFiniteCoordinatesForViolentDrag() {
        val mesh =
            BookCurlPhysics.mesh(
                state =
                    state(
                        gripY = 140f,
                        progress = 0.73f,
                        velocityX = -6400f,
                        velocityY = 2100f,
                    ),
                widthPx = 900f,
                heightPx = 1300f,
                elapsedSeconds = 2.4f,
                profile = BookCurlProfile.Low,
            )

        assertTrue(mesh.vertices2d.all { it.isFinite() }, "2D mesh must stay finite")
        assertTrue(mesh.vertices3d.all { it.isFinite() }, "3D mesh must stay finite")
        assertTrue(mesh.light.all { it.isFinite() }, "lighting must stay finite")
    }

    @Test
    fun gripYBendsNearbyRowsBeforeFarRows() {
        val topGrip =
            BookCurlPhysics.mesh(
                state = state(gripY = 100f, progress = 0.22f, velocityX = -1800f),
                widthPx = 800f,
                heightPx = 1200f,
                elapsedSeconds = 0.1f,
                profile = BookCurlProfile.Low,
            )

        val nearTopLift = topGrip.liftAt(row = 2, col = topGrip.columns)
        val farBottomLift = topGrip.liftAt(row = topGrip.rows - 2, col = topGrip.columns)

        assertTrue(nearTopLift > farBottomLift * 2f, "curl must start near the original grip")
    }

    @Test
    fun underTurnedPageReturnsInsteadOfCompleting() {
        val state = state(progress = 0.31f, velocityX = -200f)

        assertFalse(BookCurlPhysics.shouldComplete(state))

        val next =
            BookCurlPhysics.settleProgress(
                current = state.progress,
                target = 0f,
                velocity = state.velocityX,
                dtSeconds = BookCurlPhysics.FIXED_STEP_SECONDS,
            )
        assertTrue(next < state.progress, "return spring must move progress back toward zero")
    }

    @Test
    fun committedPageCompletesByProgressOrFling() {
        assertTrue(BookCurlPhysics.shouldComplete(state(progress = 0.62f, velocityX = 0f)))
        assertTrue(BookCurlPhysics.shouldComplete(state(progress = 0.2f, velocityX = -2200f)))
    }

    @Test
    fun windDecaysAfterRelease() {
        val dragging = state(progress = 0.35f, velocityX = -2400f, phase = BookCurlPhase.Dragging)
        val completing = dragging.copy(progress = 0.86f, phase = BookCurlPhase.Completing)

        assertTrue(
            BookCurlPhysics.windStrength(completing, completing.progress) <
                BookCurlPhysics.windStrength(dragging, dragging.progress),
            "gesture wind must decay as released page settles",
        )
    }

    @Test
    fun staticBuffersMatchMeshTopology() {
        val buffers =
            bookCurlMeshBuffers(
                columns = BookCurlProfile.Low.columns,
                rows = BookCurlProfile.Low.rows,
                widthPx = 800f,
                heightPx = 1200f,
            )
        val vertexCount = (BookCurlProfile.Low.columns + 1) * (BookCurlProfile.Low.rows + 1)

        assertEquals(vertexCount, buffers.vertexCount)
        assertEquals(vertexCount * 2, buffers.textureCoordinates.size)
        assertEquals(BookCurlProfile.Low.columns * BookCurlProfile.Low.rows * 6, buffers.triangleIndices.size)
        assertTrue(buffers.triangleIndices.all { it >= 0 && it < vertexCount })
    }

    @Test
    fun twoPageSpreadUsesSpineAsSheetHinge() {
        val forward = bookCurlSheetGeometry(fullWidth = 1000, direction = 1, twoPageSpread = true)
        val backward = bookCurlSheetGeometry(fullWidth = 1000, direction = -1, twoPageSpread = true)
        val single = bookCurlSheetGeometry(fullWidth = 1000, direction = 1, twoPageSpread = false)

        assertEquals(500, forward.sourceX)
        assertEquals(500, forward.width)
        assertEquals(500f, forward.offsetX)
        assertEquals(0, backward.sourceX)
        assertEquals(500, backward.width)
        assertEquals(0f, backward.offsetX)
        assertEquals(0, single.sourceX)
        assertEquals(1000, single.width)
        assertEquals(0f, single.offsetX)
    }

    @Test
    fun twoPageSpreadPlacesTargetHalfUnderTurnedSheetSlot() {
        val forward = bookCurlUnderPageGeometry(fullWidth = 1000, direction = 1, twoPageSpread = true)
        val backward = bookCurlUnderPageGeometry(fullWidth = 1000, direction = -1, twoPageSpread = true)
        val single = bookCurlUnderPageGeometry(fullWidth = 1000, direction = 1, twoPageSpread = false)

        assertEquals(500, forward.sourceX)
        assertEquals(500, forward.width)
        assertEquals(500f, forward.offsetX)
        assertEquals(0, backward.sourceX)
        assertEquals(500, backward.width)
        assertEquals(0f, backward.offsetX)
        assertEquals(0, single.sourceX)
        assertEquals(1000, single.width)
        assertEquals(0f, single.offsetX)
    }

    private fun state(
        gripY: Float = 600f,
        progress: Float,
        velocityX: Float,
        velocityY: Float = 0f,
        phase: BookCurlPhase = BookCurlPhase.Dragging,
    ): BookCurlState =
        BookCurlState(
            direction = 1,
            gripY = gripY,
            fingerX = 0f,
            fingerY = gripY,
            velocityX = velocityX,
            velocityY = velocityY,
            progress = progress,
            phase = phase,
        )

    private fun ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlMesh.liftAt(
        row: Int,
        col: Int,
    ): Float {
        val index = (row.coerceIn(0, rows) * (columns + 1) + col.coerceIn(0, columns)) * 3 + 2
        return abs(vertices3d[index])
    }
}
