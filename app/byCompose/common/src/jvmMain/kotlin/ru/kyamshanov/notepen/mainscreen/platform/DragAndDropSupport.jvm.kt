package ru.kyamshanov.notepen.mainscreen.platform

/**
 * На macOS (Skiko/Metal) регистрация AWT DropTarget ломает отрисовку `ImageBitmap`,
 * поэтому drop-таргеты там отключаются. На Windows/Linux конфликта нет.
 */
actual val isDragAndDropSupported: Boolean =
    !System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)
