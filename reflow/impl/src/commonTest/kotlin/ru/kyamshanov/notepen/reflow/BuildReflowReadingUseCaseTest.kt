package ru.kyamshanov.notepen.reflow

import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.reflow.api.PdfContentKind
import ru.kyamshanov.notepen.reflow.api.PdfReflowExtractor
import ru.kyamshanov.notepen.reflow.api.ReflowDocument
import ru.kyamshanov.notepen.reflow.api.TextAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildReflowReadingUseCaseTest {
    private val document = ReflowAssembler.assemble(listOf(page(line("hello world", top = 100f))))

    private val extractor =
        object : PdfReflowExtractor {
            override suspend fun probe(path: String): PdfContentKind = document.kind

            override suspend fun extract(path: String): ReflowDocument = document
        }

    @Test
    fun `maps page strokes to text highlights`() =
        runTest {
            val strokeOverHello =
                DrawingPath(
                    points =
                        listOf(
                            DrawingPoint(52f / PAGE_WIDTH_PT, 103f / PAGE_HEIGHT_PT),
                            DrawingPoint(78f / PAGE_WIDTH_PT, 107f / PAGE_HEIGHT_PT),
                        ),
                )
            val reading =
                BuildReflowReadingUseCase(extractor)(
                    path = "any.pdf",
                    strokesByPage = mapOf(0 to listOf(strokeOverHello)),
                )
            assertEquals(document, reading.document)
            assertEquals(listOf(TextAnchor(0, 0, 5)), reading.highlights)
        }

    @Test
    fun `no strokes yields no highlights`() =
        runTest {
            val reading = BuildReflowReadingUseCase(extractor)(path = "any.pdf", strokesByPage = emptyMap())
            assertTrue(reading.highlights.isEmpty())
            assertEquals(document, reading.document)
        }

    @Test
    fun `strokes missing text yield no highlights`() =
        runTest {
            val strokeFarAway =
                DrawingPath(
                    points = listOf(DrawingPoint(0.1f, 0.9f), DrawingPoint(0.2f, 0.95f)),
                )
            val reading =
                BuildReflowReadingUseCase(extractor)(
                    path = "any.pdf",
                    strokesByPage = mapOf(0 to listOf(strokeFarAway)),
                )
            assertTrue(reading.highlights.isEmpty())
        }
}
