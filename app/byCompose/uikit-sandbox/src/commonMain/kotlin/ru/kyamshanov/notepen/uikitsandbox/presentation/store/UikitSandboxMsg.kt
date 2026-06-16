package ru.kyamshanov.notepen.uikitsandbox.presentation.store

import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxComponent
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidget

/**
 * Сообщения, которые reducer переводит в новое состояние Store.
 *
 * Message описывает уже произошедший факт, а не пользовательское действие.
 * Пользовательские действия представлены [UikitSandboxStore.Intent], side effects
 * выполняет [UikitSandboxExecutor], а reducer принимает только эти facts и строит
 * новый [UikitSandboxStore.State].
 */
internal sealed interface UikitSandboxMsg {
    /**
     * Началось обновление remote-данных.
     *
     * Reducer включает loading и сбрасывает предыдущую recoverable-ошибку.
     */
    data object Refreshing : UikitSandboxMsg

    /**
     * Изменился активный фильтр списка.
     *
     * Executor отправляет это сообщение до перезапуска observe-flow для нового фильтра.
     * Reducer хранит выбранный фильтр в state, чтобы UI мог подсветить chip.
     *
     * @property filter новый фильтр component API.
     */
    data class FilterChanged(
        val filter: UikitSandboxComponent.Filter,
    ) : UikitSandboxMsg

    /**
     * Use case наблюдения вернул новый список виджетов.
     *
     * Это единственный путь обновления списка в state: refresh и pinned-запись
     * меняют data source, а актуальные данные приходят через observe-flow.
     *
     * @property widgets актуальные доменные виджеты.
     */
    data class WidgetsChanged(
        val widgets: List<SandboxWidget>,
    ) : UikitSandboxMsg

    /**
     * Операция завершилась recoverable-ошибкой.
     *
     * Reducer сохраняет текст в state для inline-отображения, а executor отдельно
     * публикует [UikitSandboxStore.Label.ShowMessage] для одноразового snackbar.
     *
     * @property message текст ошибки для состояния и label.
     */
    data class Failed(
        val message: String,
    ) : UikitSandboxMsg

    /**
     * Обновление завершилось успешно.
     *
     * Reducer выключает loading и очищает ошибку; список не меняется этим message,
     * потому что данные должны прийти отдельным [WidgetsChanged].
     */
    data object Completed : UikitSandboxMsg
}
