package ru.kyamshanov.notepen.book

/**
 * Источник содержимого книги, готовый к верстке в PDF: текстовый поток блоков
 * либо последовательность изображений (комикс, «в край»).
 */
internal sealed interface BookSource {
    /** Текстовая книга (EPUB/FB2) — поток [ContentBlock]. */
    data class Text(
        val content: BookContent,
    ) : BookSource

    /** Комикс (CBZ/CBR) — изображения страниц в порядке чтения. */
    data class Comic(
        val images: List<ByteArray>,
    ) : BookSource
}

/**
 * Разбирает байты книги формата [format] в [BookSource]. Единая точка
 * диспетчеризации форматов для JVM и Android: новый формат добавляется
 * значением в [BookFormat] и веткой здесь.
 *
 * @param bytes полное содержимое файла книги
 * @param format формат, определённый через [detectBookFormat]
 */
internal fun readBookSource(
    bytes: ByteArray,
    format: BookFormat,
): BookSource =
    when (format) {
        BookFormat.EPUB -> BookSource.Text(EpubParser.parse(bytes))
        BookFormat.FB2 -> BookSource.Text(Fb2Parser.parse(bytes))
        BookFormat.CBZ -> BookSource.Comic(ComicArchive.extract(bytes, BookFormat.CBZ))
        BookFormat.CBR -> BookSource.Comic(ComicArchive.extract(bytes, BookFormat.CBR))
    }
