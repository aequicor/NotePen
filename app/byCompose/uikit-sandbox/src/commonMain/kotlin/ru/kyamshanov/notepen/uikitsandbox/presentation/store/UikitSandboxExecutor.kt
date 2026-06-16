package ru.kyamshanov.notepen.uikitsandbox.presentation.store

import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxComponent
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxOutcome
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetId
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.ObserveSandboxWidgetsUseCase
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.RefreshSandboxWidgetsUseCase
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.ToggleSandboxWidgetPinnedUseCase
import ru.kyamshanov.notepen.uikitsandbox.presentation.model.toDomainFilter
import ru.kyamshanov.notepen.uikitsandbox.presentation.model.toUserMessage

/**
 * Executor Store: выполняет side effects, вызывает use case-ы и испускает messages/labels.
 *
 * Executor — единственное место Store, где допустимы suspend-вызовы, подписки на
 * flow, запуск coroutine и принятие решений о labels. Он получает bootstrap actions
 * и пользовательские intents, вызывает domain use case-ы, затем сообщает reducer-у
 * о фактах через [UikitSandboxMsg]. Одноразовые эффекты наружу отправляются через
 * [UikitSandboxStore.Label].
 *
 * @property observeWidgets use case наблюдения списка.
 * @property refreshWidgets use case обновления списка.
 * @property togglePinned use case изменения закрепления.
 */
internal class UikitSandboxExecutor(
    private val observeWidgets: ObserveSandboxWidgetsUseCase,
    private val refreshWidgets: RefreshSandboxWidgetsUseCase,
    private val togglePinned: ToggleSandboxWidgetPinnedUseCase,
) : CoroutineExecutor<
        UikitSandboxStore.Intent,
        UikitSandboxAction,
        UikitSandboxStore.State,
        UikitSandboxMsg,
        UikitSandboxStore.Label,
    >() {
    private var observeJob: Job? = null

    /**
     * Обрабатывает bootstrap-действия Store.
     *
     * Сейчас bootstrap состоит из одного действия: начать наблюдение за списком
     * с текущим фильтром и сразу запустить refresh. Наблюдение отдаёт reducer-у
     * [UikitSandboxMsg.WidgetsChanged], refresh отдаёт [UikitSandboxMsg.Refreshing],
     * [UikitSandboxMsg.Completed] или [UikitSandboxMsg.Failed].
     *
     * @param action действие bootstrapper-а.
     */
    override fun executeAction(action: UikitSandboxAction) {
        when (action) {
            UikitSandboxAction.Start -> {
                observe(filter = state().filter)
                refresh()
            }
        }
    }

    /**
     * Обрабатывает пользовательские intents Store.
     *
     * Intent здесь превращается либо в side effect, либо в message/label:
     * refresh запускает use case обновления, смена фильтра dispatch-ит
     * [UikitSandboxMsg.FilterChanged] и перезапускает observe, изменение pinned
     * вызывает запись через use case, выбор виджета публикует
     * [UikitSandboxStore.Label.OpenWidget].
     *
     * @param intent пользовательское намерение из component-адаптера.
     */
    override fun executeIntent(intent: UikitSandboxStore.Intent) {
        when (intent) {
            UikitSandboxStore.Intent.RefreshClicked -> refresh()
            is UikitSandboxStore.Intent.FilterSelected -> {
                dispatch(UikitSandboxMsg.FilterChanged(intent.filter))
                observe(filter = intent.filter)
            }
            is UikitSandboxStore.Intent.PinnedChanged -> updatePinned(intent.id, intent.isPinned)
            is UikitSandboxStore.Intent.WidgetClicked -> publish(UikitSandboxStore.Label.OpenWidget(intent.id))
        }
    }

    /**
     * Перезапускает наблюдение за списком виджетов для выбранного фильтра.
     *
     * Важно передавать [filter] явно из intent/bootstrap, а не читать его только
     * из [state], потому что `dispatch(FilterChanged)` не обязан синхронно обновить
     * state до следующей строки executor-а. Предыдущая подписка отменяется, чтобы
     * в Store не приходили messages от устаревшего фильтра.
     *
     * @param filter фильтр, который должен применить domain use case.
     */
    private fun observe(filter: UikitSandboxComponent.Filter) {
        observeJob?.cancel()
        observeJob =
            scope.launch {
                observeWidgets(filter.toDomainFilter()).collectLatest { widgets ->
                    dispatch(UikitSandboxMsg.WidgetsChanged(widgets))
                }
            }
    }

    /**
     * Запускает ручное обновление списка виджетов.
     *
     * Последовательность messages фиксирована: сначала reducer получает
     * [UikitSandboxMsg.Refreshing], затем при успехе [UikitSandboxMsg.Completed],
     * а при ошибке [UikitSandboxMsg.Failed]. Ошибка дополнительно публикуется как
     * [UikitSandboxStore.Label.ShowMessage], потому что snackbar/toast является
     * одноразовым эффектом и не должен жить только в state.
     */
    private fun refresh() {
        scope.launch {
            dispatch(UikitSandboxMsg.Refreshing)
            when (val outcome = refreshWidgets()) {
                is SandboxOutcome.Success -> dispatch(UikitSandboxMsg.Completed)
                is SandboxOutcome.Failure -> {
                    val message = outcome.reason.toUserMessage()
                    dispatch(UikitSandboxMsg.Failed(message))
                    publish(UikitSandboxStore.Label.ShowMessage(message))
                }
            }
        }
    }

    /**
     * Сохраняет состояние закрепления виджета.
     *
     * Успешная запись не dispatch-ит отдельный message: обновлённый список придёт
     * через активный observe-flow как [UikitSandboxMsg.WidgetsChanged]. Ошибка
     * публикуется label-ом, потому что состояние списка при этом не изменяется.
     *
     * @param id доменный идентификатор виджета.
     * @param isPinned новое значение закрепления.
     */
    private fun updatePinned(
        id: SandboxWidgetId,
        isPinned: Boolean,
    ) {
        scope.launch {
            when (val outcome = togglePinned(id, isPinned)) {
                is SandboxOutcome.Success -> Unit
                is SandboxOutcome.Failure -> publish(UikitSandboxStore.Label.ShowMessage(outcome.reason.toUserMessage()))
            }
        }
    }
}
