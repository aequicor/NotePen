package ru.kyamshanov.notepen.shortcuts

import androidx.compose.ui.input.key.Key

/**
 * Возвращает `true`, если данный keyCode — это одна из клавиш-модификаторов
 * (Ctrl/Shift/Alt/Meta). Используется при записи биндинга: чистое нажатие
 * модификатора не должно становиться «основной» клавишей сочетания.
 */
internal fun isModifierKey(keyCode: Long): Boolean {
    val keys = listOf(
        Key.CtrlLeft, Key.CtrlRight,
        Key.ShiftLeft, Key.ShiftRight,
        Key.AltLeft, Key.AltRight,
        Key.MetaLeft, Key.MetaRight,
    )
    return keys.any { it.keyCode == keyCode }
}

/**
 * Короткое человекочитаемое имя клавиши для отображения в UI.
 *
 * Compose `Key.toString()` возвращает строку вида "Key: A", а константы
 * Key.* доступны и в `commonMain`, и в `jvmMain`. Строим маппинг через
 * `toString()` с подстрочной правкой: убираем префикс "Key: ".
 */
internal fun keyDisplayName(keyCode: Long): String {
    val raw = Key(keyCode).toString()
    val prefix = "Key: "
    return if (raw.startsWith(prefix)) raw.substring(prefix.length) else raw
}
