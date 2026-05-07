package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * Применяет нормализацию Unicode NFC к строке.
 *
 * Android: SAF URI-ы не содержат разложенных символов Unicode — возвращается строка без изменений.
 * JVM (Desktop): использует [java.text.Normalizer] с формой NFC.
 *
 * Объявлено как expect/actual чтобы не тащить JVM API в commonMain (ADR-001).
 */
expect fun normalizeUnicode(s: String): String
