package ru.kyamshanov.notepen.book

import kotlinx.serialization.Serializable

/**
 * Запись оглавления: заголовок раздела и страница PDF, с которой он начинается.
 *
 * @property level уровень вложенности (1 — верхний), как у [ContentBlock.Heading]
 * @property title текст заголовка
 * @property pageIndex 0-based индекс страницы PDF, где начинается раздел
 */
@Serializable
data class TocEntry(
    val level: Int,
    val title: String,
    val pageIndex: Int,
)

/**
 * Поставщик оглавления документа. Для конвертированных книг (EPUB/FB2) отдаёт
 * оглавление, собранное при верстке в PDF; для прочих источников — пустой список.
 */
interface DocumentOutlineProvider {
    /**
     * @param path путь/URI открытого документа (тот же, что при открытии)
     * @return записи оглавления в порядке чтения; пусто, если оглавления нет
     */
    suspend fun outlineFor(path: String): List<TocEntry>
}
