package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlMesh
import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlPhase
import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlPhysics
import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlProfile
import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlState
import ru.kyamshanov.notepen.reflow.ui.bookcurl.bookCurlBackFaceGeometry
import ru.kyamshanov.notepen.reflow.ui.bookcurl.bookCurlMeshBuffers
import ru.kyamshanov.notepen.reflow.ui.bookcurl.bookCurlSheetGeometry
import ru.kyamshanov.notepen.reflow.ui.bookcurl.bookCurlUnderPageGeometry
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookCurlPhysicsTest {
    @Test
    fun meshHasFiniteCoordinatesForViolentDrag() {
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 140f, progress = 0.73f, velocityX = -6400f, fingerY = 1100f),
                widthPx = 900f,
                heightPx = 1300f,
                profile = BookCurlProfile.Low,
            )

        assertTrue(mesh.vertices2d.all { it.isFinite() }, "2D mesh must stay finite")
        assertTrue(mesh.vertices3d.all { it.isFinite() }, "3D mesh must stay finite")
        assertTrue(mesh.light.all { it.isFinite() }, "lighting must stay finite")
    }

    @Test
    fun contactClampKeepsSheetAboveTheBook() {
        // Контакт: ни одна вершина не уходит под книгу (z >= 0) при любом прогрессе/наклоне.
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 1100f, progress = 0.6f, velocityX = -1800f, fingerY = 200f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
            )

        var minZ = Float.MAX_VALUE
        var i = 2
        while (i < mesh.vertices3d.size) {
            if (mesh.vertices3d[i] < minZ) minZ = mesh.vertices3d[i]
            i += 3
        }
        assertTrue(minZ >= 0f, "no vertex may sink below the book (z >= 0): minZ=$minZ")
    }

    @Test
    fun inextensibleMarchPreservesEdgeLength() {
        // Вдоль изгиба соседние по строке вершины отстоят на ~ds (развёртка цилиндра — изометрия).
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 600f, progress = 0.3f, velocityX = -1800f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
            )
        val ds = 800f / mesh.columns
        val row = mesh.rows / 2
        var worst = 0f
        for (col in 0 until mesh.columns) {
            val a = (row * (mesh.columns + 1) + col) * 3
            val b = (row * (mesh.columns + 1) + col + 1) * 3
            val dx = mesh.vertices3d[b] - mesh.vertices3d[a]
            val dz = mesh.vertices3d[b + 2] - mesh.vertices3d[a + 2]
            worst = maxOf(worst, abs(sqrt(dx * dx + dz * dz) - ds) / ds)
        }
        assertTrue(worst < 0.02f, "along-bend edges must stay ~inextensible: worst=$worst")
    }

    @Test
    fun inextensibleAcrossRows() {
        // Поперёк строк без наклона (палец на линии захвата) — соседи по вертикали ровно на dy.
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 300f, progress = 0.5f, velocityX = -1800f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
            )
        assertTrue(worstAcrossRow(mesh, 1200f) < 0.01f, "across-row edges must stay ~inextensible without tilt")
    }

    @Test
    fun tiltedFoldStaysInextensibleBothWays() {
        // Диагональный хват (палец далеко от захвата) ⇒ наклонная линия сгиба; ограниченный наклон
        // (TILT_GAIN) держит изометрию и вдоль, и поперёк строк (наклон не растягивает лист = не «резина»).
        val w = 800f
        val h = 1200f
        val mesh =
            BookCurlPhysics.mesh(
                state =
                    BookCurlState(
                        direction = 1,
                        gripY = 360f,
                        fingerY = 1100f,
                        velocityX = -1800f,
                        progress = 0.5f,
                        phase = BookCurlPhase.Dragging,
                    ),
                widthPx = w,
                heightPx = h,
                profile = BookCurlProfile.Low,
            )

        val ds = w / mesh.columns
        var worstRow = 0f
        for (row in 0..mesh.rows) {
            for (col in 0 until mesh.columns) {
                val a = (row * (mesh.columns + 1) + col) * 3
                val b = (row * (mesh.columns + 1) + col + 1) * 3
                val dx = mesh.vertices3d[b] - mesh.vertices3d[a]
                val dy = mesh.vertices3d[b + 1] - mesh.vertices3d[a + 1]
                val dz = mesh.vertices3d[b + 2] - mesh.vertices3d[a + 2]
                worstRow = maxOf(worstRow, abs(sqrt(dx * dx + dy * dy + dz * dz) - ds) / ds)
            }
        }
        assertTrue(worstRow < 0.03f, "along-row must stay ~inextensible under a tilted fold: worst=$worstRow")
        assertTrue(worstAcrossRow(mesh, h) < 0.03f, "across-row must stay ~inextensible under a tilted fold")
    }

    @Test
    fun spineStaysPinnedAcrossProgressDirectionsAndTilt() {
        // Анти-разрыв: вся колонка корешка (s=0) ВСЕГДА на книге (z=0) и на месте (x=корешок) — при любом
        // прогрессе, направлении и наклоне. Построчный кламп foldS=max(0,…) держит s=0 в плоской зоне.
        val w = 800f
        val h = 1200f
        for (dir in intArrayOf(1, -1)) {
            for (fingerY in floatArrayOf(0f, 600f, 1200f)) {
                for (p in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                    val mesh =
                        BookCurlPhysics.mesh(
                            state =
                                BookCurlState(
                                    direction = dir,
                                    gripY = 700f,
                                    fingerY = fingerY,
                                    velocityX = -1800f,
                                    progress = p,
                                    phase = BookCurlPhase.Dragging,
                                ),
                            widthPx = w,
                            heightPx = h,
                            profile = BookCurlProfile.Low,
                        )
                    val spineCol = if (dir > 0) 0 else mesh.columns
                    val spineX = if (dir > 0) 0f else w
                    for (row in 0..mesh.rows) {
                        val v = row * (mesh.columns + 1) + spineCol
                        assertEquals(0f, mesh.vertices3d[v * 3 + 2], 1e-3f, "spine z=0: dir=$dir fingerY=$fingerY p=$p")
                        assertEquals(spineX, mesh.vertices2d[v * 2], 1e-2f, "spine x pinned: dir=$dir fingerY=$fingerY p=$p")
                    }
                }
            }
        }
    }

    @Test
    fun completesTheTurnShowingBackFace() {
        // На прогрессе 1 лист перевёрнут — большинство граней смотрит изнанкой (facing<0 = следующая страница).
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 600f, progress = 1f, velocityX = 0f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
            )
        val back = mesh.facing.count { it < 0f }
        assertTrue(back > mesh.facing.size / 2, "completed turn must show the back face: back=$back of ${mesh.facing.size}")
    }

    @Test
    fun liesFlatAtFullTurn() {
        // На прогрессе 1 радиус схлопнут (r→0) ⇒ почти все вершины у книги (z мал), лист лёг плашмя.
        val w = 800f
        val mesh = BookCurlPhysics.mesh(state(gripY = 600f, progress = 1f, velocityX = 0f), w, 1200f, BookCurlProfile.Low)
        var flat = 0
        var i = 2
        while (i < mesh.vertices3d.size) {
            if (abs(mesh.vertices3d[i]) < 0.05f * w) flat++
            i += 3
        }
        val total = mesh.vertices3d.size / 3
        assertTrue(flat > total * 9 / 10, "turned page must lie flat at full turn: flat=$flat/$total")
        assertTrue(mesh.maxLiftPx < 0.10f * w, "maxLift must be small at full turn: ${mesh.maxLiftPx}")
    }

    @Test
    fun notEdgeOnAtMidTurn() {
        // На полпути экранный размах по x велик — лист НЕ встаёт ребром к камере (фронтальная проекция).
        val w = 800f
        val mesh =
            BookCurlPhysics.mesh(state(gripY = 600f, progress = 0.5f, velocityX = -1800f), w, 1200f, BookCurlProfile.Low)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var i = 0
        while (i < mesh.vertices2d.size) {
            val x = mesh.vertices2d[i]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            i += 2
        }
        assertTrue(maxX - minX > 0.30f * w, "mid-turn page must not collapse edge-on: span=${maxX - minX}")
    }

    @Test
    fun spinePivotStaysOnSurfaceAndLiftsTowardFreeEdge() {
        // Корешок (s=0) на поверхности (z=0); свободная половина листа приподнята (z>0).
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 600f, progress = 0.5f, velocityX = -1800f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
            )
        val spinePivot = mesh.liftAt(row = mesh.rows / 2, col = 0)
        val freeEdgeLift = mesh.liftAt(row = mesh.rows / 2, col = mesh.columns)
        assertEquals(0f, spinePivot, "the spine pivot (s=0) must stay on the surface")
        assertTrue(freeEdgeLift > spinePivot + 1f, "the free half of the sheet must lift")
    }

    @Test
    fun foldFollowsFingerAtGripRow() {
        // Anchor-follows-finger: у строки захвата линия сгиба (граница плоской зоны) = w·(1−progress).
        val w = 800f
        val h = 1200f
        for (p in floatArrayOf(0.2f, 0.5f, 0.8f)) {
            val mesh = BookCurlPhysics.mesh(state(gripY = h * 0.5f, progress = p, velocityX = 0f), w, h, BookCurlProfile.Low)
            val row = mesh.rows / 2
            var foldX = 0f
            for (col in 0..mesh.columns) {
                val v = row * (mesh.columns + 1) + col
                if (mesh.vertices3d[v * 3 + 2] <= 0.01f) foldX = mesh.vertices3d[v * 3]
            }
            val expected = w * (1f - p)
            assertTrue(
                abs(foldX - expected) <= w / mesh.columns + 1f,
                "fold at grip row must follow finger: foldX=$foldX expected=$expected p=$p",
            )
        }
    }

    @Test
    fun diagonalFingerTiltsTheFold() {
        // Косой свайп (палец ≠ захвату) наклоняет линию сгиба ⇒ верх/низ свободного края поднимаются
        // по-разному (диагональный dog-ear). Прямой свайп (палец на линии захвата) — симметрично.
        val w = 800f
        val h = 1200f

        fun edgeAsymmetry(fingerY: Float): Float {
            val mesh =
                BookCurlPhysics.mesh(
                    state = state(gripY = 600f, progress = 0.15f, velocityX = -1800f, fingerY = fingerY),
                    widthPx = w,
                    heightPx = h,
                    profile = BookCurlProfile.Low,
                )
            val top = mesh.liftAt(row = 1, col = mesh.columns)
            val bottom = mesh.liftAt(row = mesh.rows - 1, col = mesh.columns)
            return abs(top - bottom) / maxOf(top, bottom, 1f)
        }
        assertTrue(edgeAsymmetry(fingerY = 1050f) > 0.05f, "diagonal drag must tilt the fold (asymmetric dog-ear)")
        assertTrue(edgeAsymmetry(fingerY = 600f) < 0.02f, "straight drag must stay symmetric")
    }

    @Test
    fun underTurnedPageDoesNotComplete() {
        assertFalse(BookCurlPhysics.shouldComplete(state(progress = 0.31f, velocityX = -200f)))
    }

    @Test
    fun committedPageCompletesByProgressOrFling() {
        assertTrue(BookCurlPhysics.shouldComplete(state(progress = 0.62f, velocityX = 0f)))
        assertTrue(BookCurlPhysics.shouldComplete(state(progress = 0.2f, velocityX = -2200f)))
    }

    @Test
    fun backFaceShowsNextPageNotTwoAhead() {
        // В развороте изнанка кропится из ПРОТИВОПОЛОЖНОЙ половины следующего разворота (настоящая
        // следующая страница), а не из той же колонки, что лицо.
        val forward = bookCurlBackFaceGeometry(fullWidth = 1000, pageWidth = 300, direction = 1, twoPageSpread = true)
        val backward = bookCurlBackFaceGeometry(fullWidth = 1000, pageWidth = 300, direction = -1, twoPageSpread = true)
        val single = bookCurlBackFaceGeometry(fullWidth = 1000, pageWidth = 300, direction = 1, twoPageSpread = false)
        val frontForward = bookCurlSheetGeometry(fullWidth = 1000, pageWidth = 300, direction = 1, twoPageSpread = true)

        assertEquals(200, forward.sourceX)
        assertEquals(300, forward.width)
        assertEquals(500, backward.sourceX)
        assertEquals(350, single.sourceX)
        assertTrue(forward.sourceX != frontForward.sourceX, "back-face must be the OPPOSITE half of the front sheet")
    }

    @Test
    fun twoPageSpreadUsesSpineAsSheetHinge() {
        val forward = bookCurlSheetGeometry(fullWidth = 1000, pageWidth = 300, direction = 1, twoPageSpread = true)
        val backward = bookCurlSheetGeometry(fullWidth = 1000, pageWidth = 300, direction = -1, twoPageSpread = true)
        val single = bookCurlSheetGeometry(fullWidth = 1000, pageWidth = 300, direction = 1, twoPageSpread = false)

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

    private fun worstAcrossRow(
        mesh: BookCurlMesh,
        heightPx: Float,
    ): Float {
        val dy = heightPx / mesh.rows
        var worst = 0f
        for (col in 0..mesh.columns) {
            for (row in 0 until mesh.rows) {
                val a = (row * (mesh.columns + 1) + col) * 3
                val b = ((row + 1) * (mesh.columns + 1) + col) * 3
                val dx = mesh.vertices3d[b] - mesh.vertices3d[a]
                val dvy = mesh.vertices3d[b + 1] - mesh.vertices3d[a + 1]
                val dz = mesh.vertices3d[b + 2] - mesh.vertices3d[a + 2]
                worst = maxOf(worst, abs(sqrt(dx * dx + dvy * dvy + dz * dz) - dy) / dy)
            }
        }
        return worst
    }

    private fun state(
        gripY: Float = 600f,
        progress: Float,
        velocityX: Float,
        fingerY: Float = gripY,
        phase: BookCurlPhase = BookCurlPhase.Dragging,
    ): BookCurlState =
        BookCurlState(
            direction = 1,
            gripY = gripY,
            fingerY = fingerY,
            velocityX = velocityX,
            progress = progress,
            phase = phase,
        )

    private fun BookCurlMesh.liftAt(
        row: Int,
        col: Int,
    ): Float {
        val index = (row.coerceIn(0, rows) * (columns + 1) + col.coerceIn(0, columns)) * 3 + 2
        return abs(vertices3d[index])
    }
}
