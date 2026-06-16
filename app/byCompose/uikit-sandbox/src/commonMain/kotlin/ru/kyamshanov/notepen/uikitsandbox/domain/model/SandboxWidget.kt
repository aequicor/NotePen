package ru.kyamshanov.notepen.uikitsandbox.domain.model

/**
 * Доменная модель одного sandbox-виджета.
 *
 * @property id стабильный идентификатор виджета внутри feature.
 * @property title заголовок для отображения в showcase-списке.
 * @property description короткое описание назначения виджета.
 * @property status статус зрелости демонстрируемого элемента.
 * @property isPinned признак закрепления виджета пользователем.
 */
internal data class SandboxWidget(
    val id: SandboxWidgetId,
    val title: String,
    val description: String,
    val status: SandboxWidgetStatus,
    val isPinned: Boolean,
)

/**
 * Типизированный идентификатор sandbox-виджета.
 *
 * @property value строковое значение, пригодное для хранения и callbacks.
 */
@JvmInline
internal value class SandboxWidgetId(
    val value: String,
)

/**
 * Статусы зрелости виджетов в sandbox-домене.
 */
internal enum class SandboxWidgetStatus {
    /** Виджет считается стабильным примером. */
    Stable,

    /** Виджет демонстрирует экспериментальную поверхность или поведение. */
    Experimental,

    /** Виджет оставлен для совместимости или сравнения со старым UI. */
    Deprecated,
}

/**
 * Доступные фильтры списка sandbox-виджетов.
 */
internal enum class SandboxWidgetFilter {
    /** Показывать все виджеты. */
    All,

    /** Показывать только закреплённые виджеты. */
    Pinned,

    /** Показывать только экспериментальные виджеты. */
    Experimental,
}
