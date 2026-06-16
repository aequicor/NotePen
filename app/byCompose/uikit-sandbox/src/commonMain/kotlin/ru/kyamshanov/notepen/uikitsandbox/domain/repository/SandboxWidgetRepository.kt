package ru.kyamshanov.notepen.uikitsandbox.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxOutcome
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidget
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetId

/**
 * Доменный порт хранилища sandbox-виджетов.
 *
 * Repository скрывает локальные и удалённые data source от use case-слоя.
 */
internal interface SandboxWidgetRepository {
    /**
     * Поток актуального локального списка виджетов.
     */
    val widgets: Flow<List<SandboxWidget>>

    /**
     * Обновляет локальный список из удалённого источника.
     *
     * @return [SandboxOutcome.Success] при успешном обновлении или [SandboxOutcome.Failure]
     * при recoverable-ошибке.
     */
    suspend fun refresh(): SandboxOutcome<Unit>

    /**
     * Сохраняет состояние закрепления виджета.
     *
     * @param id идентификатор изменяемого виджета.
     * @param isPinned новое значение закрепления.
     * @return [SandboxOutcome.Success] при успешной записи или [SandboxOutcome.Failure]
     * при recoverable-ошибке.
     */
    suspend fun setPinned(
        id: SandboxWidgetId,
        isPinned: Boolean,
    ): SandboxOutcome<Unit>
}
