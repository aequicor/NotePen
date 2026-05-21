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

    /**
     * Открыть sub-экран со списком папок и документов выбранного пира.
     *
     * @property peerId Стабильный id пира (`DeviceInfo.id`).
     * @property displayName Имя пира — используется как заголовок sub-экрана.
     */
    data class PeerCatalog(val peerId: String, val displayName: String) : NavigationTarget()

    /**
     * Открыть sub-экран содержимого папки.
     *
     * @property folderId UUID папки.
     * @property folderName Имя папки — заголовок sub-экрана.
     */
    data class Folder(val folderId: String, val folderName: String) : NavigationTarget()
}
