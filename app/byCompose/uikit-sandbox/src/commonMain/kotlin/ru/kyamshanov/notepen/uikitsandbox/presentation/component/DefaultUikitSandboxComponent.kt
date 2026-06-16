@file:OptIn(ExperimentalCoroutinesApi::class)

package ru.kyamshanov.notepen.uikitsandbox.presentation.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxComponent
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetId
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.ObserveSandboxWidgetsUseCase
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.RefreshSandboxWidgetsUseCase
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.ToggleSandboxWidgetPinnedUseCase
import ru.kyamshanov.notepen.uikitsandbox.presentation.model.toComponentModel
import ru.kyamshanov.notepen.uikitsandbox.presentation.store.UikitSandboxStore
import ru.kyamshanov.notepen.uikitsandbox.presentation.store.UikitSandboxStoreFactory
import ru.kyamshanov.notepen.uikitsandbox.presentation.utils.asValue

/**
 * Decompose-реализация публичного [UikitSandboxComponent].
 *
 * Компонент владеет Store через instanceKeeper, маппит State в публичную модель и
 * переводит labels в callbacks/events, не раскрывая MVIKotlin наружу.
 *
 * @property onWidgetSelected callback родителя для результата выбора виджета.
 */
internal class DefaultUikitSandboxComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    observeWidgets: ObserveSandboxWidgetsUseCase,
    refreshWidgets: RefreshSandboxWidgetsUseCase,
    togglePinned: ToggleSandboxWidgetPinnedUseCase,
    private val onWidgetSelected: (id: String) -> Unit,
) : UikitSandboxComponent,
    ComponentContext by componentContext {
    private val scope = coroutineScope()
    private val store: UikitSandboxStore =
        instanceKeeper.getStore {
            UikitSandboxStoreFactory(
                storeFactory = storeFactory,
                observeWidgets = observeWidgets,
                refreshWidgets = refreshWidgets,
                togglePinned = togglePinned,
            ).create()
        }
    private val eventStream = MutableSharedFlow<UikitSandboxComponent.Event>(extraBufferCapacity = 1)

    /**
     * Публичная Decompose-модель компонента.
     */
    override val model: Value<UikitSandboxComponent.Model> =
        store.stateFlow.asValue(scope = scope) { state -> state.toComponentModel() }

    /**
     * Поток одноразовых событий компонента.
     */
    override val events: Flow<UikitSandboxComponent.Event> = eventStream

    init {
        scope.launch {
            store.labels.collect { label ->
                when (label) {
                    is UikitSandboxStore.Label.OpenWidget -> onWidgetSelected(label.id.value)
                    is UikitSandboxStore.Label.ShowMessage ->
                        eventStream.emit(UikitSandboxComponent.Event.ShowMessage(label.message))
                }
            }
        }
    }

    /**
     * Передаёт refresh-intent в Store.
     */
    override fun onRefreshClicked() {
        store.accept(UikitSandboxStore.Intent.RefreshClicked)
    }

    /**
     * Передаёт выбранный фильтр в Store.
     *
     * @param filter новый фильтр списка.
     */
    override fun onFilterSelected(filter: UikitSandboxComponent.Filter) {
        store.accept(UikitSandboxStore.Intent.FilterSelected(filter))
    }

    /**
     * Передаёт изменение закрепления в Store.
     *
     * @param id публичный идентификатор виджета.
     * @param isPinned новое значение закрепления.
     */
    override fun onPinnedChanged(
        id: String,
        isPinned: Boolean,
    ) {
        store.accept(UikitSandboxStore.Intent.PinnedChanged(SandboxWidgetId(id), isPinned))
    }

    /**
     * Передаёт выбор виджета в Store.
     *
     * @param id публичный идентификатор виджета.
     */
    override fun onWidgetClicked(id: String) {
        store.accept(UikitSandboxStore.Intent.WidgetClicked(SandboxWidgetId(id)))
    }
}
