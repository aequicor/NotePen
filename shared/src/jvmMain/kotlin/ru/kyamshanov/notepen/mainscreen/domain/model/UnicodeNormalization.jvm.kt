package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * JVM (Desktop): нормализует строку в форму NFC с помощью [java.text.Normalizer].
 * Преобразует разложенные символы Unicode (NFD) в составные (NFC).
 */
actual fun normalizeUnicode(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
