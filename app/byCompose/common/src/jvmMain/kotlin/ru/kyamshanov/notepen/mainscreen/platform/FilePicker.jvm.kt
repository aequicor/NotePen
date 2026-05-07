package ru.kyamshanov.notepen.mainscreen.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop (JVM)-реализация [FilePicker].
 *
 * Показывает системный диалог выбора файла через [java.awt.FileDialog].
 * Фильтрует файлы по расширению `.pdf`.
 * Возвращает канонический путь выбранного файла или null при отмене.
 */
actual class FilePicker {
    actual suspend fun pickPdfFile(): String? = withContext(Dispatchers.IO) {
        var resultDir: String? = null
        var resultFile: String? = null
        java.awt.EventQueue.invokeAndWait {
            val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Открыть PDF", java.awt.FileDialog.LOAD)
            dialog.filenameFilter = java.io.FilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
            dialog.isVisible = true
            resultDir = dialog.directory
            resultFile = dialog.file
        }
        val dir = resultDir ?: return@withContext null
        val file = resultFile ?: return@withContext null
        java.io.File(dir, file).canonicalPath
    }
}
