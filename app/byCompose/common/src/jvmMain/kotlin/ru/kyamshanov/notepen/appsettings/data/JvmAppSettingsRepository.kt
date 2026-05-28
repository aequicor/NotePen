package ru.kyamshanov.notepen.appsettings.data

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.appsettings.domain.model.AppSettings
import ru.kyamshanov.notepen.appsettings.domain.port.AppSettingsRepository
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.deleteIfExists

private val logger = KotlinLogging.logger {}

/**
 * JSON-репозиторий [AppSettings] на JVM. Файл `app_settings.json` лежит рядом
 * с прочими настройками приложения ([getAppDataDir]). Запись — atomic rename
 * через temp-файл, чтобы внезапный crash не оставил повреждённого JSON.
 *
 * Держит in-memory `MutableStateFlow`: первая подписка триггерит ленивую
 * загрузку с диска; последующие `save` обновляют и стейт, и файл.
 */
class JvmAppSettingsRepository(
    private val configDir: Path = defaultConfigDir(),
    private val ioContext: CoroutineContext = Dispatchers.IO,
) : AppSettingsRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow(AppSettings())
    private val scope = CoroutineScope(SupervisorJob() + ioContext)
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    init {
        scope.launch { state.value = load() }
    }

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    private val file: Path
        get() = configDir.resolve(FILE_NAME)

    override suspend fun load(): AppSettings =
        withContext(ioContext) {
            mutex.withLock {
                val path = file
                if (!Files.exists(path)) {
                    return@withLock AppSettings()
                }
                runCatching {
                    val text = Files.readString(path)
                    json.decodeFromString(AppSettings.serializer(), text)
                }.getOrElse { t ->
                    logger.warn(t) { "AppSettingsRepository: cannot read $path, falling back to defaults" }
                    AppSettings()
                }
            }
        }

    override suspend fun save(settings: AppSettings) {
        state.value = settings
        withContext(ioContext) {
            mutex.withLock {
                runCatching {
                    Files.createDirectories(configDir)
                    val tmp = configDir.resolve("$FILE_NAME.tmp")
                    val text = json.encodeToString(AppSettings.serializer(), settings)
                    Files.writeString(tmp, text)
                    Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                }.onFailure { t ->
                    logger.warn(t) { "AppSettingsRepository: cannot save settings to $file" }
                    runCatching { configDir.resolve("$FILE_NAME.tmp").deleteIfExists() }
                }
            }
        }
    }

    private companion object {
        const val FILE_NAME = "app_settings.json"

        fun defaultConfigDir(): Path = getAppDataDir().toPath()
    }
}
