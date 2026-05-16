package ru.kyamshanov.notepen

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the cursor-anchor invariant for [computeCursorAnchoredZoom]:
 * the LazyColumn pixel under the gesture centroid before the zoom must be
 * under the centroid after the zoom.
 *
 * Inverse formula (graphicsLayer with origin top-left):
 *     lazyP = (centroid − panOffset) / gestureScale
 *
 * Regressing the previous bug requires running the same check at
 * `committedScale ≠ 100`, where the old `committedScale/100 * gestureScale`
 * inverse drifted by exactly that factor.
 */
class CursorAnchoredZoomTest {

    private fun assertCursorAnchored(
        centroid: Offset,
        panBefore: Offset,
        gBefore: Float,
        result: ZoomResult,
        pan: Offset = Offset.Zero,
        tolerance: Float = 0.001f,
    ) {
        val lazyBefore = (centroid - panBefore) / gBefore
        // Account for the additive [pan] applied on top of cursor-anchored math.
        val lazyAfter = (centroid - (result.panOffset - pan)) / result.gestureScale
        assertTrue(
            abs(lazyBefore.x - lazyAfter.x) < tolerance &&
                abs(lazyBefore.y - lazyAfter.y) < tolerance,
            "cursor drifted: before=$lazyBefore after=$lazyAfter",
        )
    }

    @Test
    fun `committed 100 - cursor pixel stays under centroid`() {
        val centroid = Offset(400f, 300f)
        val panBefore = Offset.Zero
        val gBefore = 1f
        val result = computeCursorAnchoredZoom(
            centroid = centroid,
            pan = Offset.Zero,
            zoom = 1.1f,
            committedScale = 100,
            gestureScale = gBefore,
            panOffset = panBefore,
        )
        assertCursorAnchored(centroid, panBefore, gBefore, result)
    }

    @Test
    fun `committed 200 - cursor pixel stays under centroid (regression)`() {
        // The original bug: with committedScale != 100 and a non-zero
        // panOffset, the point under the cursor drifted by committedScale/100.
        val centroid = Offset(523f, 711f)
        val panBefore = Offset.Zero
        val gBefore = 1f
        val result = computeCursorAnchoredZoom(
            centroid = centroid,
            pan = Offset.Zero,
            zoom = 1.1f,
            committedScale = 200,
            gestureScale = gBefore,
            panOffset = panBefore,
        )
        assertCursorAnchored(centroid, panBefore, gBefore, result)
    }

    @Test
    fun `committed 150 with non-trivial gesture state - cursor stays put`() {
        val centroid = Offset(640f, 480f)
        val panBefore = Offset(40f, -20f)
        val gBefore = 1.3f
        val result = computeCursorAnchoredZoom(
            centroid = centroid,
            pan = Offset.Zero,
            zoom = 0.9f,
            committedScale = 150,
            gestureScale = gBefore,
            panOffset = panBefore,
        )
        assertCursorAnchored(centroid, panBefore, gBefore, result)
    }

    @Test
    fun `multiple sequential zoom steps preserve the cursor pixel`() {
        val centroid = Offset(312f, 444f)
        var pan = Offset(10f, 5f)
        var g = 1f
        val originalLazy = (centroid - pan) / g
        repeat(8) {
            val r = computeCursorAnchoredZoom(
                centroid = centroid,
                pan = Offset.Zero,
                zoom = 1.1f,
                committedScale = 175,
                gestureScale = g,
                panOffset = pan,
            )
            pan = r.panOffset
            g = r.gestureScale
        }
        val finalLazy = (centroid - pan) / g
        assertTrue(
            abs(originalLazy.x - finalLazy.x) < 0.01f &&
                abs(originalLazy.y - finalLazy.y) < 0.01f,
            "lazy point drifted after 8 zoom steps: $originalLazy -> $finalLazy",
        )
    }

    @Test
    fun `zoom clamps at max scale and remains cursor-anchored for the clamped step`() {
        val centroid = Offset(500f, 400f)
        val panBefore = Offset.Zero
        val committedScale = 400
        // maxGesture = 800/400 = 2f. Start near it, push past.
        val gBefore = 1.9f
        val result = computeCursorAnchoredZoom(
            centroid = centroid,
            pan = Offset.Zero,
            zoom = 1.5f,
            committedScale = committedScale,
            gestureScale = gBefore,
            panOffset = panBefore,
        )
        assertEquals(2f, result.gestureScale, "gestureScale must clamp at maxScale/committed")
        assertCursorAnchored(centroid, panBefore, gBefore, result)
    }

    @Test
    fun `zoom equal to 1 with no pan returns state unchanged`() {
        val pan = Offset(17f, 23f)
        val g = 1.4f
        val result = computeCursorAnchoredZoom(
            centroid = Offset(100f, 100f),
            pan = Offset.Zero,
            zoom = 1f,
            committedScale = 150,
            gestureScale = g,
            panOffset = pan,
        )
        assertEquals(pan, result.panOffset)
        assertEquals(g, result.gestureScale)
    }

    @Test
    fun `additive pan is applied on top of cursor-anchored result`() {
        val centroid = Offset(200f, 200f)
        val panBefore = Offset(0f, 0f)
        val gBefore = 1f
        val pan = Offset(5f, 7f)
        val withPan = computeCursorAnchoredZoom(
            centroid = centroid,
            pan = pan,
            zoom = 1.2f,
            committedScale = 100,
            gestureScale = gBefore,
            panOffset = panBefore,
        )
        val withoutPan = computeCursorAnchoredZoom(
            centroid = centroid,
            pan = Offset.Zero,
            zoom = 1.2f,
            committedScale = 100,
            gestureScale = gBefore,
            panOffset = panBefore,
        )
        assertEquals(withoutPan.panOffset + pan, withPan.panOffset)
        assertEquals(withoutPan.gestureScale, withPan.gestureScale)
        assertCursorAnchored(centroid, panBefore, gBefore, withPan, pan = pan)
    }
}
