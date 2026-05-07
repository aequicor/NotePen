package ru.kyamshanov.notepen.mainscreen.infrastructure.dto

import kotlinx.serialization.Serializable
import ru.kyamshanov.notepen.mainscreen.domain.model.RecentFile

/** DTO для сериализации [RecentFile] в инфраструктурном слое. */
@Serializable
data class RecentFileDto(
    val id: String,
    val uri: String,
    val displayName: String,
    val fileSize: Long? = null,
    val openedAt: Long,
    val availabilityStatus: AvailabilityStatusDto = AvailabilityStatusDto.UNKNOWN,
    val thumbnailKey: String? = null,
    val fileMtime: Long? = null,
    val lastPageIndex: Int = 0,
) {
    /** Преобразует DTO в доменную модель. */
    fun toDomain(): RecentFile = RecentFile(
        id = id,
        uri = uri,
        displayName = displayName,
        fileSize = fileSize,
        openedAt = openedAt,
        availabilityStatus = availabilityStatus.toDomain(),
        thumbnailKey = thumbnailKey,
        fileMtime = fileMtime,
        lastPageIndex = lastPageIndex,
    )

    companion object {
        /** Преобразует доменную модель в DTO. */
        fun fromDomain(f: RecentFile): RecentFileDto = RecentFileDto(
            id = f.id,
            uri = f.uri,
            displayName = f.displayName,
            fileSize = f.fileSize,
            openedAt = f.openedAt,
            availabilityStatus = AvailabilityStatusDto.fromDomain(f.availabilityStatus),
            thumbnailKey = f.thumbnailKey,
            fileMtime = f.fileMtime,
            lastPageIndex = f.lastPageIndex,
        )
    }
}
