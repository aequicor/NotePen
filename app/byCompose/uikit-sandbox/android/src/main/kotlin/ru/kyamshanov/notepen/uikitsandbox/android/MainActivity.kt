package ru.kyamshanov.notepen.uikitsandbox.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxApp

/**
 * Android-точка входа standalone UIKit sandbox.
 *
 * Входные данные: стандартный lifecycle Activity и сохранённое состояние [savedInstanceState].
 * Выходные данные: Compose-контент [UikitSandboxApp].
 * Исключения: штатно не выбрасывает исключения наружу.
 */
class MainActivity : ComponentActivity() {
    /**
     * Инициализирует edge-to-edge режим и устанавливает Compose-контент sandbox-приложения.
     *
     * @param savedInstanceState сохранённое состояние Activity или null при холодном старте.
     *
     * Входные данные: [savedInstanceState].
     * Выходные данные: Activity с установленным Compose UI.
     * Исключения: штатно не выбрасывает исключения наружу.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            UikitSandboxApp(componentContext = defaultComponentContext())
        }
    }
}
