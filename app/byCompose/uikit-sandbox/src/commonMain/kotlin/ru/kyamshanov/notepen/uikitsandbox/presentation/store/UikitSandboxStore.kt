package ru.kyamshanov.notepen.uikitsandbox.presentation.store

import com.arkivanov.mvikotlin.core.store.Store
import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxComponent
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidget
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetId

/**
 * Internal MVIKotlin Store sandbox-фичи.
 *
 * Store остаётся внутри presentation-слоя; внешние потребители работают только через
 * [UikitSandboxComponent].
 */
internal interface UikitSandboxStore : Store<UikitSandboxStore.Intent, UikitSandboxStore.State, UikitSandboxStore.Label> {
    /**
     * Пользовательские намерения, приходящие из component-адаптера.
     */
    sealed interface Intent {
        /** Пользователь запросил обновление списка. */
        data object RefreshClicked : Intent

        /**
         * Пользователь выбрал новый фильтр.
         *
         * @property filter выбранный публичный фильтр component API.
         */
        data class FilterSelected(
            val filter: UikitSandboxComponent.Filter,
        ) : Intent

        /**
         * Пользователь изменил закрепление виджета.
         *
         * @property id доменный идентификатор виджета.
         * @property isPinned новое состояние закрепления.
         */
        data class PinnedChanged(
            val id: SandboxWidgetId,
            val isPinned: Boolean,
        ) : Intent

        /**
         * Пользователь выбрал виджет.
         *
         * @property id доменный идентификатор виджета.
         */
        data class WidgetClicked(
            val id: SandboxWidgetId,
        ) : Intent
    }

    /**
     * Состояние Store до маппинга в публичную UI-модель.
     *
     * @property isLoading признак текущего refresh.
     * @property filter активный публичный фильтр.
     * @property widgets доменные виджеты, подготовленные use case-ом.
     * @property errorMessage recoverable-ошибка, которую можно показать в UI.
     */
    data class State(
        val isLoading: Boolean = false,
        val filter: UikitSandboxComponent.Filter = UikitSandboxComponent.Filter.All,
        val widgets: List<SandboxWidget> = emptyList(),
        val errorMessage: String? = null,
    )

    /**
     * Одноразовые эффекты Store.
     */
    sealed interface Label {
        /**
         * Запросить у родителя открытие выбранного виджета.
         *
         * @property id доменный идентификатор выбранного виджета.
         */
        data class OpenWidget(
            val id: SandboxWidgetId,
        ) : Label

        /**
         * Показать пользователю короткое сообщение.
         *
         * @property message готовый текст сообщения.
         */
        data class ShowMessage(
            val message: String,
        ) : Label
    }
}
