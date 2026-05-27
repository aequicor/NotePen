package ru.kyamshanov.notepen.book

/**
 * Распарсенная книга: метаданные и линейный поток блоков в порядке чтения.
 *
 * Модель платформенно-нейтральна: её наполняют парсеры форматов (например,
 * [EpubParser]), а потребляют платформенные рендереры в PDF. Типографику
 * (шрифт, кегль, отступы) применяет рендерер — в модель она не зашивается.
 *
 * @property metadata метаданные книги
 * @property blocks блоки в порядке чтения (склейка всех документов источника)
 * @property fonts встроенные шрифты (TTF/OTF, уже деобфусцированные); пусто для
 *   форматов без встраивания (FB2) и комиксов
 */
data class BookContent(
    val metadata: BookMetadata,
    val blocks: List<ContentBlock>,
    val fonts: List<ByteArray> = emptyList(),
)

/**
 * Метаданные книги.
 *
 * @property title заголовок или `null`, если не указан
 * @property author автор или `null`
 * @property language язык (BCP-47) или `null`
 * @property identifier уникальный идентификатор книги или `null`
 */
data class BookMetadata(
    val title: String? = null,
    val author: String? = null,
    val language: String? = null,
    val identifier: String? = null,
)

/**
 * Форматированный инлайн-текст: последовательность [InlineSpan] одного блока.
 */
typealias RichText = List<InlineSpan>

/**
 * Фрагмент инлайн-текста с начертанием. Несколько фрагментов составляют
 * [RichText] одного блока (абзаца, цитаты, пункта списка).
 *
 * @property text сам текст фрагмента (последовательности пробелов уже схлопнуты)
 * @property bold полужирное начертание (`b`/`strong`)
 * @property italic курсив (`i`/`em`)
 * @property code моноширинный код (`code`/`kbd`/`samp`)
 * @property superscript верхний индекс (`sup`)
 * @property subscript нижний индекс (`sub`)
 * @property link фрагмент-ссылка (`a`) — оформляется визуально (цвет/подчёркивание), без перехода
 */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val superscript: Boolean = false,
    val subscript: Boolean = false,
    val link: Boolean = false,
)

/** Текст всех фрагментов без начертания — для поиска, тестов и фолбэков. */
fun RichText.plainText(): String = joinToString(separator = "") { it.text }

/**
 * Один блок содержимого книги. Иерархия закрытая (`sealed`) — расширение
 * новыми типами блоков, а не флагами.
 */
sealed interface ContentBlock {
    /**
     * Заголовок раздела (`h1`…`h6`).
     *
     * @property level уровень от 1 (самый крупный) до 6
     * @property text текст заголовка с уже снятой внутренней разметкой
     */
    data class Heading(
        val level: Int,
        val text: String,
    ) : ContentBlock

    /**
     * Абзац основного текста (`p` и блочные контейнеры без спец-семантики).
     *
     * Мягкие переносы строк сняты, последовательности пробелов схлопнуты —
     * текст готов к повторной верстке под произвольную ширину.
     *
     * @property text форматированный текст абзаца
     */
    data class Paragraph(
        val text: RichText,
    ) : ContentBlock

    /**
     * Элемент списка (`li`). Маркер намеренно не зашит в [text] — его
     * подставляет рендерер исходя из [ordered] и [level].
     *
     * @property text форматированный текст элемента
     * @property ordered элемент нумерованного (`ol`) списка; иначе маркированный (`ul`)
     * @property ordinal порядковый номер в нумерованном списке (с 1); для
     *   маркированного игнорируется
     * @property level глубина вложенности списка с 0 — управляет втяжкой
     */
    data class ListItem(
        val text: RichText,
        val ordered: Boolean,
        val ordinal: Int,
        val level: Int,
    ) : ContentBlock

    /**
     * Цитата (`blockquote`) — рендерится с втяжкой/выделением.
     *
     * @property text форматированный текст цитаты
     */
    data class Blockquote(
        val text: RichText,
    ) : ContentBlock

    /**
     * Растровое изображение (`img`/`image`), извлечённое из контейнера книги.
     *
     * @property data байты изображения в исходном формате (PNG/JPEG/GIF…)
     * @property mimeType MIME-тип из манифеста (например, `image/jpeg`)
     * @property alt альтернативный текст (`alt`) или `null`
     */
    data class Image(
        val data: ByteArray,
        val mimeType: String,
        val alt: String? = null,
    ) : ContentBlock {
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
    data class Table(
        val rows: List<List<String>>,
    ) : ContentBlock

    /** Горизонтальный разделитель (`hr`). */
    data object HorizontalRule : ContentBlock

    /**
     * Граница документа источника (начало новой главы) — рендерер начинает с
     * неё новую страницу PDF.
     */
    data object PageBreak : ContentBlock
}
