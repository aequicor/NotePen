package ru.kyamshanov.notepen.mainscreen.ui.model

import ru.kyamshanov.notepen.library.api.LibraryBackendKind

/**
 * Карточка одной подключённой библиотеки в секции «Папки» на главном экране — по карточке
 * на каждую библиотеку из [ru.kyamshanov.notepen.library.api.LibraryRegistry].
 *
 * @property id Идентификатор библиотеки ([ru.kyamshanov.notepen.library.api.LibraryId] value) —
 *   стабильный ключ карточки и аргумент интентов открытия/добавления.
 * @property displayName Имя библиотеки (из дескриптора).
 * @property kind Тип бэкенда (локальная папка / LAN / GitHub / облако).
 * @property bookCount Количество книг в библиотеке.
 * @property canAdd Можно ли добавлять книги (роль Библиотекарь). UI гейтит drop/добавление
 *   именно по этому флагу, никогда не выводя его из роли напрямую.
 */
data class LibraryCardUiModel(
    val id: String,
    val displayName: String,
    val kind: LibraryBackendKind,
    val bookCount: Int,
    val canAdd: Boolean,
)
