package ru.kyamshanov.notepen

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfPageExpansionOverlayLogicTest {
    @Test
    fun `left expansion button keeps preferred position when page edge is visible`() {
        assertEquals(
            144f,
            pageExpansionLeftButtonX(
                leftEdgeX = 200f,
                viewportWidth = 600f,
                buttonSize = 48f,
                padding = 8f,
            ),
        )
    }

    @Test
    fun `left expansion button stays visible when page edge is left of viewport`() {
        assertEquals(
            0f,
            pageExpansionLeftButtonX(
                leftEdgeX = -120f,
                viewportWidth = 600f,
                buttonSize = 48f,
                padding = 8f,
            ),
        )
    }

    @Test
    fun `right expansion button keeps preferred position when page edge is visible`() {
        assertEquals(
            208f,
            pageExpansionRightButtonX(
                rightEdgeX = 200f,
                viewportWidth = 600f,
                buttonSize = 48f,
                padding = 8f,
            ),
        )
    }

    @Test
    fun `right expansion button stays visible when page edge is right of viewport`() {
        assertEquals(
            552f,
            pageExpansionRightButtonX(
                rightEdgeX = 900f,
                viewportWidth = 600f,
                buttonSize = 48f,
                padding = 8f,
            ),
        )
    }

    @Test
    fun `expansion buttons do not produce negative x in too narrow viewport`() {
        assertEquals(
            0f,
            pageExpansionLeftButtonX(
                leftEdgeX = 20f,
                viewportWidth = 24f,
                buttonSize = 48f,
                padding = 8f,
            ),
        )
        assertEquals(
            0f,
            pageExpansionRightButtonX(
                rightEdgeX = 20f,
                viewportWidth = 24f,
                buttonSize = 48f,
                padding = 8f,
            ),
        )
    }
}
