package ru.kyamshanov.notepen.mainscreen.ui.model

/** Состояние загрузки миниатюры для записи истории файлов. */
sealed class ThumbnailState {
    /** Миниатюра загружается или генерируется. */
    object Loading : ThumbnailState()

    /**
     * Миниатюра готова.
     *
     * @property imageData Закодированные данные изображения.
     */
    data class Ready(val imageData: ByteArray) : ThumbnailState() {
        override fun equals(other: Any?): Boolean = other is Ready && imageData.contentEquals(other.imageData)

        override fun hashCode(): Int = imageData.contentHashCode()
    }

    /** Ошибка генерации миниатюры (файл повреждён, OOM и т.п.). */
    object Error : ThumbnailState()
}
