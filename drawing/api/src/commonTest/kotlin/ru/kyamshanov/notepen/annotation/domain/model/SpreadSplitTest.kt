package ru.kyamshanov.notepen.annotation.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpreadSplitTest {
    private fun assertClose(
        expected: Float,
        actual: Float,
        msg: String = "",
    ) {
        assertTrue(kotlin.math.abs(expected - actual) < EPS, "$msg expected=$expected actual=$actual")
    }

    // ── Index mapping ─────────────────────────────────────────────────────────

    @Test
    fun logicalIndicesAreSequentialLeftThenRight() {
        assertEquals(0, SpreadSplit.leftLogical(0))
        assertEquals(1, SpreadSplit.rightLogical(0))
        assertEquals(2, SpreadSplit.leftLogical(1))
        assertEquals(3, SpreadSplit.rightLogical(1))
        assertTrue(SpreadSplit.isRightHalf(1))
        assertFalse(SpreadSplit.isRightHalf(2))
        assertEquals(1, SpreadSplit.sourceIndexOf(2))
        assertEquals(1, SpreadSplit.sourceIndexOf(3))
    }

    @Test
    fun logicalCountDoublesWhenEnabled() {
        assertEquals(6, SpreadSplit.logicalCount(sourceCount = 3, splitEnabled = true))
        assertEquals(3, SpreadSplit.logicalCount(sourceCount = 3, splitEnabled = false))
    }

    @Test
    fun sourceForResolvesCropPerHalf() {
        val left = SpreadSplit.sourceFor(logicalIndex = 4, splitEnabled = true)
        assertEquals(2, left.sourceIndex)
        assertClose(0f, left.crop.leftN)
        assertClose(0.5f, left.crop.rightN)

        val right = SpreadSplit.sourceFor(logicalIndex = 5, splitEnabled = true)
        assertEquals(2, right.sourceIndex)
        assertClose(0.5f, right.crop.leftN)
        assertClose(1f, right.crop.rightN)

        val identity = SpreadSplit.sourceFor(logicalIndex = 5, splitEnabled = false)
        assertEquals(5, identity.sourceIndex)
        assertTrue(identity.crop.isFull)
    }

    @Test
    fun halfAspectHalvesSourceAspect() {
        // Landscape 2-up A4 spread (2 portrait A4 side by side) ≈ 1.414 wide;
        // each half is portrait A4 ≈ 0.707.
        assertClose(0.707f, SpreadSplit.halfAspect(1.414f))
    }

    // ── Point remap (the load-bearing coordinate math) ─────────────────────────

    @Test
    fun splitPointMapsLeftHalfToFullWidth() {
        // x = 0.25 (left quarter of source) → x' = 0.5 of left half.
        val (right, p) = SpreadSplit.splitPoint(DrawingPoint(0.25f, 0.3f))
        assertFalse(right)
        assertClose(0.5f, p.x)
        assertClose(0.3f, p.y, "y unchanged")
    }

    @Test
    fun splitPointMapsRightHalfToFullWidth() {
        // x = 0.75 (right quarter of source) → right half, x' = 0.5.
        val (right, p) = SpreadSplit.splitPoint(DrawingPoint(0.75f, 0.3f))
        assertTrue(right)
        assertClose(0.5f, p.x)
        assertClose(0.3f, p.y)
    }

    @Test
    fun gutterPointBelongsToRightHalf() {
        // Exactly on the gutter (x = 0.5) is assigned to the right half (x' = 0).
        val (right, p) = SpreadSplit.splitPoint(DrawingPoint(SpreadSplit.GUTTER_X, 0.1f))
        assertTrue(right)
        assertClose(0f, p.x)
    }

    @Test
    fun splitThenMergeRoundTripsPoint() {
        for (x in listOf(0f, 0.1f, 0.49f, 0.5f, 0.9f, 1f)) {
            val original = DrawingPoint(x, 0.42f, isNewPath = true, pressure = 0.7f, tilt = 0.2f)
            val (right, half) = SpreadSplit.splitPoint(original)
            val back = SpreadSplit.mergePoint(half, right)
            assertClose(x, back.x, "x round-trip at $x")
            assertClose(0.42f, back.y, "y round-trip at $x")
            // Metadata preserved.
            assertEquals(original.isNewPath, back.isNewPath)
            assertEquals(original.pressure, back.pressure)
            assertEquals(original.tilt, back.tilt)
        }
    }

    @Test
    fun splitPathDoublesStrokeWidthAndAssignsByFirstPoint() {
        // First point in the right half → whole stroke goes right; later points
        // that crossed past x=0.5 are mapped with the same (right) basis, even if
        // they extend below 0 — the gutter-spanning stroke is NOT subdivided.
        val path =
            DrawingPath(
                points = listOf(DrawingPoint(0.6f, 0.2f), DrawingPoint(0.55f, 0.25f)),
                strokeWidth = 0.01f,
            )
        val (right, split) = SpreadSplit.splitPath(path)
        assertTrue(right)
        assertClose(0.02f, split.strokeWidth, "stroke width doubled for half-width page")
        assertClose(0.2f, split.points[0].x) // (0.6-0.5)/0.5 = 0.2
        assertClose(0.1f, split.points[1].x) // (0.55-0.5)/0.5 = 0.1
    }

    @Test
    fun splitThenMergePathRoundTripsWidthAndPoints() {
        val path =
            DrawingPath(
                points = listOf(DrawingPoint(0.2f, 0.1f), DrawingPoint(0.3f, 0.4f)),
                strokeWidth = 0.012f,
            )
        val (right, split) = SpreadSplit.splitPath(path)
        val back = SpreadSplit.mergePath(split, right)
        assertClose(path.strokeWidth, back.strokeWidth, "width round-trip")
        assertClose(0.2f, back.points[0].x)
        assertClose(0.3f, back.points[1].x)
    }

    // ── Page-map remap (strokes + rotations across the index space) ─────────────

    @Test
    fun splitStrokesByPageRoutesToLeftAndRightLogicalPages() {
        val source =
            mapOf(
                1 to
                    listOf(
                        DrawingPath(points = listOf(DrawingPoint(0.1f, 0.1f))), // left → logical 2
                        DrawingPath(points = listOf(DrawingPoint(0.8f, 0.1f))), // right → logical 3
                    ),
            )
        val logical = SpreadSplit.splitStrokesByPage(source)
        assertEquals(1, logical[2]?.size)
        assertEquals(1, logical[3]?.size)
        // Round-trips back to source page 1 with both strokes.
        val merged = SpreadSplit.mergeStrokesByPage(logical)
        assertEquals(2, merged[1]?.size)
    }

    @Test
    fun splitRotationsInheritsToBothHalvesAndMergesFromLeft() {
        val source = mapOf(0 to 1, 2 to 3) // src page 0 → q1, src page 2 → q3
        val logical = SpreadSplit.splitRotations(source)
        assertEquals(1, logical[0]) // left of src 0
        assertEquals(1, logical[1]) // right of src 0
        assertEquals(3, logical[4]) // left of src 2
        assertEquals(3, logical[5]) // right of src 2

        // Merge takes the LEFT half's rotation; a divergent right half is dropped.
        val divergent = logical.toMutableMap().apply { put(1, 2) } // right half differs
        val merged = SpreadSplit.mergeRotations(divergent)
        assertEquals(1, merged[0]) // from left half of src 0
        assertEquals(3, merged[2]) // from left half of src 2
        assertFalse(merged.containsKey(1))
    }

    private companion object {
        const val EPS = 1e-4f
    }
}
