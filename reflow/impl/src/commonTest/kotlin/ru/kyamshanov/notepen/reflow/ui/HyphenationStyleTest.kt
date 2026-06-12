package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Проверяет инвариант: при hyphenation=true стили параграфа и сноски используют
 * LineBreak.Paragraph (Strategy.HighQuality) — без этого Hyphens.Auto не работает
 * (Strategy.Simple делает перенос только у слов длиннее всей строки). При false —
 * Unspecified, чтобы пагинация оставалась идентична предыдущим версиям.
 */
class HyphenationStyleTest {
    private val base = ReflowReaderSettings()

    @Test
    fun paragraphStyle_hyphenationOn_usesHighQualityBreaker() {
        val style = base.copy(hyphenation = true).paragraphStyle()
        assertEquals(Hyphens.Auto, style.hyphens)
        assertEquals(LineBreak.Paragraph, style.lineBreak)
    }

    @Test
    fun paragraphStyle_hyphenationOff_usesDefaultBreaker() {
        val style = base.copy(hyphenation = false).paragraphStyle()
        assertEquals(Hyphens.None, style.hyphens)
        assertEquals(LineBreak.Unspecified, style.lineBreak)
    }

    @Test
    fun footnoteStyle_hyphenationOn_usesHighQualityBreaker() {
        val style = base.copy(hyphenation = true).footnoteStyle()
        assertEquals(Hyphens.Auto, style.hyphens)
        assertEquals(LineBreak.Paragraph, style.lineBreak)
    }

    @Test
    fun footnoteStyle_hyphenationOff_usesDefaultBreaker() {
        val style = base.copy(hyphenation = false).footnoteStyle()
        assertEquals(Hyphens.None, style.hyphens)
        assertEquals(LineBreak.Unspecified, style.lineBreak)
    }
}
