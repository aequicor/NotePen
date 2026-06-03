package ru.kyamshanov.notepen.shortcuts.domain.model

import kotlinx.serialization.Serializable

/**
 * Пользовательские настройки шорткатов.
 *
 * v1: лупа + временное перемещение страницы пером. [loupeOpen] — биндинг
 * для входа в режим диагонального выделения области; [loupeClose] — биндинг
 * закрытия панели лупы; [penPan] — hold-модификатор: пока биндинг зажат,
 * перо перемещает PDF вместо рисования. Биндинги независимы; допустимо
 * использовать одну и ту же комбинацию для обоих действий (поведение тогда
 * toggle: открыто → закрытие, закрыто → выделение).
 *
 * По умолчанию открытие лупы — на кнопке пера; закрытие — на той же
 * кнопке (toggle).
 */
@Serializable
data class ShortcutsSettings(
    val loupeOpen: ShortcutBinding = ShortcutBinding(penButtons = setOf(1)),
    val loupeClose: ShortcutBinding = ShortcutBinding(penButtons = setOf(1)),
    val penPan: ShortcutBinding = ShortcutBinding.Disabled,
)
