package ru.kyamshanov.notepen

import com.arkivanov.decompose.value.Value

interface DetailsComponent {
    val model: Value<Model>

    fun onBack()

    /**
     * Открывает экран библиотеки (главный экран) поверх текущего документа,
     * не закрывая его. Пользователь выбирает там файл; жест «назад» (или
     * back-кнопка библиотеки) возвращает к текущему документу.
     *
     * Используется кнопкой «+» на панели вкладок.
     */
    fun openLibrary()

    /**
     * Файл, выбранный в библиотеке (открытой кнопкой «+»), который нужно
     * открыть новой вкладкой в текущем редакторе. Пустая строка, когда ничего
     * не ожидает открытия (Decompose `Value` не допускает nullable). UI
     * наблюдает это значение и после открытия вызывает [onPendingTabHandled].
     */
    val pendingTabUri: Value<String>

    /** Сбрасывает [pendingTabUri] после того, как UI открыл соответствующую вкладку. */
    fun onPendingTabHandled()

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