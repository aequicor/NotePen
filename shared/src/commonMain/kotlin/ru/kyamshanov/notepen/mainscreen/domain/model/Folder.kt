package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * Доменная сущность, представляющая пользовательскую папку для группировки файлов.
 *
 * @property id Уникальный идентификатор папки (UUID v4).
 * @property name Отображаемое имя папки. Максимум 255 символов, не пустое.
 * @property createdAt Момент создания папки (epochMillis).
 */
data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,
)
