package ru.kyamshanov.notepen.uikitsandbox

import com.arkivanov.decompose.value.Value
import kotlinx.coroutines.flow.Flow

/**
 * Internal UI-контракт sandbox-фичи.
 *
 * Компонент скрывает Decompose/MVIKotlin-реализацию и отдаёт наружу только модель
 * экрана, одноразовые события и семантические методы пользовательских действий.
 * UI и app-модули должны зависеть от этого интерфейса, а не от Store, Intent,
 * Msg или Default-реализаций.
 *
 * Входные данные: пользовательские действия через методы интерфейса.
 * Выходные данные: [model] для состояния экрана и [events] для одноразовых эффектов.
 * Исключения: методы интерфейса штатно не выбрасывают исключения; ошибки фичи
 * транслируются в [Model.errorMessage] или [Event].
 */
internal interface UikitSandboxComponent {
    /**
     * Наблюдаемая модель экрана.
     *
     * Свойство read-only для потребителя. Реализация обновляет значение при изменении
     * состояния MVIKotlin Store и маппит внутренние domain-модели в UI-модель.
     */
    val model: Value<Model>

    /**
     * Поток одноразовых UI-событий.
     *
     * Используется для эффектов, которые нельзя хранить в состоянии: сообщения,
     * snackbar/toast, внешние сигналы UI. Повторная подписка не обязана воспроизводить
     * уже обработанные события.
     */
    val events: Flow<Event>

    /**
     * Запрашивает обновление списка sandbox-виджетов.
     *
     * Входные данные: нет.
     * Выходные данные: обновление [model] и, при ошибке, [Event.ShowMessage].
     * Исключения: штатно не выбрасывает исключения наружу.
     */
    fun onRefreshClicked()

    /**
     * Меняет активный фильтр списка.
     *
     * @param filter новый фильтр, выбранный пользователем.
     *
     * Входные данные: [filter].
     * Выходные данные: обновление [Model.selectedFilter] и [Model.widgets].
     * Исключения: штатно не выбрасывает исключения наружу.
     */
    fun onFilterSelected(filter: Filter)

    /**
     * Меняет признак закрепления виджета.
     *
     * @param id публичный идентификатор виджета из [Widget.id].
     * @param isPinned новое значение закрепления.
     *
     * Входные данные: [id] и [isPinned].
     * Выходные данные: обновление соответствующего [Widget.isPinned] или одноразовое
     * сообщение об ошибке через [events].
     * Исключения: штатно не выбрасывает исключения наружу.
     */
    fun onPinnedChanged(
        id: String,
        isPinned: Boolean,
    )

    /**
     * Сообщает компоненту, что пользователь выбрал виджет.
     *
     * @param id публичный идентификатор виджета из [Widget.id].
     *
     * Входные данные: [id].
     * Выходные данные: результат навигации через callback, переданный в
     * callback, переданный в factory компонента.
     * Исключения: штатно не выбрасывает исключения наружу.
     */
    fun onWidgetClicked(id: String)

    /**
     * Публичные режимы фильтрации списка виджетов.
     */
    enum class Filter {
        /** Показывать все виджеты. */
        All,

        /** Показывать только закреплённые виджеты. */
        Pinned,

        /** Показывать только экспериментальные виджеты. */
        Experimental,
    }

    /**
     * Полная UI-модель sandbox-экрана.
     *
     * @property isLoading true, пока выполняется обновление данных.
     * @property selectedFilter текущий фильтр, применённый к [widgets].
     * @property widgets список виджетов, уже подготовленный для отображения.
     * @property errorMessage текст последней recoverable-ошибки или null.
     */
    data class Model(
        val isLoading: Boolean,
        val selectedFilter: Filter,
        val widgets: List<Widget>,
        val errorMessage: String?,
    )

    /**
     * UI-модель одного виджета sandbox-экрана.
     *
     * @property id стабильный публичный идентификатор для callbacks и ключей списка.
     * @property title заголовок, отображаемый пользователю.
     * @property description краткое описание назначения виджета.
     * @property statusLabel локализованная строка статуса для UI.
     * @property isPinned true, если виджет закреплён пользователем.
     */
    data class Widget(
        val id: String,
        val title: String,
        val description: String,
        val statusLabel: String,
        val isPinned: Boolean,
    )

    /**
     * Одноразовые события, которые компонент отправляет UI поверх состояния.
     */
    sealed interface Event {
        /**
         * Событие показа короткого сообщения пользователю.
         *
         * @property message готовый для отображения текст сообщения.
         */
        data class ShowMessage(
            val message: String,
        ) : Event
    }
}
