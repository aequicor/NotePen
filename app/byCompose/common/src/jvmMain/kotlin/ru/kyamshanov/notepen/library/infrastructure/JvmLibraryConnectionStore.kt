package ru.kyamshanov.notepen.library.infrastructure

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionStore
import ru.kyamshanov.notepen.mainscreen.infrastructure.getAppDataDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.deleteIfExists

private val logger = KotlinLogging.logger {}

/**
 * Desktop (JVM) [LibraryConnectionStore]. Persists the saved [LibraryConnection]s — which can carry
 * secrets (GitHub PAT, OAuth refresh token, peer pairing code) — to `library_connections.json` under
 * the app data dir ([getAppDataDir]), **encrypted at rest** with AES-256-GCM (authenticated
 * encryption).
 *
 * Desktop has no OS keychain binding, so the data key is a random 256-bit AES key kept in a sibling
 * `library.key` file restricted to the owner (POSIX `rw-------` where the filesystem supports it; on
 * Windows the restriction is best-effort and silently skipped). The encrypted blob is
 * `magic || iv(12) || ciphertext+tag`; the 96-bit GCM nonce is freshly drawn for every write. The
 * connections file itself is also set owner-only.
 *
 * Legacy plaintext connection files (a bare JSON array, written before encryption landed) are detected
 * by the absence of the [CONNECTIONS_MAGIC] prefix; on first load such a file is decoded and then
 * immediately re-saved encrypted, transparently upgrading it in place.
 *
 * Writes are atomic: the bytes are staged to a temp file and then `ATOMIC_MOVE`d over the target, so an
 * interrupted save never leaves a truncated/corrupt file. A [Mutex] serialises in-process access; a
 * read of an absent or unreadable file (missing key, tampered ciphertext, corrupt JSON) yields an empty
 * list rather than throwing. Backward compatibility of the JSON payload relies on the polymorphic
 * sealed serializer + property defaults of [LibraryConnection] (`ignoreUnknownKeys = true` tolerates
 * fields written by newer versions).
 *
 * @param dataDir directory the connections file lives in; defaults to the app data dir.
 * @param ioDispatcher dispatcher for the blocking file I/O.
 */
public class JvmLibraryConnectionStore(
    private val dataDir: Path = getAppDataDir().toPath(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryConnectionStore {
    private val mutex = Mutex()
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val file: Path get() = dataDir.resolve(FILE_NAME)
    private val keyFile: Path get() = dataDir.resolve(KEY_FILE_NAME)

    override suspend fun load(): List<LibraryConnection> =
        withContext(ioDispatcher) {
            mutex.withLock { readUnsafe() }
        }

    override suspend fun save(connections: List<LibraryConnection>) {
        withContext(ioDispatcher) {
            mutex.withLock { writeUnsafe(connections) }
        }
    }

    override suspend fun add(connection: LibraryConnection): List<LibraryConnection> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val updated = readUnsafe().filterNot { sameConnection(it, connection) } + connection
                writeUnsafe(updated)
                updated
            }
        }

    override suspend fun remove(connection: LibraryConnection): List<LibraryConnection> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val updated = readUnsafe().filterNot { sameConnection(it, connection) }
                writeUnsafe(updated)
                updated
            }
        }

    /**
     * Whether [a] and [b] denote the SAME saved library for de-dup. Two [LibraryConnection.Local] specs
     * are the same iff they share a [LibraryConnection.Local.rootPath] — the folder is the identity, the
     * display name is not — so re-adding a folder under a new name replaces rather than duplicates it
     * (and a disconnect removes it regardless of the name it was saved under). All other kinds compare by
     * full value equality.
     */
    private fun sameConnection(
        a: LibraryConnection,
        b: LibraryConnection,
    ): Boolean =
        if (a is LibraryConnection.Local && b is LibraryConnection.Local) {
            a.rootPath == b.rootPath
        } else {
            a == b
        }

    private fun readUnsafe(): List<LibraryConnection> {
        val path = file
        if (!Files.exists(path)) return emptyList()
        return runCatching {
            val raw = Files.readAllBytes(path)
            if (isLegacyPlaintext(raw)) {
                migrateLegacy(raw)
            } else {
                json.decodeFromString(serializer, decrypt(raw, loadOrCreateKey()).decodeToString())
            }
        }.getOrElse { t ->
            logger.warn(t) { "LibraryConnectionStore: cannot read $path, falling back to empty" }
            emptyList()
        }
    }

    /**
     * Decodes a pre-encryption plaintext JSON file, then re-saves it encrypted so the next read takes
     * the encrypted path. A failure to re-save is non-fatal — the decoded list is still returned.
     */
    private fun migrateLegacy(raw: ByteArray): List<LibraryConnection> =
        runCatching {
            json.decodeFromString(serializer, raw.decodeToString())
        }.map { decoded ->
            logger.info { "LibraryConnectionStore: migrating plaintext $file to encrypted at-rest format" }
            runCatching { writeUnsafe(decoded) }
                .onFailure { t -> logger.warn(t) { "LibraryConnectionStore: re-encrypt of $file failed; will retry next save" } }
            decoded
        }.getOrElse { t ->
            logger.warn(t) { "LibraryConnectionStore: cannot read $file, falling back to empty" }
            emptyList()
        }

    private fun writeUnsafe(connections: List<LibraryConnection>) {
        runCatching {
            Files.createDirectories(dataDir)
            val blob = encrypt(json.encodeToString(serializer, connections).encodeToByteArray(), loadOrCreateKey())
            val tmp = dataDir.resolve("$FILE_NAME.tmp")
            Files.write(tmp, blob)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            restrictToOwner(file)
        }.onFailure { t ->
            logger.warn(t) { "LibraryConnectionStore: cannot save connections to $file" }
            runCatching { dataDir.resolve("$FILE_NAME.tmp").deleteIfExists() }
        }
    }

    /**
     * Returns the persisted AES key, creating (and persisting, owner-only) a fresh random 256-bit key on
     * first use. The key file holds the raw key bytes — its protection is the owner-only filesystem
     * permission, matching the threat model (a local at-rest secret with no OS keychain to bind to).
     */
    private fun loadOrCreateKey(): SecretKeySpec {
        val path = keyFile
        if (Files.exists(path)) {
            val bytes = Files.readAllBytes(path)
            if (bytes.size == KEY_SIZE_BYTES) return SecretKeySpec(bytes, KEY_ALGORITHM)
            logger.warn { "LibraryConnectionStore: $path has unexpected size ${bytes.size}; regenerating key" }
        }
        Files.createDirectories(dataDir)
        val key = KeyGenerator.getInstance(KEY_ALGORITHM).apply { init(KEY_SIZE_BITS, SecureRandom()) }.generateKey()
        val tmp = dataDir.resolve("$KEY_FILE_NAME.tmp")
        Files.write(tmp, key.encoded)
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        restrictToOwner(path)
        return SecretKeySpec(key.encoded, KEY_ALGORITHM)
    }

    private fun encrypt(
        plaintext: ByteArray,
        key: SecretKeySpec,
    ): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        val ciphertext = cipher.doFinal(plaintext)
        return CONNECTIONS_MAGIC + iv + ciphertext
    }

    private fun decrypt(
        blob: ByteArray,
        key: SecretKeySpec,
    ): ByteArray {
        require(blob.size >= CONNECTIONS_MAGIC.size + GCM_IV_BYTES) { "encrypted blob too short" }
        val iv = blob.copyOfRange(CONNECTIONS_MAGIC.size, CONNECTIONS_MAGIC.size + GCM_IV_BYTES)
        val ciphertext = blob.copyOfRange(CONNECTIONS_MAGIC.size + GCM_IV_BYTES, blob.size)
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        return cipher.doFinal(ciphertext)
    }

    /**
     * Best-effort: restrict [path] to owner read/write only. Uses POSIX permissions where the
     * filesystem supports them; on filesystems without POSIX views (e.g. Windows) the call is skipped
     * silently — the data is still encrypted, so this is defence in depth, not the primary control.
     */
    private fun restrictToOwner(path: Path) {
        runCatching {
            val view = Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
            if (view != null) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
            }
        }.onFailure { t ->
            logger.debug(t) { "LibraryConnectionStore: cannot restrict permissions on $path (non-POSIX fs?)" }
        }
    }

    private companion object {
        const val FILE_NAME = "library_connections.json"
        const val KEY_FILE_NAME = "library.key"

        const val KEY_ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val KEY_SIZE_BYTES = KEY_SIZE_BITS / 8
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128

        /** Prefix identifying an encrypted blob; absent in legacy plaintext JSON (which starts with `[`). */
        val CONNECTIONS_MAGIC = "NPLC1\n".encodeToByteArray()

        val serializer = kotlinx.serialization.builtins.ListSerializer(LibraryConnection.serializer())

        /** A file is legacy plaintext when it does not carry the encrypted-blob [CONNECTIONS_MAGIC]. */
        fun isLegacyPlaintext(raw: ByteArray): Boolean =
            raw.size < CONNECTIONS_MAGIC.size ||
                !CONNECTIONS_MAGIC.indices.all { raw[it] == CONNECTIONS_MAGIC[it] }
    }
}
