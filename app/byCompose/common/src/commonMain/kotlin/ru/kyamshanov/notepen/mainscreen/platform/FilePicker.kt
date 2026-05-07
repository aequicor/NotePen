package ru.kyamshanov.notepen.mainscreen.platform

/**
 * Платформенный диалог выбора PDF-файла.
 *
 * Реализации предоставляются в `androidMain` и `jvmMain`.
 * Возвращает нормализованный URI выбранного файла или null, если пользователь отменил выбор.
 */
expect class FilePicker {

    /**
     * Открывает системный диалог выбора PDF.
     *
     * @return Нормализованный URI выбранного файла или null при отмене.
     */
    suspend fun pickPdfFile(): String?
}
