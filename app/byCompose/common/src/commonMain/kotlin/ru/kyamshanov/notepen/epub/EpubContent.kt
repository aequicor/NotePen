package ru.kyamshanov.notepen.epub

/**
 * Распарсенная книга EPUB: метаданные и линейный поток блоков в порядке чтения.
 *
 * Модель платформенно-нейтральна: её наполняет
 * [ru.kyamshanov.notepen.epub.EpubParser], а потребляют платформенные
 * рендереры в PDF. Типографику (шрифт, кегль, отступы) применяет рендерер —
 * в модель она не зашивается.
 *
 * @property metadata метаданные книги
 * @property blocks блоки в порядке чтения (склейка всех документов spine)
 */
data class EpubBook(
    val metadata: EpubMetadata,
    val blocks: List<EpubBlock>,
)

/**
 * Метаданные EPUB из OPF (`<metadata>`).
 *
 * @property title заголовок (`dc:title`) или `null`, если не указан
 * @property author автор (`dc:creator`) или `null`
 * @property language язык (`dc:language`, BCP-47) или `null`
 * @property identifier уникальный идентификатор книги (`dc:identifier`) или `null`
 */
data class EpubMetadata(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
    val identifier: String? = null,
)

/**
 * Один блок содержимого EPUB. Иерархия закрытая (`sealed`) — расширение
 * новыми типами блоков, а не флагами.
 */
sealed interface EpubBlock {

    /**
     * Заголовок раздела (`h1`…`h6`).
     *
     * @property level уровень от 1 (самый крупный) до 6
     * @property text текст заголовка с уже снятой внутренней разметкой
     */
    data class Heading(val level: Int, val text: String) : EpubBlock

    /**
     * Абзац основного текста (`p` и блочные контейнеры без спец-семантики).
     *
     * Мягкие переносы строк сняты, последовательности пробелов схлопнуты —
     * текст готов к повторной верстке под произвольную ширину.
     *
     * @property text текст абзаца
     */
    data class Paragraph(val text: String) : EpubBlock

    /**
     * Элемент списка (`li`). Маркер намеренно не зашит в [text] — его
     * подставляет рендерер исходя из [ordered] и [level].
     *
     * @property text текст элемента
     * @property ordered элемент нумерованного (`ol`) списка; иначе маркированный (`ul`)
     * @property ordinal порядковый номер в нумерованном списке (с 1); для
     *   маркированного игнорируется
     * @property level глубина вложенности списка с 0 — управляет втяжкой
     */
    data class ListItem(
        val text: String,
        val ordered: Boolean,
        val ordinal: Int,
        val level: Int,
    ) : EpubBlock

    /**
     * Цитата (`blockquote`) — рендерится с втяжкой/выделением.
     *
     * @property text текст цитаты
     */
    data class Blockquote(val text: String) : EpubBlock

    /**
     * Растровое изображение (`img`/`image`), извлечённое из контейнера EPUB.
     *
     * @property data байты изображения в исходном формате (PNG/JPEG/GIF…)
     * @property mimeType MIME-тип из манифеста (например, `image/jpeg`)
     * @property alt альтернативный текст (`alt`) или `null`
     */
    data class Image(
        val data: ByteArray,
        val mimeType: String,
        val alt: String? = null,
    ) : EpubBlock {
        // ByteArray использует identity-равенство; для блоков потока этого
        // достаточно — они не служат ключами и не сравниваются по значению.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return data.contentEquals(other.data) && mimeType == other.mimeType && alt == other.alt
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + (alt?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * Таблица (`table`), восстановленная построчно. Объединённые ячейки не
     * поддерживаются — каждая строка это плоский список ячеек.
     *
     * @property rows строки сверху вниз; первая обычно заголовочная
     */
    data class Table(val rows: List<List<String>>) : EpubBlock

    /** Горизонтальный разделитель (`hr`). */
    data object HorizontalRule : EpubBlock

    /**
     * Граница документа spine (начало новой главы) — рендерер начинает с неё
     * новую страницу PDF.
     */
    data object PageBreak : EpubBlock
}
