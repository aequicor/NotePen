package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlMaterial
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
                profile = BookCurlProfile.Low,
            )

        assertTrue(mesh.vertices2d.all { it.isFinite() }, "2D mesh must stay finite")
        assertTrue(mesh.vertices3d.all { it.isFinite() }, "3D mesh must stay finite")
        assertTrue(mesh.light.all { it.isFinite() }, "lighting must stay finite")
    }

    @Test
    fun stiffCurlLiftsTowardFreeEdgeUniformlyAcrossRows() {
        // Без провисания (невесомый лист): подъём одинаков во всех строках.
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 100f, progress = 0.5f, velocityX = -1800f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
                material = BookCurlMaterial(weight = 0f),
            )

        // Подъём + загиб: корешок (s=0) — ось, остаётся на поверхности; к свободному краю лист
        // поднимается всё выше (сначала жёстким наклоном, затем загибом).
        val spinePivot = mesh.liftAt(row = mesh.rows / 2, col = 0)
        val freeEdgeLift = mesh.liftAt(row = mesh.rows / 2, col = mesh.columns)
        assertEquals(0f, spinePivot, "the spine pivot (s=0) must stay on the surface")
        assertTrue(freeEdgeLift > spinePivot + 1f, "the sheet must lift toward the free edge")

        // Без веса загиб ровный по высоте: одинаковый подъём во всех строках.
        val edgeTop = mesh.liftAt(row = 1, col = mesh.columns)
        val edgeBottom = mesh.liftAt(row = mesh.rows - 1, col = mesh.columns)
        assertTrue(
            abs(edgeTop - edgeBottom) < freeEdgeLift * 0.05f + 0.5f,
            "weightless sheet must lift every row equally: top=$edgeTop bottom=$edgeBottom",
        )
    }

    @Test
    fun heavySheetSagsAwayFromGrip() {
        // Захват у нижнего края: тяжёлый мягкий лист провисает вверху (дальше от захвата).
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 1100f, progress = 0.4f, velocityX = -1800f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
                material = BookCurlMaterial(weight = 0.9f, stiffness = 0.3f),
            )

        val nearGrip = mesh.liftAt(row = mesh.rows - 1, col = mesh.columns)
        val farFromGrip = mesh.liftAt(row = 1, col = mesh.columns)
        assertTrue(
            nearGrip > farFromGrip * 1.25f,
            "heavy sheet must sag away from the grip: nearGrip=$nearGrip far=$farFromGrip",
        )
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
        // Окно 1000, страница контента 300: корешок по центру (500), свободный край у края контента.
        val forward = bookCurlSheetGeometry(fullWidth = 1000, pageWidth = 300, direction = 1, twoPageSpread = true)
        val backward = bookCurlSheetGeometry(fullWidth = 1000, pageWidth = 300, direction = -1, twoPageSpread = true)
        val single = bookCurlSheetGeometry(fullWidth = 1000, pageWidth = 300, direction = 1, twoPageSpread = false)

        assertEquals(500, forward.sourceX)
        assertEquals(300, forward.width)
        assertEquals(500f, forward.offsetX)
        assertEquals(200, backward.sourceX)
        assertEquals(300, backward.width)
        assertEquals(200f, backward.offsetX)
        // Одиночная страница центрируется в окне: (1000-300)/2 = 350.
        assertEquals(350, single.sourceX)
        assertEquals(300, single.width)
        assertEquals(350f, single.offsetX)
    }

    @Test
    fun twoPageSpreadPlacesTargetHalfUnderTurnedSheetSlot() {
        val forward = bookCurlUnderPageGeometry(fullWidth = 1000, pageWidth = 300, direction = 1, twoPageSpread = true)
        val backward = bookCurlUnderPageGeometry(fullWidth = 1000, pageWidth = 300, direction = -1, twoPageSpread = true)
        val single = bookCurlUnderPageGeometry(fullWidth = 1000, pageWidth = 300, direction = 1, twoPageSpread = false)

        assertEquals(500, forward.sourceX)
        assertEquals(300, forward.width)
        assertEquals(500f, forward.offsetX)
        assertEquals(200, backward.sourceX)
        assertEquals(300, backward.width)
        assertEquals(200f, backward.offsetX)
        assertEquals(350, single.sourceX)
        assertEquals(300, single.width)
        assertEquals(350f, single.offsetX)
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
