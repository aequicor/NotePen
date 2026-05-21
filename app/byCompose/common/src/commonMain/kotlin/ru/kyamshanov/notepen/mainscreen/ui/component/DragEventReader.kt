package ru.kyamshanov.notepen.mainscreen.ui.component

import androidx.compose.ui.draganddrop.DragAndDropEvent

/**
 * Извлекает URI файла из события drag-and-drop.
 *
 * @param event Событие drag-and-drop от платформы.
 * @return Строка URI если данные доступны и валидны; null в противном случае.
 */
internal expect fun extractFileUri(event: DragAndDropEvent): String?

/**
 * Возвращает набор MIME-типов, объявленных в событии drag-and-drop.
 *
 * Используется [shouldAcceptDragEvent] для фильтрации внешних drag-событий ОС (EC-11, MEDIUM #2).
 *
 * @return Набор строк MIME-типов или пустое множество, если типы не определены.
 */
internal expect fun DragAndDropEvent.dragEventMimeTypes(): Set<String>

/**
 * Признак внешнего (OS-level) перетаскивания файлов — из Finder/проводника, а не
 * внутреннего app-drag'а (карточка → папка).
 *
 * @return true, если событие несёт список файлов файловой системы.
 */
internal expect fun DragAndDropEvent.isExternalFileDrop(): Boolean

/**
 * Извлекает канонические пути файлов из внешнего OS-drop'а, отфильтрованные по
 * поддерживаемым типам документов (PDF, PNG, JPEG).
 *
 * @param event Событие drag-and-drop от платформы.
 * @return Список путей или пустой список, если файлов нет либо они неподдерживаемого типа.
 */
internal expect fun extractExternalFileUris(event: DragAndDropEvent): List<String>
