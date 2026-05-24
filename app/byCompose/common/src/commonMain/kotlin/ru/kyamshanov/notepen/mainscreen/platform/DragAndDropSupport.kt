package ru.kyamshanov.notepen.mainscreen.platform

/**
 * Можно ли регистрировать AWT DropTarget (`Modifier.dragAndDropTarget` /
 * `Modifier.dragAndDropSource`) без порчи отрисовки.
 *
 * Регистрация DropTarget ломает present `ImageBitmap`: только что появившиеся миниатюры
 * не доходят до экрана до следующей перерисовки (см. [rememberPdfThumbnailPainter]). На
 * десктопе с аппаратным ускорением (macOS — Metal, Windows — DirectX/ANGLE) это
 * воспроизводится, поэтому там drop-таргеты не регистрируются. На Android и Linux конфликта нет.
 */
expect val isDragAndDropSupported: Boolean
