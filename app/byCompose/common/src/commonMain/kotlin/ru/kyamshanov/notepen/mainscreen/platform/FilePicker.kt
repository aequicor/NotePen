package ru.kyamshanov.notepen.mainscreen.platform

/**
 * Платформенный диалог выбора документа (PDF или изображение PNG/JPEG).
 *
 * Реализации предоставляются в `androidMain` и `jvmMain`.
 * Возвращает нормализованный URI выбранного файла или null, если пользователь отменил выбор.
 */
expect class FilePicker {

    /**
     * Открывает системный диалог выбора документа (PDF, PNG или JPEG).
     *
     * @return Нормализованный URI выбранного файла или null при отмене.
     */
    suspend fun pickDocument(): String?
}
