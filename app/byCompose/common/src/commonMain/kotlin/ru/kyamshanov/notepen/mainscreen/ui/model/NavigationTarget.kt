package ru.kyamshanov.notepen.mainscreen.ui.model

/** Цель навигации, устанавливаемая ViewModel при переходе из главного экрана. */
sealed class NavigationTarget {

    /**
     * Открыть редактор PDF.
     *
     * @property uri Нормализованный URI файла.
     * @property lastPageIndex Последняя просмотренная страница (0-based).
     */
    data class Editor(val uri: String, val lastPageIndex: Int) : NavigationTarget()

    /** Открыть системный диалог выбора файла. */
    object FilePicker : NavigationTarget()
}
