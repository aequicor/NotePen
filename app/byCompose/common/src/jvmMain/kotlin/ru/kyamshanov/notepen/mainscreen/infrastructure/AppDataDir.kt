package ru.kyamshanov.notepen.mainscreen.infrastructure

import java.io.File

/**
 * Имя файла-маркера portable-режима. Лежит рядом с лаунчером (`NotePen.exe`) в
 * portable-ZIP и отсутствует в инсталляторной сборке — именно его наличие
 * переключает приложение на хранение данных рядом с исполняемым файлом.
 */
private const val PORTABLE_MARKER_FILE = "NotePen.portable"

/** Подпапка с данными в portable-режиме (рядом с лаунчером). */
private const val PORTABLE_DATA_DIR = "data"

/**
 * Возвращает директорию хранилища данных приложения для Desktop (JVM): sync-БД,
 * история открытий, папки, превью, пресеты инструментов, шорткаты.
 *
 * Режимы:
 * - **portable** — если рядом с лаунчером есть файл [PORTABLE_MARKER_FILE],
 *   данные пишутся в `<папка лаунчера>/[PORTABLE_DATA_DIR]`. Сборка ничего не
 *   оставляет в системе и переносится целиком (например, на флешке). Путь к
 *   лаунчеру берётся из системного свойства `jpackage.app-path`, которое
 *   выставляет сгенерированный jpackage лаунчер app-image (и в инсталляторной,
 *   и в portable-сборке — различает их только наличие маркера).
 * - **обычный** — `$HOME/.notepen/` (инсталлятор и `./gradlew run`, где
 *   `jpackage.app-path` не задан).
 *
 * Директория создаётся, если не существует.
 */
fun getAppDataDir(): File =
    (portableDataDir() ?: File(System.getProperty("user.home"), ".notepen"))
        .also { it.mkdirs() }

/**
 * Каталог данных рядом с лаунчером, если активен portable-режим, иначе `null`.
 */
private fun portableDataDir(): File? {
    val launcherDir = System.getProperty("jpackage.app-path")
        ?.let(::File)
        ?.parentFile
        ?: return null
    val portable = File(launcherDir, PORTABLE_MARKER_FILE).isFile
    return if (portable) File(launcherDir, PORTABLE_DATA_DIR) else null
}
