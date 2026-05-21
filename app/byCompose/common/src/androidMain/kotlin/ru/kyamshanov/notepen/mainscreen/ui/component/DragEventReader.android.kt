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

/**
 * Android-реализация: внешний DnD из ОС не поддерживается (нет файлового менеджера-источника
 * уровня Finder/проводника). Всегда false.
 */
internal actual fun DragAndDropEvent.isExternalFileDrop(): Boolean = false

/**
 * Android-реализация: внешний OS-drop не поддерживается — см. [isExternalFileDrop].
 */
internal actual fun extractExternalFileUris(event: DragAndDropEvent): List<String> = emptyList()
