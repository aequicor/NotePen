package ru.kyamshanov.notepen.reflow.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Desktop/JVM: пишем в системный буфер обмена AWT
 * (`Toolkit.getDefaultToolkit().systemClipboard`). Без headless-окружения буфер
 * недоступен — ловим и проглатываем ошибку, чтобы копирование никогда не валило ридер.
 */
@Composable
internal actual fun rememberClipboardWriter(): (String) -> Unit =
    remember {
        { text ->
            if (text.isNotEmpty()) {
                runCatching {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                }
            }
        }
    }
