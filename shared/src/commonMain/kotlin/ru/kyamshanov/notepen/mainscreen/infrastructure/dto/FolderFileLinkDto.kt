package ru.kyamshanov.notepen.mainscreen.infrastructure.dto

import kotlinx.serialization.Serializable
import ru.kyamshanov.notepen.mainscreen.domain.model.FolderFileLink

/** DTO для сериализации [FolderFileLink] в инфраструктурном слое. */
@Serializable
data class FolderFileLinkDto(
    val folderId: String,
    val fileUri: String,
    val lastOpenedAt: Long,
) {
    /** Преобразует DTO в доменную модель. */
    fun toDomain(): FolderFileLink = FolderFileLink(
        folderId = folderId,
        fileUri = fileUri,
        lastOpenedAt = lastOpenedAt,
    )

    companion object {
        /** Преобразует доменную модель в DTO. */
        fun fromDomain(f: FolderFileLink): FolderFileLinkDto = FolderFileLinkDto(
            folderId = f.folderId,
            fileUri = f.fileUri,
            lastOpenedAt = f.lastOpenedAt,
        )
    }
}
