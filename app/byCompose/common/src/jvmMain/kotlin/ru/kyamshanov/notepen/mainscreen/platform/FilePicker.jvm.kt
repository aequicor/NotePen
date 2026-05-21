package ru.kyamshanov.notepen.mainscreen.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop (JVM)-реализация [FilePicker].
 *
 * Показывает системный диалог выбора файла через [java.awt.FileDialog].
 * Возвращает канонический путь выбранного файла или null при отмене.
 *
 * Без [java.io.FilenameFilter]: на macOS (JBR) фильтр по нескольким расширениям
 * ненадёжен — native-панель может «гасить» не-PDF файлы, из-за чего PNG/JPEG
 * нельзя выбрать. Тип файла валидируется ниже по стеку (загрузчик), поэтому
 * безопаснее показать все файлы, чем потерять возможность открыть изображение.
 */
actual class FilePicker {
    actual suspend fun pickDocument(): String? = withContext(Dispatchers.IO) {
        var resultDir: String? = null
        var resultFile: String? = null
        java.awt.EventQueue.invokeAndWait {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Открыть документ", java.awt.FileDialog.LOAD)
            dialog.isVisible = true
            resultDir = dialog.directory
            resultFile = dialog.file
        }
        val dir = resultDir ?: return@withContext null
        val file = resultFile ?: return@withContext null
        java.io.File(dir, file).canonicalPath
    }
}
