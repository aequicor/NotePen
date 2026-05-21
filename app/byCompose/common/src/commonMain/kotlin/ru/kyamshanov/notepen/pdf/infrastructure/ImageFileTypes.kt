package ru.kyamshanov.notepen.pdf.infrastructure

/** Расширения файлов изображений, поддерживаемых для открытия как документ. */
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg")

/** MIME-типы изображений, поддерживаемых для открытия как документ. */
private val IMAGE_MIME_TYPES = setOf("image/png", "image/jpeg")

/**
 * Определяет по расширению пути/URI, является ли файл поддерживаемым изображением.
 * Регистронезависимо. Не делает IO — годится как первичная (path-based) проверка на обеих платформах.
 */
fun isSupportedImagePath(path: String): Boolean {
    val withoutQuery = path.substringBefore('?').substringBefore('#')
    val ext = withoutQuery.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
}

/** Определяет по MIME-типу, является ли файл поддерживаемым изображением. */
fun isSupportedImageMime(mime: String?): Boolean = mime?.lowercase() in IMAGE_MIME_TYPES
