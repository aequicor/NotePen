package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.datatransfer.DataFlavor
import java.io.File

private val logger = KotlinLogging.logger {}

/** Поддерживаемые расширения документов — синхронизировано с FilePicker.jvm. */
private val ALLOWED_EXTERNAL_EXTENSIONS = listOf(".pdf", ".png", ".jpg", ".jpeg")

/**
 * Desktop-реализация: извлекает строку URI из AWT [Transferable] в drag-событии.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun extractFileUri(event: DragAndDropEvent): String? =
    try {
        val transferable = event.awtTransferable
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            transferable.getTransferData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    } catch (e: Exception) {
        logger.warn { "extractFileUri failed: ${e::class.simpleName}" }
        null
    }

/**
 * Desktop-реализация: определяет MIME-типы drag-события по поддерживаемым DataFlavor.
 *
 * Внутренние drag-передачи используют [DataFlavor.stringFlavor] (text/plain;charset=unicode),
 * что соответствует фильтру [shouldAcceptDragEvent] по "text/plain".
 */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun DragAndDropEvent.dragEventMimeTypes(): Set<String> =
    try {
        val transferable = awtTransferable
        buildSet {
            if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                add("text/plain")
            }
        }
    } catch (e: Exception) {
        logger.warn { "dragEventMimeTypes failed: ${e::class.simpleName}" }
        emptySet()
    }

/**
 * Desktop-реализация: внешний drop из Finder/проводника несёт [DataFlavor.javaFileListFlavor].
 */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun DragAndDropEvent.isExternalFileDrop(): Boolean =
    try {
        awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    } catch (e: Exception) {
        logger.warn { "isExternalFileDrop failed: ${e::class.simpleName}" }
        false
    }

/**
 * Desktop-реализация: читает [DataFlavor.javaFileListFlavor], фильтрует по поддерживаемым
 * расширениям и возвращает канонические пути.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun extractExternalFileUris(event: DragAndDropEvent): List<String> =
    try {
        val transferable = event.awtTransferable
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            val files =
                (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    .orEmpty()
                    .filterIsInstance<File>()
            files
                .filter { file -> ALLOWED_EXTERNAL_EXTENSIONS.any { file.name.endsWith(it, ignoreCase = true) } }
                .map { it.canonicalPath }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        logger.warn { "extractExternalFileUris failed: ${e::class.simpleName}" }
        emptyList()
    }
