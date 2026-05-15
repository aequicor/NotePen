package ru.kyamshanov.notepen.sync.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath

/**
 * Atomic change to a page's stroke list.
 *
 * [Added]: a new stroke was drawn on [pageIndex] by [authorDeviceId].
 * [Removed]: the stroke with [strokeId] was erased. Last-writer-wins by [clock].
 *
 * All coordinates in [DrawingPath] are normalised [0..1].
 */
@Serializable
sealed class StrokeDelta {

    abstract val strokeId: String
    abstract val pageIndex: Int
    abstract val authorDeviceId: String
    abstract val clock: Long

    @Serializable
    @SerialName("added")
    data class Added(
        override val strokeId: String,
        override val pageIndex: Int,
        override val authorDeviceId: String,
        override val clock: Long,
        val path: DrawingPathDto,
    ) : StrokeDelta()

    @Serializable
    @SerialName("removed")
    data class Removed(
        override val strokeId: String,
        override val pageIndex: Int,
        override val authorDeviceId: String,
        override val clock: Long,
    ) : StrokeDelta()
}

/** Serialisation-safe version of [DrawingPath] (avoids Compose dependency in shared). */
@Serializable
data class DrawingPathDto(
    val strokeId: String,
    val colorArgb: Long,
    val strokeWidth: Float,
    val points: List<PointDto>,
)

@Serializable
data class PointDto(val x: Float, val y: Float, val isNewPath: Boolean = false)

fun DrawingPath.toDto(strokeId: String): DrawingPathDto =
    DrawingPathDto(
        strokeId = strokeId,
        colorArgb = colorArgb,
        strokeWidth = strokeWidth,
        points = points.map { PointDto(it.x, it.y, it.isNewPath) },
    )

fun DrawingPathDto.toDomain(): DrawingPath =
    DrawingPath(
        points = points.map {
            ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint(it.x, it.y, it.isNewPath)
        },
        colorArgb = colorArgb,
        strokeWidth = strokeWidth,
    )
