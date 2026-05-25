package ru.kyamshanov.notepen.mainscreen.infrastructure.dto

import kotlinx.serialization.Serializable
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus

/** DTO для сериализации [AvailabilityStatus] в инфраструктурном слое. */
@Serializable
enum class AvailabilityStatusDto {
    UNKNOWN,
    AVAILABLE,
    NOT_FOUND,
    FILE_ERROR,
    ARCHIVED_UNAVAILABLE,
    ;

    /** Преобразует DTO в доменную модель. */
    fun toDomain(): AvailabilityStatus =
        when (this) {
            UNKNOWN -> AvailabilityStatus.UNKNOWN
            AVAILABLE -> AvailabilityStatus.AVAILABLE
            NOT_FOUND -> AvailabilityStatus.NOT_FOUND
            FILE_ERROR -> AvailabilityStatus.FILE_ERROR
            ARCHIVED_UNAVAILABLE -> AvailabilityStatus.ARCHIVED_UNAVAILABLE
        }

    companion object {
        /** Преобразует доменную модель в DTO. */
        fun fromDomain(status: AvailabilityStatus): AvailabilityStatusDto =
            when (status) {
                AvailabilityStatus.UNKNOWN -> UNKNOWN
                AvailabilityStatus.AVAILABLE -> AVAILABLE
                AvailabilityStatus.NOT_FOUND -> NOT_FOUND
                AvailabilityStatus.FILE_ERROR -> FILE_ERROR
                AvailabilityStatus.ARCHIVED_UNAVAILABLE -> ARCHIVED_UNAVAILABLE
            }
    }
}
