package ru.kyamshanov.notepen.mainscreen.infrastructure

/**
 * Возвращает директорию хранилища данных приложения для Desktop (JVM).
 * Расположение: `$HOME/.notepen/`.
 * Директория создаётся, если не существует.
 */
internal fun getAppDataDir(): java.io.File =
    java.io.File(System.getProperty("user.home"), ".notepen").also { it.mkdirs() }
