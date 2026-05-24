package ru.kyamshanov.notepen.reflow

import ru.kyamshanov.notepen.reflow.api.ReflowRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FigureGeometryTest {

    @Test
    fun `axis-aligned image maps to top-left rect`() {
        // Изображение 200×100, сдвинуто в (50, 300) пользовательского пространства, страница высотой 800.
        val rect = FigureGeometry.imageRectFromCtm(
            scaleX = 200f,
            shearY = 0f,
            shearX = 0f,
            scaleY = 100f,
            translateX = 50f,
            translateY = 300f,
            pageHeightPt = 800f,
        )
        assertEquals(50f, rect.left)
        assertEquals(250f, rect.right)
        assertEquals(400f, rect.top) // 800 - (300 + 100)
        assertEquals(500f, rect.bottom) // 800 - 300
    }

    @Test
    fun `full-page image is detected and small one is not`() {
        assertTrue(FigureGeometry.isFullPage(ReflowRect(0f, 0f, 580f, 790f), 600f, 800f))
        assertFalse(FigureGeometry.isFullPage(ReflowRect(50f, 100f, 250f, 300f), 600f, 800f))
    }

    @Test
    fun `tiny decoration is filtered, sizable figure is kept`() {
        assertTrue(FigureGeometry.isTooSmall(ReflowRect(60f, 100f, 72f, 111f), 600f, 800f)) // ~12x11pt
        assertFalse(FigureGeometry.isTooSmall(ReflowRect(50f, 100f, 300f, 250f), 600f, 800f)) // 250x150pt
    }
}
