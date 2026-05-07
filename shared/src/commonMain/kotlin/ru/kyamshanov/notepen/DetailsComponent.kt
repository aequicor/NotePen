package ru.kyamshanov.notepen

import com.arkivanov.decompose.value.Value

interface DetailsComponent {
    val model: Value<Model>

    fun onBack()

    /**
     * Сохраняет последний видимый индекс страницы в историю файлов.
     * Вызывается из DetailsContent при уходе со страницы (onPause / onDispose).
     *
     * BL-14: triggered by DetailsContent on pause/back.
     * Tracking: ADR-006.
     *
     * @param pageIndex Текущий индекс страницы (0-based).
     */
    fun saveLastPageIndex(pageIndex: Int)

    data class Model(
        val title: String,
    )
}