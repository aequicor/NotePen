package ru.kyamshanov.notepen.uikitsandbox.data.remote

import kotlinx.serialization.Serializable

/**
 * DTO sandbox-виджета, приходящий из remote data source.
 *
 * @property id строковый идентификатор удалённой записи.
 * @property title заголовок виджета.
 * @property description описание виджета.
 * @property status строковый статус зрелости.
 * @property isPinned признак закрепления из удалённого источника.
 */
@Serializable
internal data class SandboxWidgetDto(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val isPinned: Boolean = false,
)
