package ru.kyamshanov.notepen.mainscreen.ui.model

/** Состояние операции перетаскивания файла на главном экране. */
sealed class DragState {
    /**
     * Файл активно перетаскивается.
     *
     * @property fileId Идентификатор записи в истории.
     * @property fileUri Нормализованный URI файла.
     * @property displayName Отображаемое имя файла.
     */
    data class Active(
        val fileId: String,
        val fileUri: String,
        val displayName: String,
    ) : DragState()

    /** Нет активной операции перетаскивания. */
    object None : DragState()
}
