package ru.kyamshanov.notepen.reflow

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontStylesTest {
    @Test
    fun `bold is detected from subset-prefixed font name`() {
        assertTrue(FontStyles.isBold("DAAAAA+HelveticaNeue-Bold"))
        assertTrue(FontStyles.isBold("Arial-BoldMT"))
        assertTrue(FontStyles.isBold("SomeFont-Black"))
    }

    @Test
    fun `regular and null fonts are not bold`() {
        assertFalse(FontStyles.isBold("BAAAAA+HelveticaNeue"))
        assertFalse(FontStyles.isBold("Times-Roman"))
        assertFalse(FontStyles.isBold(null))
    }

    @Test
    fun `monospace is detected for common code fonts`() {
        assertTrue(FontStyles.isMonospace("CAAAAA+Menlo-Regular"))
        assertTrue(FontStyles.isMonospace("Courier"))
        assertTrue(FontStyles.isMonospace("Consolas"))
        assertTrue(FontStyles.isMonospace("RobotoMono-Regular"))
    }

    @Test
    fun `proportional and null fonts are not monospace`() {
        assertFalse(FontStyles.isMonospace("HelveticaNeue"))
        assertFalse(FontStyles.isMonospace(null))
    }

    @Test
    fun `italic is detected from subset-prefixed font name`() {
        assertTrue(FontStyles.isItalic("BAAAAA+HelveticaNeue-Italic"))
        assertTrue(FontStyles.isItalic("TimesNewRomanPS-ItalicMT"))
        assertTrue(FontStyles.isItalic("Arial-BoldItalicMT"))
        assertTrue(FontStyles.isItalic("Helvetica-Oblique"))
    }

    @Test
    fun `regular and null fonts are not italic`() {
        assertFalse(FontStyles.isItalic("BAAAAA+HelveticaNeue"))
        assertFalse(FontStyles.isItalic("Times-Roman"))
        assertFalse(FontStyles.isItalic(null))
    }
}
