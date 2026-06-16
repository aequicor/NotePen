package ru.kyamshanov.notepen.uikitsandbox.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.decompose.extensions.compose.lifecycle.LifecycleController
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxApp

/**
 * Запускает standalone UIKit sandbox на Compose Desktop.
 *
 * Входные данные: аргументы командной строки не используются.
 * Выходные данные: desktop-окно с [UikitSandboxApp].
 * Исключения: штатно не выбрасывает исключения наружу.
 */
fun main() {
    val lifecycle = LifecycleRegistry()
    val componentContext = DefaultComponentContext(lifecycle = lifecycle)
    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 860.dp)
        LifecycleController(lifecycle, windowState)
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "NotePen UIKit Sandbox",
        ) {
            UikitSandboxApp(componentContext = componentContext)
        }
    }
}
