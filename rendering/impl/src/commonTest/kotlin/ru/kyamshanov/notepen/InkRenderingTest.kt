package ru.kyamshanov.notepen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.ToolKind
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InkRenderingTest {
    @Test
    fun `target spec maps page coordinates into segment surface`() {
        val spec =
            InkRenderSpec(
                surfaceSize = IntSize(400, 200),
                extent = PageExtent(left = -0.1f, top = -0.2f, right = 1.1f, bottom = 1.2f),
                targetOnPage = Rect(0.2f, 0.3f, 0.6f, 0.5f),
            )

        assertNear(0f, spec.mapPageToSurface(Offset(0.2f, 0.3f)).x)
        assertNear(0f, spec.mapPageToSurface(Offset(0.2f, 0.3f)).y)
        assertNear(400f, spec.mapPageToSurface(Offset(0.6f, 0.5f)).x)
        assertNear(200f, spec.mapPageToSurface(Offset(0.6f, 0.5f)).y)
        assertNear(200f, spec.mapPageToSurface(Offset(0.4f, 0.4f)).x)
        assertNear(100f, spec.mapPageToSurface(Offset(0.4f, 0.4f)).y)
    }

    @Test
    fun `completed ink cache key changes by target size history and extent`() {
        val spec =
            InkRenderSpec(
                surfaceSize = IntSize(512, 256),
                extent = PageExtent.Pdf,
                targetOnPage = Rect(0f, 0f, 0.5f, 0.5f),
            )
        val key = completedInkCacheKey(spec, historyVersion = 1)

        assertNotEquals(key, completedInkCacheKey(spec.copy(surfaceSize = IntSize(768, 256)), 1))
        assertNotEquals(key, completedInkCacheKey(spec.copy(targetOnPage = Rect(0.1f, 0f, 0.6f, 0.5f)), 1))
        assertNotEquals(key, completedInkCacheKey(spec.copy(extent = PageExtent(left = -0.2f, top = 0f, right = 1f, bottom = 1f)), 1))
        assertNotEquals(key, completedInkCacheKey(spec, historyVersion = 2))
    }

    @Test
    fun `cache size is bucketed until platform cap is reached`() {
        assertEquals(IntSize(512, 512), bucketedInkCacheSize(IntSize(257, 511), maxDimensionPx = 8192))
        assertEquals(IntSize(3000, 1500), bucketedInkCacheSize(IntSize(9000, 4500), maxDimensionPx = 3000))
    }

    @Test
    fun `tail start preserves all ink while cache is cold or catching up`() {
        assertEquals(0, completedInkTailStart(pathCount = 10, cachedStrokeCount = null))
        assertEquals(0, completedInkTailStart(pathCount = 60, cachedStrokeCount = null))
        assertEquals(40, completedInkTailStart(pathCount = 60, cachedStrokeCount = 40))
        assertEquals(60, completedInkTailStart(pathCount = 60, cachedStrokeCount = 99))
    }

    @Test
    fun `heavy completed ink tail is deferred to bitmap cache`() {
        val paths = List(MAX_VECTOR_INK_TAIL_PATHS + 1) { testPath(toolKind = ToolKind.PEN) }

        assertEquals(
            false,
            shouldDrawCompletedInkTail(paths, tailStart = 0) { it.toolType != ToolKind.MARKER },
        )
    }

    @Test
    fun `small completed ink tail is drawn as vector anti flicker tail`() {
        val paths = List(3) { testPath(toolKind = ToolKind.PEN) }

        assertEquals(
            true,
            shouldDrawCompletedInkTail(paths, tailStart = 0) { it.toolType != ToolKind.MARKER },
        )
    }

    @Test
    fun `completed ink tail limit ignores unrelated tools`() {
        val paths = List(MAX_VECTOR_INK_TAIL_PATHS + 1) { testPath(toolKind = ToolKind.MARKER) }

        assertEquals(
            true,
            shouldDrawCompletedInkTail(paths, tailStart = 0) { it.toolType != ToolKind.MARKER },
        )
    }

    private fun testPath(toolKind: ToolKind): DrawingPath =
        DrawingPath(
            points =
                listOf(
                    DrawingPoint(x = 0f, y = 0f),
                    DrawingPoint(x = 1f, y = 1f),
                ),
            colorArgb = 0xff000000,
            strokeWidth = 0.01f,
            toolType = toolKind,
        )

    private fun assertNear(
        expected: Float,
        actual: Float,
        eps: Float = 1e-4f,
    ) {
        assertTrue(abs(expected - actual) <= eps, "Expected $expected, got $actual")
    }
}
