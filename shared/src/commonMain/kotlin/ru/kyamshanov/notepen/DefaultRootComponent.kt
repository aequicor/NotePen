package ru.kyamshanov.notepen

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import ru.kyamshanov.notepen.RootComponent.Child.DetailsChild
import ru.kyamshanov.notepen.RootComponent.Child.MainChild
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository

/**
 * Стандартная реализация [RootComponent].
 *
 * Принимает фабрику [mainComponentFactory], которая создаёт [MainComponent] из `:common`.
 * Это позволяет `:shared` оставаться независимым от `:common` (нет циклической зависимости).
 *
 * @param componentContext Контекст компонента Decompose.
 * @param mainComponentFactory Фабрика для создания компонента главного экрана.
 *        Вызывается с [ComponentContext] и колбэком навигации в редактор.
 */
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val historyRepository: FileHistoryRepository,
    private val mainComponentFactory: (
        componentContext: ComponentContext,
        onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
    ) -> MainComponent,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    override val stack: Value<ChildStack<*, RootComponent.Child>> =
        childStack(
            source = navigation,
            serializer = Config.serializer(),
            initialConfiguration = Config.Main,
            handleBackButton = true,
            childFactory = ::child,
        )

    private fun child(config: Config, ctx: ComponentContext): RootComponent.Child = when (config) {
        is Config.Main -> MainChild(mainComponent(ctx))
        is Config.Details -> DetailsChild(detailsComponent(ctx, config))
    }

    private fun mainComponent(ctx: ComponentContext): MainComponent =
        mainComponentFactory(ctx) { uri, lastPageIndex ->
            navigation.push(Config.Details(uri, lastPageIndex))
        }

    private fun detailsComponent(ctx: ComponentContext, config: Config.Details): DetailsComponent =
        DefaultDetailsComponent(
            componentContext = ctx,
            title = config.uri,
            historyRepository = historyRepository,
            onBackListener = navigation::pop,
        )

    override fun onBackClicked(toIndex: Int) {
        navigation.popTo(index = toIndex)
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Main : Config

        @Serializable
        data class Details(val uri: String, val lastPageIndex: Int = 0) : Config
    }
}
