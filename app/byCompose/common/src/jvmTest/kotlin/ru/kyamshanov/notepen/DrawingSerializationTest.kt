package ru.kyamshanov.notepen

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
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
            color = Color.Red,
            strokeWidth = 5f
        )
        val encoded = json.encodeToString(DrawingPath.serializer(), original)
        val decoded = json.decodeFromString(DrawingPath.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun drawingPath_nonDefaultColor_encodesColorKey() {
        val path = DrawingPath(color = Color.Blue)
        val encoded = json.encodeToString(DrawingPath.serializer(), path)
        assertTrue(encoded.contains("\"color\""), "JSON must contain color key: $encoded")
    }

    @Test
    fun colorAsLongSerializer_transparentColor_roundTrip() {
        val original = Color(0x80FF0000.toInt())
        val encoded = json.encodeToString(ColorAsLongSerializer, original)
        val decoded = json.decodeFromString(ColorAsLongSerializer, encoded)
        assertEquals(original, decoded)
    }
}
