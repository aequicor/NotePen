package ru.kyamshanov.notepen.uikitsandbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import ru.kyamshanov.notepen.uikitsandbox.presentation.component.UikitSandboxComponentFactory
import ru.kyamshanov.notepen.uikitsandbox.presentation.ui.UikitSandboxAppContent

/**
 * Самостоятельное sandbox-приложение UIKit.
 *
 * Функция остаётся единственным публичным API модуля для Android/Desktop wrapper-ов.
 * Внутренняя сборка component graph-а скрыта в `presentation.component`.
 *
 * @param componentContext Decompose-контекст корневого sandbox-компонента.
 * @param modifier модификатор корневого Compose-узла.
 *
 * Входные данные: [componentContext] и [modifier].
 * Выходные данные: Compose-дерево standalone sandbox-приложения.
 * Исключения: штатно не выбрасывает исключения наружу.
 */
@Composable
public fun UikitSandboxApp(
    componentContext: ComponentContext,
    modifier: Modifier = Modifier,
) {
    val componentFactory = remember { UikitSandboxComponentFactory() }
    UikitSandboxAppContent(
        componentFactory = componentFactory,
        componentContext = componentContext,
        modifier = modifier,
    )
}
