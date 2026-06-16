package ru.kyamshanov.notepen.uikitsandbox.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Локальный data source sandbox-виджетов.
 *
 * Контракт имитирует Room/SQLDelight-границу, но не раскрывает механизм хранения.
 */
internal interface SandboxWidgetLocalDataSource {
    /**
     * Наблюдает полный локальный список entity.
     *
     * @return поток entity в порядке хранения.
     */
    fun observeWidgets(): Flow<List<SandboxWidgetEntity>>

    /**
     * Заменяет локальный список целиком.
     *
     * @param widgets новый список entity.
     */
    suspend fun replaceAll(widgets: List<SandboxWidgetEntity>)

    /**
     * Обновляет состояние закрепления одной entity.
     *
     * @param id строковый идентификатор entity.
     * @param isPinned новое значение закрепления.
     */
    suspend fun updatePinned(
        id: String,
        isPinned: Boolean,
    )
}

/**
 * In-memory реализация локального источника для standalone sandbox и тестов.
 *
 * @property initialWidgets начальный список entity.
 */
internal class InMemorySandboxWidgetLocalDataSource(
    initialWidgets: List<SandboxWidgetEntity> = SandboxWidgetSeeds.entities,
) : SandboxWidgetLocalDataSource {
    private val state = MutableStateFlow(initialWidgets)

    /**
     * Возвращает поток текущего in-memory состояния.
     *
     * @return state-flow как [Flow].
     */
    override fun observeWidgets(): Flow<List<SandboxWidgetEntity>> = state

    /**
     * Полностью заменяет in-memory состояние.
     *
     * @param widgets новый список entity.
     */
    override suspend fun replaceAll(widgets: List<SandboxWidgetEntity>) {
        state.value = widgets
    }

    /**
     * Обновляет закрепление entity с указанным [id].
     *
     * @param id идентификатор entity.
     * @param isPinned новое значение закрепления.
     */
    override suspend fun updatePinned(
        id: String,
        isPinned: Boolean,
    ) {
        state.update { widgets ->
            widgets.map { widget ->
                if (widget.id == id) widget.copy(isPinned = isPinned) else widget
            }
        }
    }
}

private object SandboxWidgetSeeds {
    val entities: List<SandboxWidgetEntity> =
        listOf(
            SandboxWidgetEntity(
                id = "button",
                title = "Buttons",
                description = "Command surfaces with stable sizing and clear enabled states.",
                status = "Stable",
                isPinned = true,
            ),
            SandboxWidgetEntity(
                id = "tool-rail",
                title = "Tool Rail",
                description = "Orientation-aware controls for stylus tools.",
                status = "Experimental",
                isPinned = false,
            ),
            SandboxWidgetEntity(
                id = "dialogs",
                title = "Dialogs",
                description = "Focused confirmation flows with component-owned events.",
                status = "Stable",
                isPinned = false,
            ),
        )
}
