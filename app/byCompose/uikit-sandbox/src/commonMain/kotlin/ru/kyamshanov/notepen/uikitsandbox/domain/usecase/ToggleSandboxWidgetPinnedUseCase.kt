package ru.kyamshanov.notepen.uikitsandbox.domain.usecase

import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxOutcome
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetId
import ru.kyamshanov.notepen.uikitsandbox.domain.repository.SandboxWidgetRepository

/**
 * Use case изменения закрепления sandbox-виджета.
 *
 * @property repository доменный порт записи состояния.
 */
internal class ToggleSandboxWidgetPinnedUseCase(
    private val repository: SandboxWidgetRepository,
) {
    /**
     * Сохраняет новое состояние закрепления.
     *
     * @param id идентификатор изменяемого виджета.
     * @param isPinned новое значение закрепления.
     * @return результат записи без проброса recoverable-исключений наружу.
     */
    suspend operator fun invoke(
        id: SandboxWidgetId,
        isPinned: Boolean,
    ): SandboxOutcome<Unit> = repository.setPinned(id, isPinned)
}
