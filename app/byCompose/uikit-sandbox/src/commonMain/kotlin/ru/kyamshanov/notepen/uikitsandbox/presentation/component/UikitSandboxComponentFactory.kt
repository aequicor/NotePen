package ru.kyamshanov.notepen.uikitsandbox.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxComponent
import ru.kyamshanov.notepen.uikitsandbox.data.SandboxWidgetRepositoryImpl
import ru.kyamshanov.notepen.uikitsandbox.data.local.InMemorySandboxWidgetLocalDataSource
import ru.kyamshanov.notepen.uikitsandbox.data.remote.StaticSandboxWidgetRemoteDataSource
import ru.kyamshanov.notepen.uikitsandbox.domain.repository.SandboxWidgetRepository
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.ObserveSandboxWidgetsUseCase
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.RefreshSandboxWidgetsUseCase
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.ToggleSandboxWidgetPinnedUseCase
import ru.kyamshanov.notepen.uikitsandbox.utils.DefaultSandboxDispatchers

/**
 * Internal factory для создания Decompose-компонента sandbox.
 *
 * Factory отвечает только за сборку domain/data/usecase graph-а и component-а. Compose
 * rendering остаётся в `presentation.ui`, поэтому здесь нет UI-зависимостей.
 *
 * @property storeFactory фабрика MVIKotlin Store.
 * @property repositoryFactory фабрика repository для нового component instance.
 */
internal class UikitSandboxComponentFactory(
    private val storeFactory: StoreFactory = DefaultStoreFactory(),
    private val repositoryFactory: () -> SandboxWidgetRepository = ::createDefaultRepository,
) {
    /**
     * Создаёт component sandbox-фичи.
     *
     * @param componentContext Decompose-контекст жизненного цикла.
     * @param onWidgetSelected callback выбора виджета для родительского UI.
     * @return публичный для модуля component-контракт sandbox.
     */
    fun create(
        componentContext: ComponentContext,
        onWidgetSelected: (id: String) -> Unit,
    ): UikitSandboxComponent {
        val repository = repositoryFactory()
        return DefaultUikitSandboxComponent(
            componentContext = componentContext,
            storeFactory = storeFactory,
            observeWidgets = ObserveSandboxWidgetsUseCase(repository),
            refreshWidgets = RefreshSandboxWidgetsUseCase(repository),
            togglePinned = ToggleSandboxWidgetPinnedUseCase(repository),
            onWidgetSelected = onWidgetSelected,
        )
    }
}

private fun createDefaultRepository(): SandboxWidgetRepository =
    SandboxWidgetRepositoryImpl(
        localDataSource = InMemorySandboxWidgetLocalDataSource(),
        remoteDataSource = StaticSandboxWidgetRemoteDataSource(),
        dispatchers = DefaultSandboxDispatchers,
    )
