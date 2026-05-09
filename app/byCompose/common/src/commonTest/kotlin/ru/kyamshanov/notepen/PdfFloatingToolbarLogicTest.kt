package ru.kyamshanov.notepen

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit-tests for pure state-mapping helpers used by [PdfFloatingToolbar].
 *
 * The composable surface (rendering of `Surface`, `IconButton`, embedded
 * panels) is verified manually — TC-22 / TC-23 / TC-24 — because the
 * project does not yet have compose-ui-test infrastructure
 * (tracked in `vault/tech-debt/common/compose-ui-test-infra.md`).
 *
 * The tested helper is the single non-trivial piece of logic in the
 * toolbar — toggle semantics for the Pen / Eraser buttons (AC-2, AC-3):
 * clicking an inactive tool switches to it; clicking the currently active
 * tool returns to [ToolMode.NONE].
 */
class PdfFloatingToolbarLogicTest {

    @Test
    fun `clicking PEN when current is NONE switches to PEN`() {
        // AC-2: pen toggle activates pen.
        assertEquals(
            ToolMode.PEN,
            nextToolModeOnToggle(current = ToolMode.NONE, requested = ToolMode.PEN),
        )
    }

    @Test
    fun `clicking ERASER when current is NONE switches to ERASER`() {
        // AC-3: eraser toggle activates eraser.
        assertEquals(
            ToolMode.ERASER,
            nextToolModeOnToggle(current = ToolMode.NONE, requested = ToolMode.ERASER),
        )
    }

    @Test
    fun `clicking PEN when PEN is already active returns NONE`() {
        // AC-2 toggle: re-tap on active tool deactivates.
        assertEquals(
            ToolMode.NONE,
            nextToolModeOnToggle(current = ToolMode.PEN, requested = ToolMode.PEN),
        )
    }

    @Test
    fun `clicking ERASER when ERASER is already active returns NONE`() {
        // AC-3 toggle: re-tap on active tool deactivates.
        assertEquals(
            ToolMode.NONE,
            nextToolModeOnToggle(current = ToolMode.ERASER, requested = ToolMode.ERASER),
        )
    }

    @Test
    fun `clicking PEN when ERASER is active switches to PEN (mutual exclusion)`() {
        // AC-2 + AC-3: tools are mutually exclusive — not toggle-NONE in between.
        assertEquals(
            ToolMode.PEN,
            nextToolModeOnToggle(current = ToolMode.ERASER, requested = ToolMode.PEN),
        )
    }

    @Test
    fun `clicking ERASER when PEN is active switches to ERASER (mutual exclusion)`() {
        assertEquals(
            ToolMode.ERASER,
            nextToolModeOnToggle(current = ToolMode.PEN, requested = ToolMode.ERASER),
        )
    }

    @Test
    fun `requesting NONE always returns NONE regardless of current`() {
        // Defensive: callers that want explicit deactivation pass NONE.
        assertEquals(ToolMode.NONE, nextToolModeOnToggle(ToolMode.NONE, ToolMode.NONE))
        assertEquals(ToolMode.NONE, nextToolModeOnToggle(ToolMode.PEN, ToolMode.NONE))
        assertEquals(ToolMode.NONE, nextToolModeOnToggle(ToolMode.ERASER, ToolMode.NONE))
    }
}
