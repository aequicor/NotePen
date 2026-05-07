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
     * Должен вызываться до [pickPdfFile].
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

    actual suspend fun pickPdfFile(): String? {
        val activeLauncher = launcher ?: return null
        activeLauncher.launch(arrayOf("application/pdf"))
        return resultChannel.receive()
    }
}
