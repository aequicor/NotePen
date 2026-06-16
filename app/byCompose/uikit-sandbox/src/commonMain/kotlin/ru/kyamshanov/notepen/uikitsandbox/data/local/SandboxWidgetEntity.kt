package ru.kyamshanov.notepen.uikitsandbox.data.local

/**
 * Локальная entity sandbox-виджета.
 *
 * @property id строковый ключ записи.
 * @property title заголовок, сохранённый в локальном источнике.
 * @property description описание, сохранённое в локальном источнике.
 * @property status строковое значение статуса для имитации хранения.
 * @property isPinned сохранённый признак закрепления.
 */
internal data class SandboxWidgetEntity(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val isPinned: Boolean,
)
