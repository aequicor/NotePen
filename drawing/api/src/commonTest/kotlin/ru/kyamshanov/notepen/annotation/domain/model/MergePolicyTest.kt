package ru.kyamshanov.notepen.annotation.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergePolicyTest {
    private fun path(
        id: String,
        tool: ToolKind = ToolKind.PEN,
    ) = DrawingPath(strokeId = id, toolType = tool)

    @Test
    fun unionsStrokesAcrossLayersOnSamePage() {
        val a = AnnotationLayer("devA", mapOf(0 to listOf(path("devA#1"))))
        val b = AnnotationLayer("devB", mapOf(0 to listOf(path("devB#1"))))
        val out = UnionAllPolicy.compose(listOf(a, b), AnnotationFilter.All)
        assertEquals(setOf("devA#1", "devB#1"), out.getValue(0).map { it.strokeId }.toSet())
    }

    @Test
    fun dedupesByStrokeIdAcrossLayers() {
        val a = AnnotationLayer("devA", mapOf(0 to listOf(path("dup#1"))))
        val b = AnnotationLayer("devB", mapOf(0 to listOf(path("dup#1"))))
        val out = UnionAllPolicy.compose(listOf(a, b), AnnotationFilter.All)
        assertEquals(1, out.getValue(0).size)
    }

    @Test
    fun keepsEveryLegacyStrokeWithEmptyId() {
        val legacy = AnnotationLayer(AnnotationLayer.HOST, mapOf(0 to listOf(path(""), path(""))))
        val out = UnionAllPolicy.compose(listOf(legacy), AnnotationFilter.All)
        assertEquals(2, out.getValue(0).size)
    }

    @Test
    fun byAuthorKeepsOnlyMatchingLayer() {
        val a = AnnotationLayer("devA", mapOf(0 to listOf(path("devA#1"))))
        val b = AnnotationLayer("devB", mapOf(0 to listOf(path("devB#1"))))
        val out = UnionAllPolicy.compose(listOf(a, b), AnnotationFilter.ByAuthor(setOf("devB")))
        assertEquals(listOf("devB#1"), out.getValue(0).map { it.strokeId })
    }

    @Test
    fun byToolKeepsOnlyMatchingTool() {
        val layer =
            AnnotationLayer(
                "devA",
                mapOf(0 to listOf(path("p#1", ToolKind.PEN), path("m#1", ToolKind.MARKER))),
            )
        val out = UnionAllPolicy.compose(listOf(layer), AnnotationFilter.ByTool(setOf(ToolKind.MARKER)))
        assertEquals(listOf("m#1"), out.getValue(0).map { it.strokeId })
    }

    @Test
    fun byPageRangeKeepsOnlyPagesInRange() {
        val layer =
            AnnotationLayer(
                "devA",
                mapOf(0 to listOf(path("a")), 2 to listOf(path("c")), 5 to listOf(path("f"))),
            )
        val out = UnionAllPolicy.compose(listOf(layer), AnnotationFilter.ByPageRange(1..3))
        assertEquals(setOf(2), out.keys)
    }

    @Test
    fun andComposesAuthorAndTool() {
        val a =
            AnnotationLayer(
                "devA",
                mapOf(0 to listOf(path("a#pen", ToolKind.PEN), path("a#mk", ToolKind.MARKER))),
            )
        val b = AnnotationLayer("devB", mapOf(0 to listOf(path("b#mk", ToolKind.MARKER))))
        val filter =
            AnnotationFilter.And(
                listOf(
                    AnnotationFilter.ByAuthor(setOf("devA")),
                    AnnotationFilter.ByTool(setOf(ToolKind.MARKER)),
                ),
            )
        val out = UnionAllPolicy.compose(listOf(a, b), filter)
        assertEquals(listOf("a#mk"), out.getValue(0).map { it.strokeId })
    }

    @Test
    fun notInvertsMatch() {
        val f = AnnotationFilter.Not(AnnotationFilter.ByTool(setOf(ToolKind.MARKER)))
        assertTrue(f.matches("d", 0, path("p", ToolKind.PEN)))
        assertFalse(f.matches("d", 0, path("m", ToolKind.MARKER)))
    }

    @Test
    fun byTimeCurrentlyPassesThrough() {
        val layer = AnnotationLayer("devA", mapOf(0 to listOf(path("a"))))
        val out = UnionAllPolicy.compose(listOf(layer), AnnotationFilter.ByTime(0L, 1L))
        assertEquals(1, out.getValue(0).size)
    }
}
