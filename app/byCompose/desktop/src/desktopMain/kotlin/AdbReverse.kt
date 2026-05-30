import com.sun.jna.Platform
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.qrconnect.CableDevice
import ru.kyamshanov.notepen.qrconnect.CablePairing
import ru.kyamshanov.notepen.qrconnect.CableState
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Desktop [CablePairing] backed by `adb reverse`.
 *
 * Lets a USB-tethered Android tablet reach this host at `127.0.0.1:<port>` over
 * the cable: `adb reverse tcp:<port> tcp:<port>` forwards the device's loopback
 * port to the desktop's. The host server already binds `0.0.0.0`, so no server
 * change is needed — the tablet pairs with a `notepen://pair?h=127.0.0.1…`
 * payload exactly as it would over Wi-Fi.
 *
 * adb is **not bundled**; it is auto-detected from the user's existing Android
 * SDK (see [detectAdb]). When absent the state becomes [CableState.NoTool].
 *
 * All process work runs on [ioDispatcher]; cancelling the calling coroutine
 * between adb invocations aborts cleanly (adb commands themselves are short).
 *
 * @param locator override for tests; defaults to the SDK/PATH detection chain.
 */
class AdbReverse(
    private val ioDispatcher: CoroutineDispatcher,
    private val locator: () -> String? = ::detectAdb,
) : CablePairing {
    private val _state = MutableStateFlow<CableState>(CableState.Idle)
    override val state: StateFlow<CableState> = _state.asStateFlow()

    override suspend fun start(
        port: Int,
        serial: String?,
    ) {
        withContext(ioDispatcher) {
            val adb = locator()
            if (adb == null) {
                _state.value = CableState.NoTool
                return@withContext
            }
            val usable = listDevices(adb).filter { it.state == "device" }
            if (usable.isEmpty()) {
                _state.value = CableState.NoDevice
                return@withContext
            }
            if (serial == null && usable.size > 1) {
                _state.value = CableState.MultipleDevices(usable)
                return@withContext
            }
            val target = serial ?: usable.first().serial
            val result =
                runCatching {
                    runProcess(listOf(adb, "-s", target, "reverse", "tcp:$port", "tcp:$port"))
                }.getOrElse {
                    _state.value = CableState.Error(it.message ?: "adb reverse failed")
                    return@withContext
                }
            _state.value =
                if (result.exitCode == 0) {
                    logger.info { "adb reverse tcp:$port installed for $target" }
                    CableState.Ready(serial = target, port = port)
                } else {
                    CableState.Error(result.output.trim().ifBlank { "adb reverse exit ${result.exitCode}" })
                }
        }
    }

    override suspend fun stop(
        port: Int,
        serial: String?,
    ) {
        withContext(ioDispatcher) {
            val adb = locator()
            if (adb == null) {
                _state.value = CableState.Idle
                return@withContext
            }
            val target = serial ?: (_state.value as? CableState.Ready)?.serial
            val command =
                if (target != null) {
                    listOf(adb, "-s", target, "reverse", "--remove", "tcp:$port")
                } else {
                    listOf(adb, "reverse", "--remove", "tcp:$port")
                }
            runCatching { runProcess(command) }
                .onFailure { logger.warn { "adb reverse --remove failed: ${it.message}" } }
            _state.value = CableState.Idle
        }
    }

    private fun listDevices(adb: String): List<CableDevice> {
        val result = runCatching { runProcess(listOf(adb, "devices")) }.getOrNull() ?: return emptyList()
        return result.output
            .lineSequence()
            .drop(1) // "List of devices attached" header
            .mapNotNull { line ->
                val parts = line.trim().split('\t', ' ').filter { it.isNotBlank() }
                if (parts.size >= 2) CableDevice(serial = parts[0], state = parts[1]) else null
            }.toList()
    }

    private fun runProcess(command: List<String>): ProcessResult {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        return try {
            // Bound the wait so a wedged/zombie adb daemon can't pin an IO thread.
            // adb output is tiny (< pipe buffer), so draining after exit is safe.
            val finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                ProcessResult(exitCode = -1, output = "adb timed out")
            } else {
                ProcessResult(exitCode = process.exitValue(), output = process.inputStream.bufferedReader().readText())
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private data class ProcessResult(
        val exitCode: Int,
        val output: String,
    )

    companion object {
        /**
         * Locates an adb executable, in priority order: `ANDROID_HOME`,
         * `ANDROID_SDK_ROOT`, the OS-default SDK install, then bare `adb` on PATH
         * (verified with `adb version`). Returns null if none works.
         */
        fun detectAdb(): String? {
            val exe = if (Platform.isWindows()) "adb.exe" else "adb"
            val roots =
                buildList {
                    System.getenv("ANDROID_HOME")?.let { add(it) }
                    System.getenv("ANDROID_SDK_ROOT")?.let { add(it) }
                    System.getenv("LOCALAPPDATA")?.let { add(File(it, "Android\\Sdk").path) }
                    System.getProperty("user.home")?.let { home ->
                        add(File(home, "Library/Android/sdk").path)
                        add(File(home, "Android/Sdk").path)
                    }
                }
            roots
                .map { File(File(it, "platform-tools"), exe) }
                .firstOrNull { it.isFile }
                ?.let { return it.absolutePath }
            return if (canRun(exe)) exe else null
        }

        private fun canRun(exe: String): Boolean =
            runCatching {
                val probe = ProcessBuilder(exe, "version").redirectErrorStream(true).start()
                val finished = probe.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!finished) probe.destroyForcibly()
                finished
            }.getOrDefault(false)

        private const val PROCESS_TIMEOUT_SECONDS = 10L
    }
}
