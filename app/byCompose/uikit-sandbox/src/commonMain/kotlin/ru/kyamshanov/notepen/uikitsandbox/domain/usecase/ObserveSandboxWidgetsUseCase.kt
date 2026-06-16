package ru.kyamshanov.notepen.uikitsandbox.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidget
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetFilter
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetStatus
import ru.kyamshanov.notepen.uikitsandbox.domain.repository.SandboxWidgetRepository

/**
 * Use case наблюдения за отфильтрованным списком sandbox-виджетов.
 *
 * Закреплённые элементы всегда поднимаются вверх, остальные сортируются по заголовку.
 *
 * @property repository доменный порт чтения виджетов.
 */
internal class ObserveSandboxWidgetsUseCase(
    private val repository: SandboxWidgetRepository,
) {
    /**
     * Возвращает поток виджетов с применённым фильтром и стабильной сортировкой.
     *
     * @param filter выбранный пользователем фильтр.
     * @return поток подготовленного списка виджетов.
     */
    operator fun invoke(filter: SandboxWidgetFilter): Flow<List<SandboxWidget>> =
        repository.widgets.map { widgets ->
            widgets.filterBy(filter).sortedWith(compareByDescending<SandboxWidget> { it.isPinned }.thenBy { it.title })
        }

    private fun List<SandboxWidget>.filterBy(filter: SandboxWidgetFilter): List<SandboxWidget> =
        when (filter) {
            SandboxWidgetFilter.All -> this
            SandboxWidgetFilter.Pinned -> filter(SandboxWidget::isPinned)
            SandboxWidgetFilter.Experimental -> filter { it.status == SandboxWidgetStatus.Experimental }
        }
}
