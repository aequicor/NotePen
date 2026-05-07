package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * Утилита нормализации URI для использования в качестве первичного ключа [RecentFile].
 *
 * Правила:
 * - Android `content://` URI возвращается без изменений (SAF URI не требует NFC).
 * - Desktop-путь: обрезается trailing slash/backslash, применяется NFC-нормализация Unicode.
 */
object UriNormalizer {

    /**
     * Нормализует URI.
     *
     * Desktop: применяет NFC-нормализацию Unicode и удаляет trailing slash/backslash.
     * Android: content:// URI возвращается as-is.
     */
    fun normalize(uri: String): String =
        if (uri.startsWith("content://")) {
            uri
        } else {
            normalizeUnicode(uri.trimEnd('/', '\\'))
        }
}
