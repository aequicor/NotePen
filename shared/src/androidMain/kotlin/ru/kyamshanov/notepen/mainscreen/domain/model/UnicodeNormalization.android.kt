package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * Android: SAF URI-ы не содержат разложенных символов Unicode.
 * Возвращается строка без изменений.
 */
actual fun normalizeUnicode(s: String): String = s
