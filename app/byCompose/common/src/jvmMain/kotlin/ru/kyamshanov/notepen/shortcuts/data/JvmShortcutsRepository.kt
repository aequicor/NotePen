package ru.kyamshanov.notepen.shortcuts.data

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir
import ru.kyamshanov.notepen.shortcuts.domain.model.ShortcutsSettings
import ru.kyamshanov.notepen.shortcuts.domain.port.ShortcutsRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.deleteIfExists

private val logger = KotlinLogging.logger {}

/**
 * JSON-репозиторий настроек шорткатов на JVM.
 *
 * Файл `shortcuts.json` — в каталоге данных приложения (`getAppDataDir`):
 * `${user.home}/.notepen` либо рядом с `.exe` в portable. При первом запуске
 * или ошибке чтения возвращаются значения по умолчанию. Запись идёт через
 * temp-файл + atomic rename, чтобы внезапный crash посреди save'а не
 * оставил повреждённый JSON.
 *
 * [ioContext] инжектится: продакшен — [Dispatchers.IO]; в тестах — заменяется
 * на `UnconfinedTestDispatcher`.
 */
class JvmShortcutsRepository(
    private val configDir: Path = defaultConfigDir(),
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : ShortcutsRepository {
    private val mutex = Mutex()
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val file: Path
        get() = configDir.resolve(FILE_NAME)

    override suspend fun load(): ShortcutsSettings =
        withContext(ioContext) {
            mutex.withLock {
                val path = file
                if (!Files.exists(path)) {
                    return@withLock ShortcutsSettings()
                }
                runCatching {
                    val text = Files.readString(path)
                    json.decodeFromString(ShortcutsSettings.serializer(), text)
                }.getOrElse { t ->
                    logger.warn(t) { "ShortcutsRepository: cannot read $path, falling back to defaults" }
                    ShortcutsSettings()
                }
            }
        }

    override suspend fun save(settings: ShortcutsSettings): Unit =
        withContext(ioContext) {
            mutex.withLock {
                runCatching {
                    Files.createDirectories(configDir)
                    val tmp = configDir.resolve("$FILE_NAME.tmp")
                    val text = json.encodeToString(ShortcutsSettings.serializer(), settings)
                    Files.writeString(tmp, text)
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }.onFailure { t ->
                    logger.warn(t) { "ShortcutsRepository: cannot save settings to $file" }
                    runCatching { configDir.resolve("$FILE_NAME.tmp").deleteIfExists() }
                }
            }
        }

    private companion object {
        const val FILE_NAME = "shortcuts.json"

        fun defaultConfigDir(): Path = getAppDataDir().toPath()
    }
}
