package ru.kyamshanov.notepen.uikitsandbox.presentation.store

import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.ObserveSandboxWidgetsUseCase
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.RefreshSandboxWidgetsUseCase
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.ToggleSandboxWidgetPinnedUseCase

/**
 * Фабрика MVIKotlin Store для sandbox-фичи.
 *
 * Store создаётся компонентом через instanceKeeper, поэтому фабрика не хранит состояние
 * экрана и может безопасно передаваться как зависимость.
 * Файл должен оставаться wiring-слоем: вся side-effect логика находится в
 * [UikitSandboxExecutor], а все state transitions — в [UikitSandboxReducer].
 *
 * @property storeFactory фабрика MVIKotlin.
 * @property observeWidgets use case наблюдения за списком.
 * @property refreshWidgets use case обновления списка.
 * @property togglePinned use case изменения закрепления.
 */
internal class UikitSandboxStoreFactory(
    private val storeFactory: StoreFactory,
    private val observeWidgets: ObserveSandboxWidgetsUseCase,
    private val refreshWidgets: RefreshSandboxWidgetsUseCase,
    private val togglePinned: ToggleSandboxWidgetPinnedUseCase,
) {
    /**
     * Создаёт новый Store с bootstrap-действием загрузки.
     *
     * Здесь только связываются MVIKotlin primitives: initial state, bootstrap action,
     * executor и reducer. Бизнес-ветвления не добавляются в factory, чтобы чтение
     * Store оставалось разделённым по ролям.
     *
     * @return internal Store sandbox-фичи.
     */
    fun create(): UikitSandboxStore =
        object :
            UikitSandboxStore,
            Store<UikitSandboxStore.Intent, UikitSandboxStore.State, UikitSandboxStore.Label> by storeFactory.create(
                name = "UikitSandboxStore",
                initialState = UikitSandboxStore.State(),
                bootstrapper = SimpleBootstrapper(UikitSandboxAction.Start),
                executorFactory = {
                    UikitSandboxExecutor(
                        observeWidgets = observeWidgets,
                        refreshWidgets = refreshWidgets,
                        togglePinned = togglePinned,
                    )
                },
                reducer = UikitSandboxReducer,
            ) {}
}
