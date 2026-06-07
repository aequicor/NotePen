package ru.kyamshanov.notepen.reflow.ui

import ru.kyamshanov.notepen.reflow.api.BookCurlMaterialId
import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlDerived
import ru.kyamshanov.notepen.reflow.ui.bookcurl.BookCurlMaterial
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
    fun spinePivotStaysOnSurfaceAndLiftsTowardFreeEdge() {
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 600f, progress = 0.5f, velocityX = -1800f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
            )

        // Корешок (s=0) — ось, остаётся на поверхности (z=0); к свободному краю лист поднимается.
        val spinePivot = mesh.liftAt(row = mesh.rows / 2, col = 0)
        val freeEdgeLift = mesh.liftAt(row = mesh.rows / 2, col = mesh.columns)
        assertEquals(0f, spinePivot, "the spine pivot (s=0) must stay on the surface")
        assertTrue(freeEdgeLift > spinePivot + 1f, "the sheet must lift toward the free edge")
    }

    @Test
    fun inextensibleAcrossRows() {
        // #2 (ПОПЕРЁК строк): соседние по вертикали вершины отстоят на ~dy — лист не растягивается между
        // строками. Это главный признак «резины»: если каждую строку гнуть независимо, поперечная
        // дистанция раздувается. Берём самый мягкий пресет и захват не по центру — худший случай.
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 300f, progress = 0.5f, velocityX = -1800f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
                material = BookCurlMaterial.of(BookCurlMaterialId.NEWSPRINT),
            )
        val dy = 1200f / mesh.rows
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
        assertTrue(worst < 0.02f, "across-row edges must stay ~inextensible: worst=$worst")
    }

    @Test
    fun contactClampKeepsSheetAboveTheBook() {
        // Контакт #5: ни одна вершина не уходит под книгу (z >= 0), даже при сильной гравитации.
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 1100f, progress = 0.6f, velocityX = -1800f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
                material = BookCurlMaterial.of(BookCurlMaterialId.NEWSPRINT),
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
    fun stifferMaterialCurlsWithLargerRadius() {
        // #1: радиус завитка растёт с жёсткостью EI — картон мягче офиса, офис мягче газеты.
        val office = BookCurlDerived(BookCurlMaterial.of(BookCurlMaterialId.OFFICE), 800f)
        val cardboard = BookCurlDerived(BookCurlMaterial.of(BookCurlMaterialId.CARDBOARD), 800f)
        val newsprint = BookCurlDerived(BookCurlMaterial.of(BookCurlMaterialId.NEWSPRINT), 800f)
        assertTrue(cardboard.rCurl > office.rCurl, "cardboard must curl softer than office")
        assertTrue(office.rCurl > newsprint.rCurl, "office must curl softer than newsprint")
    }

    @Test
    fun inextensibleMarchPreservesEdgeLength() {
        // #2: марш равными шагами длины дуги — соседние вдоль изгиба вершины отстоят на ~ds (3D).
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
    fun tiltedAxisStaysInextensibleBothWays() {
        // Диагональный хват (палец ушёл вниз) ⇒ ось сгиба НАКЛОННАЯ; развёртка всё ещё изометрия —
        // и вдоль строки, и поперёк соседи сохраняют расстояние (наклон не растягивает лист = не «резина»).
        val w = 800f
        val h = 1200f
        val mesh =
            BookCurlPhysics.mesh(
                state =
                    BookCurlState(
                        direction = 1,
                        gripY = 360f,
                        fingerX = w * 0.5f,
                        fingerY = 760f,
                        velocityX = -1800f,
                        velocityY = 0f,
                        progress = 0.5f,
                        phase = BookCurlPhase.Dragging,
                    ),
                widthPx = w,
                heightPx = h,
                profile = BookCurlProfile.Low,
                material = BookCurlMaterial.of(BookCurlMaterialId.BOOK),
            )

        fun dist(
            ra: Int,
            ca: Int,
            rb: Int,
            cb: Int,
        ): Float {
            val a = (ra * (mesh.columns + 1) + ca) * 3
            val b = (rb * (mesh.columns + 1) + cb) * 3
            val dx = mesh.vertices3d[b] - mesh.vertices3d[a]
            val dy = mesh.vertices3d[b + 1] - mesh.vertices3d[a + 1]
            val dz = mesh.vertices3d[b + 2] - mesh.vertices3d[a + 2]
            return sqrt(dx * dx + dy * dy + dz * dz)
        }
        val ds = w / mesh.columns
        val dyStep = h / mesh.rows
        var worstRow = 0f
        var worstCol = 0f
        for (row in 0..mesh.rows) {
            for (col in 0 until mesh.columns) worstRow = maxOf(worstRow, abs(dist(row, col, row, col + 1) - ds) / ds)
        }
        for (col in 0..mesh.columns) {
            for (row in 0 until mesh.rows) worstCol = maxOf(worstCol, abs(dist(row, col, row + 1, col) - dyStep) / dyStep)
        }
        assertTrue(worstRow < 0.03f, "along-row must stay ~inextensible under a tilted axis: worst=$worstRow")
        assertTrue(worstCol < 0.03f, "across-row must stay ~inextensible under a tilted axis: worst=$worstCol")
    }

    @Test
    fun spineStaysPinnedAcrossProgressAndDirections() {
        // R1 (нельзя вырвать): корешок (x=0, col 0) ВСЕГДА на книге (z=0) — при любом прогрессе,
        // направлении и положении пальца. Лист — заслонка на шарнире у корешка, оторвать нельзя.
        for (dir in intArrayOf(1, -1)) {
            for (p in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val mesh =
                    BookCurlPhysics.mesh(
                        state =
                            BookCurlState(
                                direction = dir,
                                gripY = 700f,
                                fingerX = 200f,
                                fingerY = 900f,
                                velocityX = -1800f,
                                velocityY = 0f,
                                progress = p,
                                phase = BookCurlPhase.Dragging,
                            ),
                        widthPx = 800f,
                        heightPx = 1200f,
                        profile = BookCurlProfile.Low,
                    )
                // Сгиб едет от свободного края к корешку; корешок — col 0 при dir>0 и col=columns при dir<0.
                val spineCol = if (dir > 0) 0 else mesh.columns
                for (row in 0..mesh.rows) {
                    val z = mesh.vertices3d[(row * (mesh.columns + 1) + spineCol) * 3 + 2]
                    assertEquals(0f, z, 1e-3f, "spine column must stay pinned (z=0): dir=$dir p=$p row=$row")
                }
            }
        }
    }

    @Test
    fun completesTheTurnShowingBackFace() {
        // R2: на прогрессе 1 лист ПЕРЕВЁРНУТ — большая часть смотрит изнанкой (facing<0 = следующая
        // страница), то есть переворот доведён до конца, а не застрял на полпути.
        val mesh =
            BookCurlPhysics.mesh(
                state = state(gripY = 600f, progress = 1f, velocityX = 0f),
                widthPx = 800f,
                heightPx = 1200f,
                profile = BookCurlProfile.Low,
            )
        val back = mesh.facing.count { it < 0f }
        assertTrue(
            back > mesh.facing.size / 2,
            "completed turn must show the back face over most of the sheet: back=$back of ${mesh.facing.size}",
        )
    }

    @Test
    fun liesFlatAtFullTurn() {
        // C2: на прогрессе 1 конус «укладывает» лист плашмя — почти все вершины у книги (z мал), а не
        // висят на ~2*radius. Радиус схлопывается + панель доворачивается за π, клэмп кладёт на стол.
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
        // C3: на полпути лист НЕ встаёт ребром к камере (верх-вниз) — горизонтальный размах большой.
        val w = 800f
        val mesh =
            BookCurlPhysics.mesh(state(gripY = 600f, progress = 0.5f, velocityX = -1800f), w, 1200f, BookCurlProfile.Low)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var i = 0
        while (i < mesh.vertices3d.size) {
            val x = mesh.vertices3d[i]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            i += 3
        }
        assertTrue(maxX - minX > 0.30f * w, "mid-turn page must not collapse edge-on: span=${maxX - minX}")
    }

    @Test
    fun gripPositionChangesShape() {
        // C5: форма зависит от захвата — угловой захват пилит асимметрично (dog-ear), центральный симметрично.
        val w = 800f
        val h = 1200f
        fun edgeAsymmetry(gripY: Float): Float {
            val mesh =
                BookCurlPhysics.mesh(
                    state = state(gripY = gripY, progress = 0.5f, velocityX = -1800f),
                    widthPx = w,
                    heightPx = h,
                    profile = BookCurlProfile.Low,
                    material = BookCurlMaterial.of(BookCurlMaterialId.NEWSPRINT),
                )
            val top = mesh.liftAt(row = 1, col = mesh.columns)
            val bottom = mesh.liftAt(row = mesh.rows - 1, col = mesh.columns)
            return abs(top - bottom) / maxOf(top, bottom, 1f)
        }
        assertTrue(edgeAsymmetry(120f) > 0.05f, "corner grip must peel asymmetrically (dog-ear)")
        assertTrue(edgeAsymmetry(h / 2f) < 0.02f, "mid-edge grip must stay symmetric")
    }

    @Test
    fun cornerGripStaysInextensibleAcrossRows() {
        // C4 (опасная полоса): угловой захват + средне-высокий прогресс не должен растягивать поперёк —
        // конусный угол спадает (coneFade) до того, как дуга станет широкой. Guard на регрессию.
        val w = 800f
        val h = 1200f
        val dy = h / BookCurlProfile.Low.rows
        for (p in floatArrayOf(0.6f, 0.7f, 0.8f)) {
            val mesh =
                BookCurlPhysics.mesh(
                    state = state(gripY = 0f, progress = p, velocityX = -1800f),
                    widthPx = w,
                    heightPx = h,
                    profile = BookCurlProfile.Low,
                    material = BookCurlMaterial.of(BookCurlMaterialId.NEWSPRINT),
                )
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
            assertTrue(worst < 0.02f, "corner grip must stay ~inextensible across rows at p=$p: worst=$worst")
        }
    }

    @Test
    fun backFaceShowsNextPageNotTwoAhead() {
        // #3: в развороте изнанка кропится из ПРОТИВОПОЛОЖНОЙ половины следующего разворота (настоящая
        // следующая страница), а не из той же колонки, что лицо (иначе показывали бы страницу через одну).
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
