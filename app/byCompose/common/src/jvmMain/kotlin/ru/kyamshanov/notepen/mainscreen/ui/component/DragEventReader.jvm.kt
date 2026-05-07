package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.awtTransferable
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.datatransfer.DataFlavor

private val logger = KotlinLogging.logger {}

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
