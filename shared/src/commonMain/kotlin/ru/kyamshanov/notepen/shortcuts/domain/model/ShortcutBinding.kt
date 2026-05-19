package ru.kyamshanov.notepen.shortcuts.domain.model

import kotlinx.serialization.Serializable

/**
 * Пользовательский биндинг для одного действия.
 *
 * Содержит произвольное сочетание из:
 * - модификаторов клавиатуры ([ctrl], [shift], [alt], [meta]);
 * - кнопок стилуса ([penButtons]) — набор битовых индексов; у обычного
 *   пера одна-две кнопки, индексы зависят от драйвера (на Huion / Wacom
 *   барэл-кнопка обычно сидит на бите 1);
 * - произвольной клавиши клавиатуры ([keyCode] + читаемое [keyName]).
 *
 * Биндинг считается активным, если **все** перечисленные в нём элементы
 * сейчас зажаты одновременно. Пустой биндинг ([isEmpty]) — отключён.
 *
 * Сериализуется как обычный JSON; неизвестные поля игнорируются
 * репозиторием для forward-compat.
 */
@Serializable
data class ShortcutBinding(
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
    /** Зажатые кнопки пера: набор битовых индексов из `TabletInputController.penButtons`. */
    val penButtons: Set<Int> = emptySet(),
    /** Compose key code (`Key.keyCode`). `0` — клавиша не задана. */
    val keyCode: Long = 0L,
    /** Человекочитаемое имя клавиши для отображения; пусто — клавиша не задана. */
    val keyName: String = "",
) {
    /** Биндинг пуст — не содержит ни одного активного триггера. */
    val isEmpty: Boolean
        get() = !ctrl && !shift && !alt && !meta && penButtons.isEmpty() && keyCode == 0L

    companion object {
        /** Пустой биндинг = «отключено». */
        val Disabled: ShortcutBinding = ShortcutBinding()
    }
}
