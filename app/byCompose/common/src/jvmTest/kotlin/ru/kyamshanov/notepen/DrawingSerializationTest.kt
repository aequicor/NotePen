package ru.kyamshanov.notepen

import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawingSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun drawingPoint_roundTrip_preservesAllFields() {
        val original = DrawingPoint(x = 12.5f, y = 34.0f, isNewPath = true)
        val encoded = json.encodeToString(DrawingPoint.serializer(), original)
        val decoded = json.decodeFromString(DrawingPoint.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun drawingPath_roundTrip_preservesColorAndStroke() {
        val original = DrawingPath(
            points = listOf(DrawingPoint(1f, 2f, true), DrawingPoint(3f, 4f)),
            colorArgb = 0xFFE53935L,
            strokeWidth = 5f,
        )
        val encoded = json.encodeToString(DrawingPath.serializer(), original)
        val decoded = json.decodeFromString(DrawingPath.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun drawingPath_nonDefaultColor_encodesColorArgbKey() {
        val path = DrawingPath(colorArgb = 0xFF1E88E5L)
        val encoded = json.encodeToString(DrawingPath.serializer(), path)
        assertTrue(encoded.contains("\"colorArgb\""), "JSON must contain colorArgb key: $encoded")
    }

    @Test
    fun drawingPath_transparentColor_roundTrip() {
        val original = DrawingPath(colorArgb = 0x80FF0000L)
        val encoded = json.encodeToString(DrawingPath.serializer(), original)
        val decoded = json.decodeFromString(DrawingPath.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
