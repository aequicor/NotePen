package ru.kyamshanov.notepen

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationBundle
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationViewState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.NormalizedRect
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotationRepositoryJvmTest {
    private val repo = AnnotationRepositoryJvmAndroid()
    private val json = Json { ignoreUnknownKeys = true }

    // TC-6 (legacy): save creates .notepen.json with correct structure
    @Test
    fun save_withAnnotations_createsJsonFile() =
        runBlocking {
            val dir = createTempDirectory("notepen_test")
            val pdfPath = dir.resolve("sample.pdf").toString()
            val annotations =
                mapOf(
                    0 to
                        listOf(
                            DrawingPath(
                                points = listOf(DrawingPoint(10f, 20f, true), DrawingPoint(30f, 40f)),
                                colorArgb = 0xFFFF0000L,
                                strokeWidth = 8f,
                            ),
                        ),
                )

            val result = repo.save(pdfPath, annotations, scale = 100)

            assertTrue(result.isSuccess, "save must succeed")
            val outputFile = java.io.File("$pdfPath.notepen.json")
            assertTrue(outputFile.exists(), "output file must be created")
            val content = outputFile.readText()
            assertTrue(content.contains("\"pages\""), "JSON must contain pages key: $content")
            assertTrue(content.contains("\"0\""), "JSON must contain page index key: $content")
        }

    // TC-6 (round-trip via public load API)
    @Test
    fun save_roundTrip_annotationsDecodedCorrectly() =
        runBlocking {
            val dir = createTempDirectory("notepen_test_rt")
            val pdfPath = dir.resolve("doc.pdf").toString()
            val original =
                mapOf(
                    1 to listOf(DrawingPath(colorArgb = 0xFF0000FFL, strokeWidth = 5f)),
                )

            repo.save(pdfPath, original, scale = 150)
            val bundle = repo.load(pdfPath).getOrThrow()

            assertEquals(1, bundle.pages[1]?.size)
            assertEquals(0xFF0000FFL, bundle.pages[1]?.first()?.colorArgb)
            assertEquals(150, bundle.scale)
        }

    // TC-7 (legacy)
    @Test
    fun save_nonExistentDirectory_returnsFailure() =
        runBlocking {
            val result = repo.save("/nonexistent_dir_abc123/sample.pdf", emptyMap(), scale = 100)
            assertTrue(result.isFailure, "save must return failure for bad path")
        }

    @Test
    fun load_noFile_returnsEmptyBundle() =
        runBlocking {
            val dir = createTempDirectory("notepen_load_empty")
            val result = repo.load(dir.resolve("missing.pdf").toString())
            assertTrue(result.isSuccess)
            assertEquals(AnnotationBundle(), result.getOrNull())
        }

    @Test
    fun load_afterSave_returnsOriginalAnnotationsAndScale() =
        runBlocking {
            val dir = createTempDirectory("notepen_load_rt")
            val pdfPath = dir.resolve("doc.pdf").toString()
            val original =
                mapOf(
                    0 to listOf(DrawingPath(colorArgb = 0xFFFF0000L, strokeWidth = 3f)),
                    2 to listOf(DrawingPath(colorArgb = 0xFF0000FFL)),
                )
            repo.save(pdfPath, original, scale = 130)

            val bundle = repo.load(pdfPath).getOrThrow()

            assertEquals(2, bundle.pages.size)
            assertEquals(0xFFFF0000L, bundle.pages[0]?.first()?.colorArgb)
            assertEquals(0xFF0000FFL, bundle.pages[2]?.first()?.colorArgb)
            assertEquals(130, bundle.scale)
        }

    // TC-15: save writes JSON with pen and eraser fields
    @Test
    fun save_writesToolsBlock_withPenAndEraser() =
        runBlocking {
            val dir = createTempDirectory("notepen_tools")
            val pdfPath = dir.resolve("doc.pdf").toString()
            val pen = PenSettings(colorArgb = 0xFFFF0000L, strokeWidth = 22f, alpha = 0.8f)
            val eraser = EraserSettings(shape = EraserShape.SQUARE, sizeNormalized = 0.07f)

            val result = repo.save(pdfPath, emptyMap(), scale = 100, pen = pen, eraser = eraser)

            assertTrue(result.isSuccess)
            val content = java.io.File("$pdfPath.notepen.json").readText()
            assertTrue(content.contains("\"pen\""), "JSON must contain pen key: $content")
            assertTrue(content.contains("\"eraser\""), "JSON must contain eraser key: $content")
            assertTrue(content.contains("\"SQUARE\""), "eraser shape SQUARE must round-trip: $content")
        }

    // TC-16: legacy JSON without pen/eraser loads with defaults
    @Test
    fun load_legacyJsonWithoutToolsBlock_returnsDefaults() =
        runBlocking {
            val dir = createTempDirectory("notepen_legacy")
            val pdfPath = dir.resolve("legacy.pdf").toString()
            java.io.File("$pdfPath.notepen.json").writeText("""{"pages":{},"scale":120}""")

            val bundle = repo.load(pdfPath).getOrThrow()

            assertEquals(120, bundle.scale)
            assertEquals(PenSettings(), bundle.pen, "missing pen must yield default PenSettings")
            assertEquals(EraserSettings(), bundle.eraser, "missing eraser must yield default EraserSettings")
        }

    // TC-17: new JSON with pen/eraser loads from file
    @Test
    fun load_newJsonWithTools_returnsPenAndEraserFromFile() =
        runBlocking {
            val dir = createTempDirectory("notepen_tools_load")
            val pdfPath = dir.resolve("doc.pdf").toString()
            val pen = PenSettings(colorArgb = 0xFF8E24AAL, strokeWidth = 30f, alpha = 0.6f)
            val eraser = EraserSettings(shape = EraserShape.SQUARE, sizeNormalized = 0.12f)
            repo.save(pdfPath, emptyMap(), scale = 100, pen = pen, eraser = eraser)

            val bundle = repo.load(pdfPath).getOrThrow()

            assertEquals(pen.strokeWidth, bundle.pen.strokeWidth)
            assertEquals(pen.alpha, bundle.pen.alpha)
            assertEquals(pen.colorArgb, bundle.pen.colorArgb, "ARGB Long round-trip")
            assertEquals(eraser, bundle.eraser)
        }

    // TC-18: save under IOException returns Result.failure
    @Test
    fun save_ioException_returnsFailure() =
        runBlocking {
            val result =
                repo.save(
                    "/nonexistent_path_xyz_777/file.pdf",
                    emptyMap(),
                    scale = 100,
                    pen = PenSettings(),
                    eraser = EraserSettings(),
                )
            assertTrue(result.isFailure)
        }

    // TC-30 (Step 7): full flow — save (annotations + scale + pen + eraser) then load
    @Test
    fun saveAndLoad_fullDetailsContentFlow_roundTripsAllFields() =
        runBlocking {
            val dir = createTempDirectory("notepen_step7_full")
            val pdfPath = dir.resolve("doc.pdf").toString()
            val annotations =
                mapOf(
                    0 to
                        listOf(
                            DrawingPath(
                                points = listOf(DrawingPoint(0.1f, 0.2f, true), DrawingPoint(0.3f, 0.4f)),
                                colorArgb = 0xFFE53935L,
                                strokeWidth = 18f,
                            ),
                        ),
                    2 to listOf(DrawingPath(colorArgb = 0xFF1E88E5L, strokeWidth = 5f)),
                )
            val pen = PenSettings(colorArgb = 0xFF43A047L, strokeWidth = 25f, alpha = 0.7f)
            val eraser = EraserSettings(shape = EraserShape.SQUARE, sizeNormalized = 0.15f)
            val scale = 140

            val saveResult = repo.save(pdfPath, annotations, scale, pen = pen, eraser = eraser)
            assertTrue(saveResult.isSuccess, "save must succeed for the full DetailsContent flow")

            val bundle = repo.load(pdfPath).getOrThrow()

            assertEquals(scale, bundle.scale)
            assertEquals(2, bundle.pages.size)
            assertEquals(0xFFE53935L, bundle.pages[0]?.first()?.colorArgb)
            assertEquals(0xFF1E88E5L, bundle.pages[2]?.first()?.colorArgb)
            assertEquals(pen.strokeWidth, bundle.pen.strokeWidth)
            assertEquals(pen.alpha, bundle.pen.alpha)
            assertEquals(pen.colorArgb, bundle.pen.colorArgb, "pen ARGB Long round-trip")
            assertEquals(eraser.shape, bundle.eraser.shape)
            assertEquals(eraser.sizeNormalized, bundle.eraser.sizeNormalized)
        }

    // TC-1 (Step 2): save(currentPage = 5) → JSON contains "currentPage": 5
    @Test
    fun save_withCurrentPage_writesCurrentPageToJson() =
        runBlocking {
            val dir = createTempDirectory("notepen_cp_save")
            val pdfPath = dir.resolve("doc.pdf").toString()

            val result = repo.save(pdfPath, emptyMap(), scale = 100, currentPage = 5)

            assertTrue(result.isSuccess)
            val content = java.io.File("$pdfPath.notepen.json").readText()
            assertTrue(
                content.contains("\"currentPage\":5") || content.contains("\"currentPage\": 5"),
                "JSON must contain currentPage 5, got: $content",
            )
        }

    // TC-2 (Step 2): load JSON with currentPage=5 → bundle.currentPage == 5
    @Test
    fun load_jsonWithCurrentPage_returnsBundleWithCurrentPage() =
        runBlocking {
            val dir = createTempDirectory("notepen_cp_load")
            val pdfPath = dir.resolve("doc.pdf").toString()
            repo.save(pdfPath, emptyMap(), scale = 100, currentPage = 5)

            val bundle = repo.load(pdfPath).getOrThrow()

            assertEquals(5, bundle.currentPage)
        }

    // TC-3 (Step 2): legacy JSON without currentPage → bundle.currentPage == 0
    @Test
    fun load_legacyJsonWithoutCurrentPage_returnsZero() =
        runBlocking {
            val dir = createTempDirectory("notepen_cp_legacy")
            val pdfPath = dir.resolve("doc.pdf").toString()
            java.io.File("$pdfPath.notepen.json").writeText("""{"pages":{},"scale":100}""")

            val bundle = repo.load(pdfPath).getOrThrow()

            assertEquals(0, bundle.currentPage, "missing currentPage must default to 0")
        }

    // TC-4 (Step 2): currentPage round-trips through save/load
    @Test
    fun save_currentPage_roundTripsToBundle() =
        runBlocking {
            val dir = createTempDirectory("notepen_cp_rt")
            val pdfPath = dir.resolve("doc.pdf").toString()
            repo.save(pdfPath, emptyMap(), scale = 120, currentPage = 3)

            val bundle = repo.load(pdfPath).getOrThrow()

            assertEquals(3, bundle.currentPage)
            assertEquals(120, bundle.scale)
        }

    @Test
    fun loadViewState_afterSave_returnsScalePageOffset() =
        runBlocking {
            val dir = createTempDirectory("notepen_view_rt")
            val pdfPath = dir.resolve("doc.pdf").toString()
            repo.save(pdfPath, emptyMap(), scale = 175, currentPage = 4, currentPageOffset = 320)

            val view = repo.loadViewState(pdfPath).getOrThrow()

            assertEquals(175, view?.scale)
            assertEquals(4, view?.currentPage)
            assertEquals(320, view?.currentPageOffset)
        }

    @Test
    fun loadViewState_noFile_returnsNull() =
        runBlocking {
            val dir = createTempDirectory("notepen_view_empty")
            val result = repo.loadViewState(dir.resolve("missing.pdf").toString())
            assertTrue(result.isSuccess)
            assertEquals(null, result.getOrNull())
        }

    @Test
    fun save_writesSeparateViewSidecar() =
        runBlocking {
            val dir = createTempDirectory("notepen_view_file")
            val pdfPath = dir.resolve("doc.pdf").toString()
            repo.save(pdfPath, emptyMap(), scale = 110, currentPage = 2)

            assertTrue(java.io.File("$pdfPath.notepen.json.view").exists(), "view sidecar must be created")
        }

    @Test
    fun saveViewState_roundTrip_returnsReadingMode() =
        runBlocking {
            val dir = createTempDirectory("notepen_view_reading")
            val pdfPath = dir.resolve("doc.pdf").toString()
            repo.saveViewState(
                pdfPath,
                AnnotationViewState(scale = 130, currentPage = 5, currentPageOffset = 64, readingMode = true),
            )

            val view = repo.loadViewState(pdfPath).getOrThrow()

            assertEquals(130, view?.scale)
            assertEquals(5, view?.currentPage)
            assertEquals(64, view?.currentPageOffset)
            assertEquals(true, view?.readingMode)
        }

    @Test
    fun save_afterSaveViewState_preservesReadingMode() =
        runBlocking {
            val dir = createTempDirectory("notepen_view_preserve")
            val pdfPath = dir.resolve("doc.pdf").toString()
            // Режим чтения включён через лёгкий saveViewState.
            repo.saveViewState(pdfPath, AnnotationViewState(scale = 100, readingMode = true))
            // Последующий сейв штрихов (save) не передаёт readingMode — он не должен его затереть.
            repo.save(pdfPath, emptyMap(), scale = 100, currentPage = 1)

            assertEquals(true, repo.loadViewState(pdfPath).getOrThrow()?.readingMode)
        }

    @Test
    fun save_roundTrip_stickyHighlights() =
        runBlocking {
            val dir = createTempDirectory("notepen_hl_rt")
            val pdfPath = dir.resolve("doc.pdf").toString()
            val highlights =
                mapOf(
                    1 to
                        listOf(
                            StickyHighlight(
                                rects = listOf(NormalizedRect(0.1f, 0.2f, 0.5f, 0.24f)),
                                colorArgb = 0x80FFEB3BL,
                            ),
                        ),
                )

            repo.save(pdfPath, emptyMap(), scale = 100, highlights = highlights)
            val bundle = repo.load(pdfPath).getOrThrow()

            val hl = bundle.highlights[1]?.single()
            assertEquals(0x80FFEB3BL, hl?.colorArgb, "highlight ARGB Long round-trip")
            assertEquals(NormalizedRect(0.1f, 0.2f, 0.5f, 0.24f), hl?.rects?.single())
        }

    @Test
    fun load_legacyJsonWithoutHighlights_returnsEmptyMap() =
        runBlocking {
            val dir = createTempDirectory("notepen_hl_legacy")
            val pdfPath = dir.resolve("legacy.pdf").toString()
            java.io.File("$pdfPath.notepen.json").writeText("""{"pages":{},"scale":100}""")

            val bundle = repo.load(pdfPath).getOrThrow()

            assertTrue(bundle.highlights.isEmpty(), "missing highlights key must yield empty map")
        }

    @Test
    fun save_defaultMarker_persistsStickyTrue() =
        runBlocking {
            val dir = createTempDirectory("notepen_marker_sticky")
            val pdfPath = dir.resolve("doc.pdf").toString()
            repo.save(pdfPath, emptyMap(), scale = 100, marker = MarkerSettings())

            val bundle = repo.load(pdfPath).getOrThrow()

            assertTrue(bundle.marker.sticky, "default marker must persist sticky = true")
        }

    @Test
    fun load_legacyMarkerWithoutSticky_defaultsTrue() =
        runBlocking {
            val dir = createTempDirectory("notepen_marker_legacy")
            val pdfPath = dir.resolve("legacy.pdf").toString()
            java.io.File("$pdfPath.notepen.json")
                .writeText("""{"pages":{},"scale":100,"marker":{"colorArgb":255,"strokeWidth":0.025}}""")

            val bundle = repo.load(pdfPath).getOrThrow()

            assertTrue(bundle.marker.sticky, "legacy marker without sticky field must default to true")
        }

    // TC-19 surrogate: JSON produced by save/load round-trip is valid (same schema across platforms)
    @Test
    fun jsonShape_isCompatibleAcrossPlatforms() =
        runBlocking {
            val dir = createTempDirectory("notepen_cross_platform")
            val pdfPath = dir.resolve("doc.pdf").toString()
            repo.save(
                pdfPath,
                emptyMap(),
                scale = 100,
                pen = PenSettings(),
                eraser = EraserSettings(),
            )

            val bundle = repo.load(pdfPath).getOrThrow()

            assertEquals(PenSettings(), bundle.pen)
            assertEquals(EraserSettings(), bundle.eraser)
        }
}
