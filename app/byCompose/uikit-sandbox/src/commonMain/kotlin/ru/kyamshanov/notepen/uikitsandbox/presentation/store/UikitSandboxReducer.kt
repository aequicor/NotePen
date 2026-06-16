package ru.kyamshanov.notepen.uikitsandbox.presentation.store

import com.arkivanov.mvikotlin.core.store.Reducer

/**
 * Reducer Store: преобразует текущее состояние и message в новое состояние.
 *
 * Reducer — чистая часть Store. Он не запускает coroutine, не вызывает use case-ы,
 * не читает flow, не публикует labels и не делает маппинг доменных моделей в
 * component model. Вся внешняя работа должна завершиться в executor-е до отправки
 * [UikitSandboxMsg]; reducer только фиксирует результат этой работы в
 * [UikitSandboxStore.State].
 */
internal object UikitSandboxReducer : Reducer<UikitSandboxStore.State, UikitSandboxMsg> {
    /**
     * Применяет message к состоянию Store.
     *
     * Правила переходов:
     * [UikitSandboxMsg.Refreshing] включает loading и очищает прошлую ошибку;
     * [UikitSandboxMsg.FilterChanged] меняет фильтр и очищает ошибку;
     * [UikitSandboxMsg.WidgetsChanged] заменяет список виджетов;
     * [UikitSandboxMsg.Failed] выключает loading и сохраняет текст ошибки;
     * [UikitSandboxMsg.Completed] выключает loading и очищает ошибку.
     *
     * @param msg сообщение от executor-а.
     * @return новое состояние Store.
     */
    override fun UikitSandboxStore.State.reduce(msg: UikitSandboxMsg): UikitSandboxStore.State =
        when (msg) {
            UikitSandboxMsg.Refreshing -> copy(isLoading = true, errorMessage = null)
            is UikitSandboxMsg.FilterChanged -> copy(filter = msg.filter, errorMessage = null)
            is UikitSandboxMsg.WidgetsChanged -> copy(widgets = msg.widgets)
            is UikitSandboxMsg.Failed -> copy(isLoading = false, errorMessage = msg.message)
            UikitSandboxMsg.Completed -> copy(isLoading = false, errorMessage = null)
        }
}
