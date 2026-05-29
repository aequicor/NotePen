package ru.kyamshanov.notepen.appsettings

import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.appsettings.domain.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Round-trip + backward-compatibility of [AppSettings] JSON, including the M2b
 * `openLibraryAtStartup` flag.
 */
class AppSettingsSerializationTest {
    // Mirrors the repositories' config: ignore unknown keys, encode defaults.
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    @Test
    fun roundTrip_preservesAllFields() {
        val settings = AppSettings(alwaysOnDisplay = false, openLibraryAtStartup = true)
        val text = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), text)
        assertEquals(settings, decoded)
    }

    @Test
    fun oldJson_withoutNewFlag_deserializesToFalse() {
        // JSON written by an app version that predates openLibraryAtStartup.
        val legacy = """{"alwaysOnDisplay":true}"""
        val decoded = json.decodeFromString(AppSettings.serializer(), legacy)
        assertTrue(decoded.alwaysOnDisplay, "existing field preserved")
        assertFalse(decoded.openLibraryAtStartup, "missing new flag defaults to false")
    }

    @Test
    fun emptyJson_yieldsAllDefaults() {
        val decoded = json.decodeFromString(AppSettings.serializer(), "{}")
        assertEquals(AppSettings(), decoded)
        assertTrue(decoded.alwaysOnDisplay)
        assertFalse(decoded.openLibraryAtStartup)
    }

    @Test
    fun encodedJson_containsBothFlags() {
        val text = json.encodeToString(AppSettings.serializer(), AppSettings())
        assertTrue(text.contains("alwaysOnDisplay"))
        assertTrue(text.contains("openLibraryAtStartup"))
    }
}
