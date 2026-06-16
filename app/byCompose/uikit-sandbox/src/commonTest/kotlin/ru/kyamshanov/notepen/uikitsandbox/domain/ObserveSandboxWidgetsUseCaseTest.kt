package ru.kyamshanov.notepen.uikitsandbox.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxOutcome
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidget
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetFilter
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetId
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetStatus
import ru.kyamshanov.notepen.uikitsandbox.domain.repository.SandboxWidgetRepository
import ru.kyamshanov.notepen.uikitsandbox.domain.usecase.ObserveSandboxWidgetsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Тесты сортировки и фильтрации [ObserveSandboxWidgetsUseCase].
 */
class ObserveSandboxWidgetsUseCaseTest {
    /**
     * Проверяет, что закреплённые виджеты поднимаются перед алфавитной сортировкой.
     */
    @Test
    fun pins_are_sorted_first_WHEN_all_widgets_observed() =
        runTest {
            val useCase = ObserveSandboxWidgetsUseCase(FakeRepository())

            val widgets = useCase(SandboxWidgetFilter.All).first()

            assertEquals(listOf("Pinned", "Alpha", "Beta"), widgets.map(SandboxWidget::title))
        }

    /**
     * Проверяет, что experimental-фильтр возвращает только экспериментальные виджеты.
     */
    @Test
    fun only_experimental_widgets_returned_WHEN_experimental_filter_selected() =
        runTest {
            val useCase = ObserveSandboxWidgetsUseCase(FakeRepository())

            val widgets = useCase(SandboxWidgetFilter.Experimental).first()

            assertEquals(listOf("Beta"), widgets.map(SandboxWidget::title))
        }

    private class FakeRepository : SandboxWidgetRepository {
        override val widgets: MutableStateFlow<List<SandboxWidget>> =
            MutableStateFlow(
                listOf(
                    widget(title = "Beta", status = SandboxWidgetStatus.Experimental),
                    widget(title = "Pinned", isPinned = true),
                    widget(title = "Alpha"),
                ),
            )

        override suspend fun refresh(): SandboxOutcome<Unit> = SandboxOutcome.Success(Unit)

        override suspend fun setPinned(
            id: SandboxWidgetId,
            isPinned: Boolean,
        ): SandboxOutcome<Unit> = SandboxOutcome.Success(Unit)

        private fun widget(
            title: String,
            status: SandboxWidgetStatus = SandboxWidgetStatus.Stable,
            isPinned: Boolean = false,
        ): SandboxWidget =
            SandboxWidget(
                id = SandboxWidgetId(title.lowercase()),
                title = title,
                description = title,
                status = status,
                isPinned = isPinned,
            )
    }
}
