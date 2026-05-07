package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Android-реализация: извлекает строку URI из ClipData в drag-событии.
 */
internal actual fun extractFileUri(event: DragAndDropEvent): String? =
    try {
        val androidEvent = event.toAndroidDragEvent()
        androidEvent.clipData?.getItemAt(0)?.text?.toString()
    } catch (e: Exception) {
        logger.warn { "extractFileUri failed: ${e::class.simpleName}" }
        null
    }

/**
 * Android-реализация: возвращает MIME-типы из ClipDescription drag-события.
 */
internal actual fun DragAndDropEvent.dragEventMimeTypes(): Set<String> =
    try {
        mimeTypes()
    } catch (e: Exception) {
        logger.warn { "dragEventMimeTypes failed: ${e::class.simpleName}" }
        emptySet()
    }
