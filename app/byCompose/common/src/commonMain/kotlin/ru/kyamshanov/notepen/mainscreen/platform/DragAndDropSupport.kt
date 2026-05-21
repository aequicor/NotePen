package ru.kyamshanov.notepen.mainscreen.platform

/**
 * Можно ли регистрировать AWT DropTarget (`Modifier.dragAndDropTarget`) без порчи отрисовки.
 *
 * На macOS Skiko рендерит через Metal, и регистрация DropTarget ломает present `ImageBitmap`:
 * только что появившиеся миниатюры не доходят до экрана (см. [rememberPdfThumbnailPainter]).
 * OpenGL, который снимает конфликт на Windows, на macOS недоступен. Поэтому на macOS
 * drop-таргеты не регистрируются (перетаскивание-источник из приложения сохраняется).
 */
expect val isDragAndDropSupported: Boolean
