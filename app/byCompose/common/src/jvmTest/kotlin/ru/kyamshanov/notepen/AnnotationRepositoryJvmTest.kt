package ru.kyamshanov.notepen

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotationRepositoryJvmTest {

    private val repo = AnnotationRepositoryJvm()
    private val json = Json { ignoreUnknownKeys = true }

    // TC-6: save creates .notepen.json with correct structure
    @Test
    fun save_withAnnotations_createsJsonFile() = runBlocking {
        val dir = createTempDirectory("notepen_test")
        val pdfPath = dir.resolve("sample.pdf").toString()
        val annotations = mapOf(
            0 to listOf(
                DrawingPath(
                    points = listOf(DrawingPoint(10f, 20f, true), DrawingPoint(30f, 40f)),
                    color = Color.Red,
                    strokeWidth = 8f,
                )
            )
        )

        val result = repo.save(pdfPath, annotations, scale = 100)

        assertTrue(result.isSuccess, "save must succeed")
        val outputFile = java.io.File("$pdfPath.notepen.json")
        assertTrue(outputFile.exists(), "output file must be created")
        val content = outputFile.readText()
        assertTrue(content.contains("\"pages\""), "JSON must contain pages key: $content")
        assertTrue(content.contains("\"0\""), "JSON must contain page index key: $content")
    }

    // TC-6: round-trip — saved annotations can be decoded back
    @Test
    fun save_roundTrip_annotationsDecodedCorrectly() = runBlocking {
        val dir = createTempDirectory("notepen_test_rt")
        val pdfPath = dir.resolve("doc.pdf").toString()
        val original = mapOf(
            1 to listOf(DrawingPath(color = Color.Blue, strokeWidth = 5f))
        )

        repo.save(pdfPath, original, scale = 150)

        val outputFile = java.io.File("$pdfPath.notepen.json")
        val annotationData = json.decodeFromString(AnnotationData.serializer(), outputFile.readText())
        val decoded = annotationData.pages["1"]
        assertEquals(1, decoded?.size)
        assertEquals(Color.Blue, decoded?.first()?.color)
        assertEquals(150, annotationData.scale)
    }

    // TC-7: save to non-existent directory returns Result.failure
    @Test
    fun save_nonExistentDirectory_returnsFailure() = runBlocking {
        val result = repo.save("/nonexistent_dir_abc123/sample.pdf", emptyMap(), scale = 100)
        assertTrue(result.isFailure, "save must return failure for bad path")
    }

    // load — missing file returns empty bundle (no error)
    @Test
    fun load_noFile_returnsEmptyBundle() = runBlocking {
        val dir = createTempDirectory("notepen_load_empty")
        val result = repo.load(dir.resolve("missing.pdf").toString())
        assertTrue(result.isSuccess)
        assertEquals(AnnotationBundle(), result.getOrNull())
    }

    // load — round-trip with save (annotations + scale)
    @Test
    fun load_afterSave_returnsOriginalAnnotationsAndScale() = runBlocking {
        val dir = createTempDirectory("notepen_load_rt")
        val pdfPath = dir.resolve("doc.pdf").toString()
        val original = mapOf(
            0 to listOf(DrawingPath(color = Color.Red, strokeWidth = 3f)),
            2 to listOf(DrawingPath(color = Color.Blue)),
        )
        repo.save(pdfPath, original, scale = 130)

        val bundle = repo.load(pdfPath).getOrThrow()

        assertEquals(2, bundle.pages.size)
        assertEquals(Color.Red, bundle.pages[0]?.first()?.color)
        assertEquals(Color.Blue, bundle.pages[2]?.first()?.color)
        assertEquals(130, bundle.scale)
    }
}
