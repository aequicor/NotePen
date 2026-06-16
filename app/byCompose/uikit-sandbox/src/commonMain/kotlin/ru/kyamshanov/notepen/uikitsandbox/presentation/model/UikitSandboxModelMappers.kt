package ru.kyamshanov.notepen.uikitsandbox.presentation.model

import ru.kyamshanov.notepen.uikitsandbox.UikitSandboxComponent
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidget
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetFailure
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetFilter
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetStatus
import ru.kyamshanov.notepen.uikitsandbox.presentation.store.UikitSandboxStore

/**
 * Преобразует состояние Store в модель component API.
 *
 * @return UI-модель, с которой работает Compose-слой.
 */
internal fun UikitSandboxStore.State.toComponentModel(): UikitSandboxComponent.Model =
    UikitSandboxComponent.Model(
        isLoading = isLoading,
        selectedFilter = filter,
        widgets = widgets.map(SandboxWidget::toComponentWidget),
        errorMessage = errorMessage,
    )

/**
 * Преобразует публичный для component фильтр в доменный фильтр use case-а.
 *
 * @return доменный фильтр списка виджетов.
 */
internal fun UikitSandboxComponent.Filter.toDomainFilter(): SandboxWidgetFilter =
    when (this) {
        UikitSandboxComponent.Filter.All -> SandboxWidgetFilter.All
        UikitSandboxComponent.Filter.Pinned -> SandboxWidgetFilter.Pinned
        UikitSandboxComponent.Filter.Experimental -> SandboxWidgetFilter.Experimental
    }

/**
 * Преобразует recoverable-ошибку домена в текст для component event.
 *
 * @return готовая строка сообщения пользователю.
 */
internal fun SandboxWidgetFailure.toUserMessage(): String =
    when (this) {
        SandboxWidgetFailure.Network -> "Could not refresh sandbox widgets."
        SandboxWidgetFailure.Storage -> "Could not save sandbox widget state."
        is SandboxWidgetFailure.Unknown -> message
    }

private fun SandboxWidget.toComponentWidget(): UikitSandboxComponent.Widget =
    UikitSandboxComponent.Widget(
        id = id.value,
        title = title,
        description = description,
        statusLabel = status.toLabel(),
        isPinned = isPinned,
    )

private fun SandboxWidgetStatus.toLabel(): String =
    when (this) {
        SandboxWidgetStatus.Stable -> "Stable"
        SandboxWidgetStatus.Experimental -> "Experimental"
        SandboxWidgetStatus.Deprecated -> "Deprecated"
    }
