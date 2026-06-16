package ru.kyamshanov.notepen.uikitsandbox.data

import ru.kyamshanov.notepen.uikitsandbox.data.local.SandboxWidgetEntity
import ru.kyamshanov.notepen.uikitsandbox.data.remote.SandboxWidgetDto
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidget
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetId
import ru.kyamshanov.notepen.uikitsandbox.domain.model.SandboxWidgetStatus

/**
 * Преобразует локальную entity в чистую доменную модель.
 *
 * @return sandbox-виджет без data-source деталей.
 */
internal fun SandboxWidgetEntity.toDomain(): SandboxWidget =
    SandboxWidget(
        id = SandboxWidgetId(id),
        title = title,
        description = description,
        status = status.toStatus(),
        isPinned = isPinned,
    )

/**
 * Преобразует DTO удалённого источника в entity локального хранения.
 *
 * @return entity, пригодная для замены локального списка.
 */
internal fun SandboxWidgetDto.toEntity(): SandboxWidgetEntity =
    SandboxWidgetEntity(
        id = id,
        title = title,
        description = description,
        status = status.toStatus().name,
        isPinned = isPinned,
    )

private fun String.toStatus(): SandboxWidgetStatus =
    SandboxWidgetStatus.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }
        ?: SandboxWidgetStatus.Experimental
