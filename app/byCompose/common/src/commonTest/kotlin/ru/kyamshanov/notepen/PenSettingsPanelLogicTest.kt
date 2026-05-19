package ru.kyamshanov.notepen

import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.EraserShape
import ru.kyamshanov.notepen.annotation.domain.model.applyAlpha
import ru.kyamshanov.notepen.annotation.domain.model.applyPreset
import ru.kyamshanov.notepen.annotation.domain.model.applyShape
import ru.kyamshanov.notepen.annotation.domain.model.applySize
import ru.kyamshanov.notepen.annotation.domain.model.applyStrokeWidth
import kotlin.math.roundToInt
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
        // 0x80000000L = black with alpha 0x80 (128/255 ≈ 0.5)
        val current = PenSettings(colorArgb = 0x80000000L, alpha = 0.5f)
        val presetArgb = 0xFFE53935L

        val updated = current.applyPreset(presetArgb)

        // RGB should be E53935, alpha byte preserved (0x80)
        assertEquals(0x80E53935L, updated.colorArgb)
        assertEquals(0.5f, updated.alpha)
        assertEquals(current.strokeWidth, updated.strokeWidth)
    }

    @Test
    fun `applyAlpha rebuilds colorArgb with new alpha and stores alpha verbatim`() {
        // Verifies AC-7: alpha slider drives both PenSettings.alpha and the
        // alpha channel of PenSettings.colorArgb.
        val current = PenSettings(colorArgb = 0xFFE53935L, alpha = 1f)

        val updated = current.applyAlpha(0.3f)

        assertEquals(0.3f, updated.alpha)
        // Alpha byte in colorArgb must match the clamped alpha
        val alphaByte = (updated.colorArgb shr 24) and 0xFFL
        val expectedAlphaByte = (0.3f * 255).roundToInt().toLong()
        assertEquals(expectedAlphaByte, alphaByte)
        // RGB channels untouched
        assertEquals(0xE53935L, updated.colorArgb and 0x00FFFFFFL)
    }

    @Test
    fun `applyStrokeWidth clamps below MIN and above MAX`() {
        val current = PenSettings()

        val tooSmall = current.applyStrokeWidth(PenSettings.MIN_STROKE_WIDTH / 2f)
        val tooLarge = current.applyStrokeWidth(PenSettings.MAX_STROKE_WIDTH * 1.5f)
        val ok = current.applyStrokeWidth(0.005f)

        assertEquals(PenSettings.MIN_STROKE_WIDTH, tooSmall.strokeWidth)
        assertEquals(PenSettings.MAX_STROKE_WIDTH, tooLarge.strokeWidth)
        assertEquals(0.005f, ok.strokeWidth)
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
        for (presetArgb in PenSettings.PRESET_COLORS) {
            val alphaByte = (presetArgb shr 24) and 0xFFL
            assertEquals(
                0xFFL,
                alphaByte,
                "Preset 0x${presetArgb.toString(16)} must be fully opaque so the alpha slider stays the single source of truth for transparency",
            )
        }
    }
}
