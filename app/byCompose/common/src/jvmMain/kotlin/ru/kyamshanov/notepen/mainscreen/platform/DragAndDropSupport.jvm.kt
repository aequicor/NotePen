package ru.kyamshanov.notepen.mainscreen.platform

/**
 * Регистрация AWT DropTarget ломает отрисовку `ImageBitmap` (миниатюр) на десктопе с
 * аппаратным ускорением — и на macOS (Skiko/Metal), и на Windows (DirectX/ANGLE).
 * Поэтому на обеих платформах drop-таргеты не регистрируются; DnD остаётся только на Linux.
 */
actual val isDragAndDropSupported: Boolean =
    System.getProperty("os.name").orEmpty().let { os ->
        !os.startsWith("Mac", ignoreCase = true) && !os.startsWith("Windows", ignoreCase = true)
    }
