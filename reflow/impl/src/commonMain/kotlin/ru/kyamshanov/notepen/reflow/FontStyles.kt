package ru.kyamshanov.notepen.reflow

/**
 * Эвристика начертания по имени PDF-шрифта. Имя обычно приходит с префиксом
 * сабсета (`BAAAAA+HelveticaNeue-Bold`), поэтому сопоставление — по вхождению
 * подстроки без учёта регистра.
 *
 * Вынесено отдельно (а не в экстракторы), чтобы быть платформенно-нейтральной
 * чистой логикой, покрытой unit-тестами и общей для PDFBox и PdfBox-Android.
 */
internal object FontStyles {
    /** Маркеры полужирного начертания в имени шрифта. */
    private val BOLD_MARKERS = listOf("bold", "black", "heavy")

    /**
     * Маркеры курсивного начертания в имени шрифта. Postscript-имена
     * обычно содержат «Italic» (например, `BAAAAA+HelveticaNeue-Italic`,
     * `TimesNewRomanPS-ItalicMT`) или, реже, «Oblique» (для гротесков).
     */
    private val ITALIC_MARKERS = listOf("italic", "oblique")

    /** Маркеры моноширинных шрифтов (типичны для inline-кода). */
    private val MONOSPACE_MARKERS = listOf("mono", "menlo", "courier", "consol")

    /** Полужирный ли шрифт [fontName] (`null`/неизвестно → `false`). */
    fun isBold(fontName: String?): Boolean = fontName.matchesAny(BOLD_MARKERS)

    /** Курсивный ли шрифт [fontName] (`null`/неизвестно → `false`). */
    fun isItalic(fontName: String?): Boolean = fontName.matchesAny(ITALIC_MARKERS)

    /** Моноширинный ли шрифт [fontName] (`null`/неизвестно → `false`). */
    fun isMonospace(fontName: String?): Boolean = fontName.matchesAny(MONOSPACE_MARKERS)

    private fun String?.matchesAny(markers: List<String>): Boolean = this != null && markers.any { contains(it, ignoreCase = true) }
}
