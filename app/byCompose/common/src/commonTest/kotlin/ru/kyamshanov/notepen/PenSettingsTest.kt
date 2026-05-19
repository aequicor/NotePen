package ru.kyamshanov.notepen

import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// covers TC-10, TC-11, TC-13, TC-14

class PenSettingsTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    // TC-10: PenSettings defaults: black, normalised text-thickness stroke, full alpha
    @Test
    fun penSettings_defaults_areBlackTextThicknessAndFullAlpha() {
        val s = PenSettings()
        assertEquals(DrawingPath.BLACK_ARGB, s.colorArgb, "default color must be Black (ARGB)")
        assertEquals(
            PenSettings.DEFAULT_STROKE_WIDTH,
            s.strokeWidth,
            "default strokeWidth must match DEFAULT_STROKE_WIDTH",
        )
        assertEquals(1f, s.alpha, "default alpha must be 1f")
        assertTrue(
            PenSettings.DEFAULT_STROKE_WIDTH in
                PenSettings.MIN_STROKE_WIDTH..PenSettings.MAX_STROKE_WIDTH,
            "default must lie within slider range",
        )
        assertTrue(PenSettings.PRESET_COLORS.isNotEmpty(), "PRESET_COLORS must not be empty")
    }

    // TC-13: PenSettings round-trip serialization preserves colorArgb and alpha
    @Test
    fun penSettings_roundTripSerialization_preservesAlpha() {
        val original = PenSettings(
            colorArgb = 0x80FF0000L,
            strokeWidth = 0.01f,
            alpha = 0.5f,
        )

        val text = json.encodeToString(PenSettings.serializer(), original)
        val decoded = json.decodeFromString(PenSettings.serializer(), text)

        assertEquals(original.strokeWidth, decoded.strokeWidth)
        assertEquals(original.alpha, decoded.alpha)
        assertEquals(original.colorArgb, decoded.colorArgb, "ARGB Long must round-trip including alpha channel")
    }

    // TC-11: EraserSettings defaults: shape=CIRCLE, sizeNormalized=DEFAULT_SIZE_NORMALIZED
    @Test
    fun eraserSettings_defaults_areCircleAndDefaultSize() {
        val s = EraserSettings()
        assertEquals(EraserShape.CIRCLE, s.shape)
        assertEquals(EraserSettings.DEFAULT_SIZE_NORMALIZED, s.sizeNormalized)
        assertTrue(
            EraserSettings.MIN_SIZE_NORMALIZED < EraserSettings.MAX_SIZE_NORMALIZED,
            "MIN < MAX size constraint",
        )
    }

    // TC-14: EraserSettings round-trip serialization
    @Test
    fun eraserSettings_roundTripSerialization() {
        val original = EraserSettings(shape = EraserShape.SQUARE, sizeNormalized = 0.1f)

        val text = json.encodeToString(EraserSettings.serializer(), original)
        val decoded = json.decodeFromString(EraserSettings.serializer(), text)

        assertEquals(original, decoded)
    }
}
