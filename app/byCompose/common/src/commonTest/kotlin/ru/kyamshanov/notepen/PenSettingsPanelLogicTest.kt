package ru.kyamshanov.notepen

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit-tests for the pure state-mapping helpers used by [PenSettingsPanel]
 * and [EraserSettingsPanel]. The composable surface itself is verified
 * manually (TC-20, TC-21) — compose-ui-test infra is tracked as tech-debt
 * in vault/tech-debt/common/compose-ui-test-infra.md.
 */
class PenSettingsPanelLogicTest {

    @Test
    fun `applyPreset replaces RGB but preserves current alpha`() {
        // Verifies AC-8 + EC-9: switching a preset must keep the alpha slider value.
        val current = PenSettings(color = Color.Black.copy(alpha = 0.5f), alpha = 0.5f)
        val red = Color(0xFFE53935)

        val updated = current.applyPreset(red)

        assertEquals(red.copy(alpha = 0.5f), updated.color)
        assertEquals(0.5f, updated.alpha)
        assertEquals(current.strokeWidth, updated.strokeWidth)
    }

    @Test
    fun `applyAlpha rebuilds color with new alpha and stores alpha verbatim`() {
        // Verifies AC-7: alpha slider drives both PenSettings.alpha and the
        // alpha channel of PenSettings.color.
        val current = PenSettings(color = Color(0xFFE53935), alpha = 1f)

        val updated = current.applyAlpha(0.3f)

        assertEquals(0.3f, updated.alpha)
        assertEquals(0.3f, updated.color.alpha, absoluteTolerance = 0.01f)
        // Red / green / blue components untouched.
        assertEquals(current.color.red, updated.color.red, absoluteTolerance = 0.01f)
        assertEquals(current.color.green, updated.color.green, absoluteTolerance = 0.01f)
        assertEquals(current.color.blue, updated.color.blue, absoluteTolerance = 0.01f)
    }

    @Test
    fun `applyStrokeWidth clamps below MIN and above MAX`() {
        val current = PenSettings()

        val tooSmall = current.applyStrokeWidth(PenSettings.MIN_STROKE_WIDTH - 5f)
        val tooLarge = current.applyStrokeWidth(PenSettings.MAX_STROKE_WIDTH + 5f)
        val ok = current.applyStrokeWidth(15f)

        assertEquals(PenSettings.MIN_STROKE_WIDTH, tooSmall.strokeWidth)
        assertEquals(PenSettings.MAX_STROKE_WIDTH, tooLarge.strokeWidth)
        assertEquals(15f, ok.strokeWidth)
    }

    @Test
    fun `applyAlpha clamps below 0 and above 1`() {
        val current = PenSettings()

        val tooSmall = current.applyAlpha(-0.5f)
        val tooLarge = current.applyAlpha(1.5f)

        assertEquals(0f, tooSmall.alpha)
        assertEquals(1f, tooLarge.alpha)
    }

    @Test
    fun `EraserSettings applyShape switches shape and preserves size`() {
        val current = EraserSettings(shape = EraserShape.CIRCLE, sizeNormalized = 0.07f)

        val updated = current.applyShape(EraserShape.SQUARE)

        assertEquals(EraserShape.SQUARE, updated.shape)
        assertEquals(0.07f, updated.sizeNormalized)
    }

    @Test
    fun `EraserSettings applySize clamps to the valid range`() {
        val current = EraserSettings()

        val tooSmall = current.applySize(EraserSettings.MIN_SIZE_NORMALIZED - 0.01f)
        val tooLarge = current.applySize(EraserSettings.MAX_SIZE_NORMALIZED + 0.5f)
        val ok = current.applySize(0.10f)

        // Verifies EC-10 / EC-11.
        assertEquals(EraserSettings.MIN_SIZE_NORMALIZED, tooSmall.sizeNormalized)
        assertEquals(EraserSettings.MAX_SIZE_NORMALIZED, tooLarge.sizeNormalized)
        assertEquals(0.10f, ok.sizeNormalized)
    }

    @Test
    fun `PRESET_COLORS are all fully opaque`() {
        // Spec § 1: presets are fully opaque; alpha is applied separately.
        for (preset in PenSettings.PRESET_COLORS) {
            assertTrue(
                preset.alpha == 1f,
                "Preset $preset must be fully opaque so the alpha slider stays the single source of truth for transparency",
            )
        }
    }
}
