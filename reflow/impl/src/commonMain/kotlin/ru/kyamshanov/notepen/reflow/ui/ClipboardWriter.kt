package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.runtime.Composable

/**
 * Платформенный писатель в системный буфер обмена для действия «Копировать» панели
 * выделения ([ReflowReader]). Возвращает функцию `(String) -> Unit`, копирующую
 * переданный текст в системный clipboard.
 *
 * Реализован через `expect/actual` (без `LocalClipboardManager`), потому что вызов
 * происходит из коллбэка панели действий, а не во время композиции, и платформам
 * нужен прямой доступ к буферу: JVM — AWT `Toolkit.systemClipboard`, Android —
 * `ClipboardManager` через `LocalContext`. Пустой текст не копируется.
 *
 * @return функция копирования; её захватывают в `remember`/коллбэк панели выделения
 */
@Composable
internal expect fun rememberClipboardWriter(): (String) -> Unit
