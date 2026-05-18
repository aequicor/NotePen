package ru.kyamshanov.notepen

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
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
import ru.kyamshanov.notepen.RootComponent.Child.PeerCatalogChild
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository

/**
 * Стандартная реализация [RootComponent].
 *
 * Принимает фабрики [mainComponentFactory] и [peerCatalogComponentFactory], которые
 * создают компоненты из `:common`. Это позволяет `:shared` оставаться независимым от
 * `:common` (нет циклической зависимости).
 *
 * @param componentContext Контекст компонента Decompose.
 * @param mainComponentFactory Фабрика главного экрана. Принимает [ComponentContext],
 *        колбэк перехода в редактор и колбэк перехода на sub-экран каталога пира.
 * @param peerCatalogComponentFactory Фабрика sub-экрана каталога пира.
 *        Принимает [ComponentContext], peerId, displayName и колбэк back.
 */
class DefaultRootComponent(
    componentContext: ComponentContext,
    private val historyRepository: FileHistoryRepository,
    private val mainComponentFactory: (
        componentContext: ComponentContext,
        onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
        onOpenPeerCatalog: (peerId: String, displayName: String) -> Unit,
    ) -> MainComponent,
    private val peerCatalogComponentFactory: (
        componentContext: ComponentContext,
        peerId: String,
        displayName: String,
        onBack: () -> Unit,
        onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
    ) -> PeerCatalogComponent,
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
        is Config.PeerCatalog -> PeerCatalogChild(peerCatalogComponent(ctx, config))
    }

    @OptIn(DelicateDecomposeApi::class)
    private fun mainComponent(ctx: ComponentContext): MainComponent =
        mainComponentFactory(
            ctx,
            { uri, lastPageIndex -> navigation.push(Config.Details(uri, lastPageIndex)) },
            { peerId, displayName -> navigation.push(Config.PeerCatalog(peerId, displayName)) },
        )

    private fun detailsComponent(ctx: ComponentContext, config: Config.Details): DetailsComponent =
        DefaultDetailsComponent(
            componentContext = ctx,
            title = config.uri,
            historyRepository = historyRepository,
            onBackListener = navigation::pop,
        )

    @OptIn(DelicateDecomposeApi::class)
    private fun peerCatalogComponent(ctx: ComponentContext, config: Config.PeerCatalog): PeerCatalogComponent =
        peerCatalogComponentFactory(
            ctx,
            config.peerId,
            config.displayName,
            navigation::pop,
        ) { uri, lastPageIndex ->
            navigation.push(Config.Details(uri, lastPageIndex))
        }

    override fun onBackClicked(toIndex: Int) {
        navigation.popTo(index = toIndex)
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun openDetailsExternally(uri: String) {
        navigation.push(Config.Details(uri = uri, lastPageIndex = 0))
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Main : Config

        @Serializable
        data class Details(val uri: String, val lastPageIndex: Int = 0) : Config

        @Serializable
        data class PeerCatalog(val peerId: String, val displayName: String) : Config
    }
}
