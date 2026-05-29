package ru.kyamshanov.notepen.mainscreen.platform

import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.channels.Channel

/**
 * Android-реализация [FilePicker].
 *
 * Запускает системный диалог выбора файла через [ActivityResultLauncher].
 * Результат возвращается через [Channel]. Вызов [onResult] регистрирует выбранный URI.
 *
 * Интеграция с `rememberLauncherForActivityResult` будет добавлена в Stage 07.
 */
actual class FilePicker {
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private val resultChannel = Channel<String?>(1)

    /**
     * Инициализирует FilePicker с [ActivityResultLauncher].
     * Должен вызываться до [pickDocument].
     */
    fun init(launcher: ActivityResultLauncher<Array<String>>) {
        this.launcher = launcher
    }

    /**
     * Вызывается из Activity/Fragment при получении результата от системного диалога.
     */
    fun onResult(uri: android.net.Uri?) {
        resultChannel.trySend(uri?.toString())
    }

    actual suspend fun pickDocument(): String? {
        val activeLauncher = launcher ?: return null
        activeLauncher.launch(
            arrayOf(
                "application/pdf",
                "image/png",
                "image/jpeg",
                "application/epub+zip",
                "application/x-fictionbook+xml",
                "application/vnd.comicbook+zip",
                "application/vnd.comicbook-rar",
                // ACTION_OPEN_DOCUMENT filters strictly by the DocumentsProvider's
                // reported MIME (extensions are ignored). Real .fb2 (and many
                // .cbz/.cbr) are reported as application/octet-stream by the
                // Downloads/Storage providers, so without this entry those rows
                // render disabled. Generic-MIME picks are resolved by extension
                // downstream (detectBookFormat over the resolved display name).
                "application/octet-stream",
            ),
        )
        return resultChannel.receive()
    }
}
