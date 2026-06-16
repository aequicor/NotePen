package ru.kyamshanov.notepen.uikitsandbox.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.uikitsandbox.data.local.SandboxWidgetLocalDataSource
import ru.kyamshanov.notepen.uikitsandbox.data.remote.SandboxWidgetRemoteDataSource
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxOutcome
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidget
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetFailure
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetId
import ru.kyamshanov.notepen.uikitsandbox.domain.repository.SandboxWidgetRepository
import ru.kyamshanov.notepen.uikitsandbox.utils.SandboxDispatchers
import ru.kyamshanov.notepen.uikitsandbox.utils.sandboxSafeCall

/**
 * Реализация repository для демонстрационной sandbox-фичи.
 *
 * Класс объединяет локальный источник состояния и удалённый источник обновлений,
 * показывая целевую зависимость data-слоя от domain-порта.
 *
 * @property localDataSource источник локального списка виджетов.
 * @property remoteDataSource источник данных для ручного refresh.
 * @property dispatchers набор dispatcher-ов для потенциально долгой работы.
 */
internal class SandboxWidgetRepositoryImpl(
    private val localDataSource: SandboxWidgetLocalDataSource,
    private val remoteDataSource: SandboxWidgetRemoteDataSource,
    private val dispatchers: SandboxDispatchers,
) : SandboxWidgetRepository {
    /**
     * Поток доменных моделей, полученный из локальных entity.
     */
    override val widgets: Flow<List<SandboxWidget>> =
        localDataSource.observeWidgets()
            .map { entities -> entities.map { it.toDomain() } }

    /**
     * Загружает remote-список и заменяет локальные данные только непустым результатом.
     *
     * @return [SandboxOutcome.Success] при успешной попытке или network failure при ошибке.
     */
    override suspend fun refresh(): SandboxOutcome<Unit> =
        sandboxSafeCall(SandboxWidgetFailure.Network) {
            val remoteWidgets = withContext(dispatchers.io) { remoteDataSource.fetchWidgets() }
            if (remoteWidgets.isNotEmpty()) {
                localDataSource.replaceAll(remoteWidgets.map { it.toEntity() })
            }
        }

    /**
     * Обновляет состояние закрепления в локальном источнике.
     *
     * @param id идентификатор виджета.
     * @param isPinned новое значение закрепления.
     * @return [SandboxOutcome.Success] или storage failure при ошибке записи.
     */
    override suspend fun setPinned(
        id: SandboxWidgetId,
        isPinned: Boolean,
    ): SandboxOutcome<Unit> =
        sandboxSafeCall(SandboxWidgetFailure.Storage) {
            localDataSource.updatePinned(id.value, isPinned)
        }
}
