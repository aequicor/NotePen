package ru.kyamshanov.notepen

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable
import ru.kyamshanov.notepen.RootComponent.Child.DetailsChild
import ru.kyamshanov.notepen.RootComponent.Child.FolderContentsChild
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
        onOpenFolder: (folderId: String, folderName: String) -> Unit,
    ) -> MainComponent,
    private val peerCatalogComponentFactory: (
        componentContext: ComponentContext,
        peerId: String,
        displayName: String,
        onBack: () -> Unit,
        onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
    ) -> PeerCatalogComponent,
    private val folderComponentFactory: (
        componentContext: ComponentContext,
        folderId: String,
        folderName: String,
        onBack: () -> Unit,
        onOpenEditor: (uri: String, lastPageIndex: Int) -> Unit,
        onOpenFolder: (folderId: String, folderName: String) -> Unit,
    ) -> FolderComponent,
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

    // Decompose требует уникальности конфигураций в пределах стека. Чтобы
    // разрешить дубликаты (один документ открыт дважды, библиотека открыта
    // поверх документа несколько раз), каждому пушу выдаём уникальный id.
    // Засеиваем счётчик от восстановленного стека, чтобы пережить смерть процесса.
    private var instanceCounter: Long =
        stack.value.items.maxOf { item ->
            when (val config = item.configuration) {
                is Config.Library -> config.instanceId
                is Config.Details -> config.instanceId
                is Config.PeerCatalog -> config.instanceId
                is Config.FolderContents -> config.instanceId
                else -> -1L
            }
        } + 1L

    private fun nextInstanceId(): Long = instanceCounter++

    /**
     * Opens [uri]. When the navigation was started by the "+" button — i.e. a
     * [Config.Library] sits over an open [Config.Details] — the file is added
     * as a new tab to that editor (set [pendingTabUri], pop back to it),
     * preserving its panel split. This holds even after drilling through
     * folders. Otherwise (opening from the root library) a fresh editor is
     * pushed as before.
     */
    @OptIn(DelicateDecomposeApi::class)
    private fun openEditorOrAddTab(
        uri: String,
        lastPageIndex: Int,
    ) {
        val items = stack.value.items
        val libraryIndex = items.indexOfLast { it.configuration is Config.Library }
        val editorIndex = libraryIndex - 1
        if (libraryIndex > 0 && items[editorIndex].configuration is Config.Details) {
            pendingTabUri.value = uri
            navigation.popTo(index = editorIndex)
        } else {
            navigation.push(Config.Details(uri, lastPageIndex, nextInstanceId()))
        }
    }

    /**
     * Файл, выбранный в библиотеке, открытой поверх редактора (кнопкой «+»).
     * Библиотека выставляет его и возвращается в редактор; активный
     * [DefaultDetailsComponent] забирает значение и открывает файл новой
     * вкладкой (в том же `tabSession` — так у каждой вкладки своя страница).
     */
    private val pendingTabUri = MutableValue("")

    private fun child(
        config: Config,
        ctx: ComponentContext,
    ): RootComponent.Child =
        when (config) {
            is Config.Main -> MainChild(mainComponent(ctx))
            is Config.Library -> MainChild(libraryComponent(ctx))
            is Config.Details -> DetailsChild(detailsComponent(ctx, config))
            is Config.PeerCatalog -> PeerCatalogChild(peerCatalogComponent(ctx, config))
            is Config.FolderContents -> FolderContentsChild(folderContentsComponent(ctx, config))
        }

    @OptIn(DelicateDecomposeApi::class)
    private fun mainComponent(ctx: ComponentContext): MainComponent =
        mainComponentFactory(
            ctx,
            { uri, lastPageIndex -> navigation.push(Config.Details(uri, lastPageIndex, nextInstanceId())) },
            { peerId, displayName -> navigation.push(Config.PeerCatalog(peerId, displayName, nextInstanceId())) },
            { folderId, folderName -> navigation.push(Config.FolderContents(folderId, folderName, nextInstanceId())) },
        )

    /**
     * Библиотека, открытая поверх редактора: выбор файла не пушит новый
     * экран, а возвращает в редактор и просит открыть файл новой вкладкой.
     */
    private fun libraryComponent(ctx: ComponentContext): MainComponent =
        mainComponentFactory(
            ctx,
            { uri, lastPageIndex -> openEditorOrAddTab(uri, lastPageIndex) },
            { peerId, displayName -> navigation.push(Config.PeerCatalog(peerId, displayName, nextInstanceId())) },
            { folderId, folderName -> navigation.push(Config.FolderContents(folderId, folderName, nextInstanceId())) },
        )

    @OptIn(DelicateDecomposeApi::class)
    private fun detailsComponent(
        ctx: ComponentContext,
        config: Config.Details,
    ): DetailsComponent =
        DefaultDetailsComponent(
            componentContext = ctx,
            title = config.uri,
            historyRepository = historyRepository,
            onBackListener = navigation::pop,
            onOpenLibraryListener = { navigation.push(Config.Library(nextInstanceId())) },
            pendingTabUri = pendingTabUri,
            onPendingTabHandledListener = { pendingTabUri.value = "" },
        )

    @OptIn(DelicateDecomposeApi::class)
    private fun peerCatalogComponent(
        ctx: ComponentContext,
        config: Config.PeerCatalog,
    ): PeerCatalogComponent =
        peerCatalogComponentFactory(
            ctx,
            config.peerId,
            config.displayName,
            navigation::pop,
        ) { uri, lastPageIndex ->
            navigation.push(Config.Details(uri, lastPageIndex, nextInstanceId()))
        }

    @OptIn(DelicateDecomposeApi::class)
    private fun folderContentsComponent(
        ctx: ComponentContext,
        config: Config.FolderContents,
    ): FolderComponent =
        folderComponentFactory(
            ctx,
            config.folderId,
            config.folderName,
            navigation::pop,
            { uri, lastPageIndex -> openEditorOrAddTab(uri, lastPageIndex) },
            { folderId, folderName -> navigation.push(Config.FolderContents(folderId, folderName, nextInstanceId())) },
        )

    override fun onBackClicked(toIndex: Int) {
        navigation.popTo(index = toIndex)
    }

    @OptIn(DelicateDecomposeApi::class)
    override fun openDetailsExternally(uri: String) {
        navigation.push(Config.Details(uri = uri, lastPageIndex = 0, instanceId = nextInstanceId()))
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Main : Config

        /**
         * Библиотека, открытая поверх документа (кнопкой «+»). Отдельная от
         * [Main] конфигурация с уникальным [instanceId] — чтобы не нарушать
         * требование уникальности конфигураций в стеке.
         */
        @Serializable
        data class Library(val instanceId: Long) : Config

        @Serializable
        data class Details(
            val uri: String,
            val lastPageIndex: Int = 0,
            val instanceId: Long = 0L,
        ) : Config

        @Serializable
        data class PeerCatalog(
            val peerId: String,
            val displayName: String,
            val instanceId: Long = 0L,
        ) : Config

        @Serializable
        data class FolderContents(
            val folderId: String,
            val folderName: String,
            val instanceId: Long = 0L,
        ) : Config
    }
}
