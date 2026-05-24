package ru.kyamshanov.notepen.pdf.infrastructure

/** Расширения файлов EPUB, поддерживаемых для открытия как документ. */
private val EPUB_EXTENSIONS = setOf("epub")

/** MIME-типы EPUB, поддерживаемых для открытия как документ. */
private val EPUB_MIME_TYPES = setOf("application/epub+zip", "application/epub")

/**
 * Определяет по расширению пути/URI, является ли файл EPUB.
 * Регистронезависимо. Не делает IO — годится как первичная (path-based) проверка на обеих платформах.
 */
fun isEpubPath(path: String): Boolean {
    val withoutQuery = path.substringBefore('?').substringBefore('#')
    val ext = withoutQuery.substringAfterLast('.', "").lowercase()
    return ext in EPUB_EXTENSIONS
}

/** Определяет по MIME-типу, является ли файл EPUB. */
fun isEpubMime(mime: String?): Boolean = mime?.lowercase() in EPUB_MIME_TYPES
