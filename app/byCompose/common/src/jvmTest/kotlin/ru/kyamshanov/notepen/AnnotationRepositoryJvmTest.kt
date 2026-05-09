package ru.kyamshanov.notepen

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnnotationRepositoryJvmTest {

    private val repo = AnnotationRepositoryJvm()
    private val json = Json { ignoreUnknownKeys = true }

    // TC-6 (legacy): save creates .notepen.json with correct structure
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

    // TC-6 (legacy round-trip)
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

    // TC-7 (legacy)
    @Test
    fun save_nonExistentDirectory_returnsFailure() = runBlocking {
        val result = repo.save("/nonexistent_dir_abc123/sample.pdf", emptyMap(), scale = 100)
        assertTrue(result.isFailure, "save must return failure for bad path")
    }

    @Test
    fun load_noFile_returnsEmptyBundle() = runBlocking {
        val dir = createTempDirectory("notepen_load_empty")
        val result = repo.load(dir.resolve("missing.pdf").toString())
        assertTrue(result.isSuccess)
        assertEquals(AnnotationBundle(), result.getOrNull())
    }

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

    // TC-15: save writes JSON with tools.pen and tools.eraser
    @Test
    fun save_writesToolsBlock_withPenAndEraser() = runBlocking {
        // covers TC-15
        val dir = createTempDirectory("notepen_tools")
        val pdfPath = dir.resolve("doc.pdf").toString()
        val pen = PenSettings(color = Color.Red, strokeWidth = 22f, alpha = 0.8f)
        val eraser = EraserSettings(shape = EraserShape.SQUARE, sizeNormalized = 0.07f)

        val result = repo.save(pdfPath, emptyMap(), scale = 100, pen = pen, eraser = eraser)

        assertTrue(result.isSuccess)
        val content = java.io.File("$pdfPath.notepen.json").readText()
        assertTrue(content.contains("\"tools\""), "JSON must contain tools key: $content")
        assertTrue(content.contains("\"pen\""), "JSON must contain tools.pen: $content")
        assertTrue(content.contains("\"eraser\""), "JSON must contain tools.eraser: $content")
        assertTrue(content.contains("\"SQUARE\""), "eraser shape SQUARE must round-trip: $content")
    }

    // TC-16: legacy JSON without tools loads with default pen/eraser
    @Test
    fun load_legacyJsonWithoutToolsBlock_returnsDefaults() = runBlocking {
        // covers TC-16 (AC-19 backward compat)
        val dir = createTempDirectory("notepen_legacy")
        val pdfPath = dir.resolve("legacy.pdf").toString()
        // Write a legacy file by hand — no `tools` field at all.
        val legacyJson = """{"pages":{"0":[]},"scale":120}"""
        java.io.File("$pdfPath.notepen.json").writeText(legacyJson)

        val bundle = repo.load(pdfPath).getOrThrow()

        assertEquals(120, bundle.scale)
        assertEquals(PenSettings(), bundle.pen, "missing tools must yield default PenSettings")
        assertEquals(EraserSettings(), bundle.eraser, "missing tools must yield default EraserSettings")
    }

    // TC-17: new JSON with tools loads pen/eraser from file
    @Test
    fun load_newJsonWithTools_returnsPenAndEraserFromFile() = runBlocking {
        // covers TC-17 (AC-16)
        val dir = createTempDirectory("notepen_tools_load")
        val pdfPath = dir.resolve("doc.pdf").toString()
        val pen = PenSettings(color = Color(0xFF8E24AA), strokeWidth = 30f, alpha = 0.6f)
        val eraser = EraserSettings(shape = EraserShape.SQUARE, sizeNormalized = 0.12f)
        repo.save(pdfPath, emptyMap(), scale = 100, pen = pen, eraser = eraser)

        val bundle = repo.load(pdfPath).getOrThrow()

        assertEquals(pen.strokeWidth, bundle.pen.strokeWidth)
        assertEquals(pen.alpha, bundle.pen.alpha)
        assertEquals(pen.color.value, bundle.pen.color.value, "ARGB Long round-trip")
        assertEquals(eraser, bundle.eraser)
    }

    // TC-18: save under IOException returns Result.failure
    @Test
    fun save_ioException_returnsFailure() = runBlocking {
        // covers TC-18 / EC-12 — using a path that cannot be written to
        val result = repo.save(
            "/nonexistent_path_xyz_777/file.pdf",
            emptyMap(),
            scale = 100,
            pen = PenSettings(),
            eraser = EraserSettings(),
        )
        assertTrue(result.isFailure)
    }

    // TC-30 (Step 7): full DetailsContent flow — save (annotations + scale + pen + eraser) then
    // load — all 4 fields round-trip together. This pins the contract DetailsContent depends on.
    @Test
    fun saveAndLoad_fullDetailsContentFlow_roundTripsAllFields() = runBlocking {
        val dir = createTempDirectory("notepen_step7_full")
        val pdfPath = dir.resolve("doc.pdf").toString()
        val annotations = mapOf(
            0 to listOf(
                DrawingPath(
                    points = listOf(DrawingPoint(0.1f, 0.2f, true), DrawingPoint(0.3f, 0.4f)),
                    color = Color(0xFFE53935),
                    strokeWidth = 18f,
                )
            ),
            2 to listOf(DrawingPath(color = Color(0xFF1E88E5), strokeWidth = 5f)),
        )
        val pen = PenSettings(color = Color(0xFF43A047), strokeWidth = 25f, alpha = 0.7f)
        val eraser = EraserSettings(shape = EraserShape.SQUARE, sizeNormalized = 0.15f)
        val scale = 140

        val saveResult = repo.save(pdfPath, annotations, scale, pen, eraser)
        assertTrue(saveResult.isSuccess, "save must succeed for the full DetailsContent flow")

        val bundle = repo.load(pdfPath).getOrThrow()

        // scale
        assertEquals(scale, bundle.scale)
        // pages
        assertEquals(2, bundle.pages.size)
        assertEquals(Color(0xFFE53935), bundle.pages[0]?.first()?.color)
        assertEquals(Color(0xFF1E88E5), bundle.pages[2]?.first()?.color)
        // pen
        assertEquals(pen.strokeWidth, bundle.pen.strokeWidth)
        assertEquals(pen.alpha, bundle.pen.alpha)
        assertEquals(pen.color.value, bundle.pen.color.value, "pen ARGB Long round-trip")
        // eraser
        assertEquals(eraser.shape, bundle.eraser.shape)
        assertEquals(eraser.sizeNormalized, bundle.eraser.sizeNormalized)
    }

    // TC-19 surrogate: AnnotationRepositoryAndroid is structurally identical and shares
    // the JSON shape via AnnotationData — cross-validated through round-trip on JVM.
    // The dedicated Android instrumentation test lives in androidTest (out of scope for
    // jvmTest); see plan.md Step 1 Known limitations.
    @Test
    fun jsonShape_isCompatibleAcrossPlatforms() = runBlocking {
        // covers TC-19 (structural — same AnnotationData schema used by both impls)
        val dir = createTempDirectory("notepen_cross_platform")
        val pdfPath = dir.resolve("doc.pdf").toString()
        repo.save(
            pdfPath,
            emptyMap(),
            scale = 100,
            pen = PenSettings(),
            eraser = EraserSettings(),
        )
        val content = java.io.File("$pdfPath.notepen.json").readText()
        // Re-decode through the same serializer the Android impl uses.
        val decoded = json.decodeFromString(AnnotationData.serializer(), content)
        assertNotNull(decoded.tools, "tools block must round-trip")
        assertEquals(PenSettings(), decoded.tools?.pen)
        assertEquals(EraserSettings(), decoded.tools?.eraser)
    }
}
