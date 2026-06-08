package ru.kyamshanov.notepen.reflow.ui.bookcurl

import ru.kyamshanov.notepen.reflow.api.BookCurlMaterialId

/**
 * Визуально-физический пресет перелистывания: радиус завитка и упругость пружины оседания.
 * PAPER — мягкий бумажный лист с лёгким отскоком. COVER — тугая обложка, чёткий снэп.
 */
internal data class PageTurnStyle(
    /** Радиус цилиндра завитка как доля ширины страницы: крупнее = мягче. */
    val radiusFrac: Float,
    /** Коэффициент затухания пружины Compose (1.0 = без отскока, < 1 = отскок). */
    val settleDampingRatio: Float,
    /** Жёсткость пружины Compose: выше = быстрее оседает. */
    val settleStiffness: Float,
) {
    companion object {
        val Default: PageTurnStyle = paper()

        fun of(material: BookCurlMaterialId): PageTurnStyle =
            when (material) {
                BookCurlMaterialId.PAPER -> paper()
                BookCurlMaterialId.COVER -> cover()
            }

        private fun paper() = PageTurnStyle(radiusFrac = 0.10f, settleDampingRatio = 0.75f, settleStiffness = 200f)

        private fun cover() = PageTurnStyle(radiusFrac = 0.06f, settleDampingRatio = 1.00f, settleStiffness = 600f)
    }
}
