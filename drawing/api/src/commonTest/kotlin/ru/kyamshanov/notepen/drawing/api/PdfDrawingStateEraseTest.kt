package ru.kyamshanov.notepen.drawing.api

import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [PdfDrawingState.erasePointsInZone] — Step 2 of pdf-eraser-tool-settings.
 *
 * Covers TC-3..TC-9 (EC-3..EC-7, AC-10/AC-13 metric).
 *
 * Coordinate system: normalized [0..1] relative to canvas.
 */
class PdfDrawingStateEraseTest {
    private fun newStateWith(vararg paths: DrawingPath): PdfDrawingState {
        val s = PdfDrawingState()
        s.currentPaths.addAll(paths)
        return s
    }

    private fun straightLineX(
        y: Float,
        xs: List<Float>,
        colorArgb: Long = 0xFFFF0000L,
        stroke: Float = 7f,
    ): DrawingPath {
        val pts = xs.mapIndexed { idx, x -> DrawingPoint(x, y, isNewPath = idx == 0) }
        return DrawingPath(points = pts, colorArgb = colorArgb, strokeWidth = stroke)
    }

    // TC-3 / EC-3: middle erase splits into two sub-strokes; first point of each has isNewPath=true
    @Test
    fun erasePointsInZone_middleErase_splitsIntoTwoSubpaths() {
        val path =
            straightLineX(
                y = 0.5f,
                xs = listOf(0.10f, 0.20f, 0.30f, 0.50f, 0.70f, 0.80f, 0.90f),
            )
        val state = newStateWith(path)

        // CIRCLE zone at (0.50, 0.50) radius 0.05 — kills only x=0.50.
        val changed =
            state.erasePointsInZone(
                centerX = 0.50f,
                centerY = 0.50f,
                halfSizeNormalized = 0.05f,
                shape = EraserShape.CIRCLE,
            )

        assertTrue(changed, "erase must report change")
        assertEquals(2, state.currentPaths.size, "expected 2 sub-strokes after middle erase")

        val left = state.currentPaths[0]
        val right = state.currentPaths[1]
        assertEquals(listOf(0.10f, 0.20f, 0.30f), left.points.map { it.x })
        assertEquals(listOf(0.70f, 0.80f, 0.90f), right.points.map { it.x })
        assertTrue(left.points.first().isNewPath, "left sub-stroke first point must be isNewPath=true")
        assertTrue(right.points.first().isNewPath, "right sub-stroke first point must be isNewPath=true")
        assertFalse(left.points[1].isNewPath, "non-first point isNewPath must be false")
        assertFalse(right.points[1].isNewPath, "non-first point isNewPath must be false")
    }

    // TC-4 / EC-4: erase at the start / end shortens, does NOT split
    @Test
    fun erasePointsInZone_eraseAtStart_shortensWithoutSplit() {
        val path =
            straightLineX(
                y = 0.5f,
                xs = listOf(0.10f, 0.20f, 0.30f, 0.50f, 0.70f),
            )
        val state = newStateWith(path)

        // Zone covers x ∈ [0.05, 0.25].
        state.erasePointsInZone(
            centerX = 0.15f,
            centerY = 0.50f,
            halfSizeNormalized = 0.10f,
            shape = EraserShape.SQUARE,
        )

        assertEquals(1, state.currentPaths.size, "no split when erasing at start")
        assertEquals(listOf(0.30f, 0.50f, 0.70f), state.currentPaths[0].points.map { it.x })
        assertTrue(
            state.currentPaths[0]
                .points
                .first()
                .isNewPath,
        )
    }

    @Test
    fun erasePointsInZone_eraseAtEnd_shortensWithoutSplit() {
        val path =
            straightLineX(
                y = 0.5f,
                xs = listOf(0.10f, 0.30f, 0.50f, 0.80f, 0.90f),
            )
        val state = newStateWith(path)

        // SQUARE zone over x ∈ [0.78, 0.98].
        state.erasePointsInZone(
            centerX = 0.88f,
            centerY = 0.50f,
            halfSizeNormalized = 0.10f,
            shape = EraserShape.SQUARE,
        )

        assertEquals(1, state.currentPaths.size)
        assertEquals(listOf(0.10f, 0.30f, 0.50f), state.currentPaths[0].points.map { it.x })
    }

    // TC-5 / EC-5: zone fully covers stroke → stroke removed entirely
    @Test
    fun erasePointsInZone_zoneCoversWholeStroke_removesStroke() {
        val path = straightLineX(y = 0.5f, xs = listOf(0.45f, 0.50f, 0.55f))
        val state = newStateWith(path)

        val changed =
            state.erasePointsInZone(
                centerX = 0.50f,
                centerY = 0.50f,
                halfSizeNormalized = 0.20f,
                shape = EraserShape.SQUARE,
            )

        assertTrue(changed)
        assertEquals(0, state.currentPaths.size, "stroke fully inside zone must be removed")
    }

    // TC-6 / EC-6: multiple strokes processed independently
    @Test
    fun erasePointsInZone_multipleStrokes_processedIndependently() {
        // Stroke A — entirely outside zone. Stroke B — middle erased (split).
        // Stroke C — fully inside zone (removed).
        val a = straightLineX(y = 0.10f, xs = listOf(0.10f, 0.20f, 0.30f), colorArgb = 0xFFFF0000L, stroke = 5f)
        val b = straightLineX(y = 0.50f, xs = listOf(0.10f, 0.20f, 0.50f, 0.80f, 0.90f), colorArgb = 0xFF0000FFL, stroke = 7f)
        val c = straightLineX(y = 0.50f, xs = listOf(0.49f, 0.50f, 0.51f), colorArgb = 0xFF00FF00L, stroke = 3f)
        val state = newStateWith(a, b, c)

        state.erasePointsInZone(
            centerX = 0.50f,
            centerY = 0.50f,
            halfSizeNormalized = 0.05f,
            shape = EraserShape.CIRCLE,
        )

        // A intact (1) + B split (2) + C removed (0) = 3 paths.
        assertEquals(3, state.currentPaths.size)
        // A first.
        assertEquals(0xFFFF0000L, state.currentPaths[0].colorArgb)
        assertEquals(listOf(0.10f, 0.20f, 0.30f), state.currentPaths[0].points.map { it.x })
        // B halves.
        assertEquals(0xFF0000FFL, state.currentPaths[1].colorArgb)
        assertEquals(0xFF0000FFL, state.currentPaths[2].colorArgb)
        assertEquals(listOf(0.10f, 0.20f), state.currentPaths[1].points.map { it.x })
        assertEquals(listOf(0.80f, 0.90f), state.currentPaths[2].points.map { it.x })
    }

    // TC-7 / EC-7: sub-strokes with < 2 points are dropped
    @Test
    fun erasePointsInZone_subStrokeBelowTwoPoints_dropped() {
        // Erase point #2 of [a, b, c, d] where {a,c} survive but become singletons.
        // Geometry: points at x = 0.10, 0.20, 0.30, 0.40 on y=0.5.
        // Two zones not feasible in one call — instead engineer points so both ends collapse to 1.
        // Use single zone that erases x=0.20 and x=0.30; survivors are [0.10] and [0.40].
        val path = straightLineX(y = 0.5f, xs = listOf(0.10f, 0.20f, 0.30f, 0.40f))
        val state = newStateWith(path)

        state.erasePointsInZone(
            centerX = 0.25f,
            centerY = 0.50f,
            halfSizeNormalized = 0.10f,
            shape = EraserShape.SQUARE,
        )

        // Both surviving sub-strokes have only 1 point each → both dropped → 0 paths.
        assertEquals(0, state.currentPaths.size, "single-point sub-strokes must be dropped")
    }

    // TC-8 / AC-10, AC-13: CIRCLE uses circular metric; SQUARE uses square metric.
    // Corner point at (0.575, 0.575) has dx=dy=0.075 from center (0.5, 0.5).
    //   SQUARE (halfSize=0.10): |0.075| <= 0.10 ✓ → inside zone
    //   CIRCLE (r=0.10):       0.075² + 0.075² = 0.01125 > 0.01 = r² → outside zone
    // Float values are exactly representable enough to dodge the boundary issue.
    @Test
    fun erasePointsInZone_circleMetric_excludesCornerPoint() {
        val path =
            DrawingPath(
                points =
                    listOf(
                        DrawingPoint(0.575f, 0.575f, isNewPath = true),
                        DrawingPoint(0.580f, 0.580f),
                    ),
                colorArgb = 0xFF000000L,
                strokeWidth = 5f,
            )
        val state = newStateWith(path)

        val changed =
            state.erasePointsInZone(
                centerX = 0.50f,
                centerY = 0.50f,
                halfSizeNormalized = 0.10f,
                shape = EraserShape.CIRCLE,
            )

        assertFalse(changed, "corner point must NOT be erased by circle metric")
        assertEquals(1, state.currentPaths.size)
        assertEquals(2, state.currentPaths[0].points.size)
    }

    @Test
    fun erasePointsInZone_squareMetric_includesCornerPoint() {
        // Same corner point — SQUARE metric DOES include it (|dx|<=0.10 && |dy|<=0.10).
        val path =
            DrawingPath(
                points =
                    listOf(
                        DrawingPoint(0.575f, 0.575f, isNewPath = true),
                        DrawingPoint(0.580f, 0.580f),
                    ),
                colorArgb = 0xFF000000L,
                strokeWidth = 5f,
            )
        val state = newStateWith(path)

        val changed =
            state.erasePointsInZone(
                centerX = 0.50f,
                centerY = 0.50f,
                halfSizeNormalized = 0.10f,
                shape = EraserShape.SQUARE,
            )

        assertTrue(changed, "corner point must be erased by square metric")
        assertEquals(0, state.currentPaths.size)
    }

    // TC-9 / EC-3: color / strokeWidth inherited by sub-strokes
    @Test
    fun erasePointsInZone_subStrokes_inheritColorAndStrokeWidth() {
        val path =
            DrawingPath(
                points =
                    listOf(
                        DrawingPoint(0.10f, 0.50f, isNewPath = true),
                        DrawingPoint(0.20f, 0.50f),
                        DrawingPoint(0.50f, 0.50f),
                        DrawingPoint(0.80f, 0.50f),
                        DrawingPoint(0.90f, 0.50f),
                    ),
                colorArgb = 0xFFAABBCCL,
                strokeWidth = 23.5f,
            )
        val state = newStateWith(path)

        state.erasePointsInZone(
            centerX = 0.50f,
            centerY = 0.50f,
            halfSizeNormalized = 0.05f,
            shape = EraserShape.CIRCLE,
        )

        assertEquals(2, state.currentPaths.size)
        for (p in state.currentPaths) {
            assertEquals(0xFFAABBCCL, p.colorArgb, "color preserved")
            assertEquals(23.5f, p.strokeWidth, "strokeWidth preserved")
        }
    }

    // No-op case: empty currentPaths returns false, leaves state untouched.
    @Test
    fun erasePointsInZone_emptyState_returnsFalse() {
        val state = PdfDrawingState()
        val changed =
            state.erasePointsInZone(
                centerX = 0.5f,
                centerY = 0.5f,
                halfSizeNormalized = 0.1f,
                shape = EraserShape.CIRCLE,
            )
        assertFalse(changed)
        assertEquals(0, state.currentPaths.size)
    }
}
