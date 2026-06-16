package ru.kyamshanov.notepen.uikitsandbox.domain.usecase

import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxOutcome
import ru.kyamshanov.notepen.uikitsandbox.domain.repository.SandboxWidgetRepository

/**
 * Use case ручного обновления sandbox-виджетов.
 *
 * @property repository доменный порт обновления данных.
 */
internal class RefreshSandboxWidgetsUseCase(
    private val repository: SandboxWidgetRepository,
) {
    /**
     * Запускает обновление repository.
     *
     * @return результат обновления без проброса recoverable-исключений наружу.
     */
    suspend operator fun invoke(): SandboxOutcome<Unit> = repository.refresh()
}
