package ru.kyamshanov.notepen.magnifier

import androidx.compose.ui.geometry.Rect

/**
 * Адаптер [MagnifierState] к [MagnifierGeometry] для [MagnifierInputController].
 *
 * Читает состояние лупы «вживую» на каждый вызов, поэтому одного экземпляра
 * достаточно на всё время жизни окна лупы.
 */
fun MagnifierState.asMagnifierGeometry(): MagnifierGeometry {
    val state = this
    return object : MagnifierGeometry {
        override val pageCanvasWidthPx: Float get() = state.pageCanvasWidthPx
        override val segments: List<MagnifierPageSegment> get() = state.segments
        override val autoScrollEnabled: Boolean get() = state.autoScrollEnabled

        override fun setSingleSegmentTarget(
            pageIndex: Int,
            targetOnPage: Rect,
        ) {
            state.setSingleSegmentTarget(pageIndex, targetOnPage)
        }
    }
}
