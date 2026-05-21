package ru.kyamshanov.notepen.mainscreen.infrastructure.dto

import kotlinx.serialization.Serializable
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder

/** DTO для сериализации [Folder] в инфраструктурном слое. */
@Serializable
data class FolderDto(
    val id: String,
    val name: String,
    val createdAt: Long,
    // Бэк-совместимость: старый folders.json без поля → папка верхнего уровня (null).
    val parentId: String? = null,
) {
    /** Преобразует DTO в доменную модель. */
    fun toDomain(): Folder = Folder(
        id = id,
        name = name,
        createdAt = createdAt,
        parentId = parentId,
    )

    companion object {
        /** Преобразует доменную модель в DTO. */
        fun fromDomain(f: Folder): FolderDto = FolderDto(
            id = f.id,
            name = f.name,
            createdAt = f.createdAt,
            parentId = f.parentId,
        )
    }
}
