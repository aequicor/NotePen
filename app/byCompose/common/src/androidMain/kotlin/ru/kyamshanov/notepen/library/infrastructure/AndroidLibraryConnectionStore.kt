package ru.kyamshanov.notepen.library.infrastructure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.library.api.LibraryConnection
import ru.kyamshanov.notepen.library.api.LibraryConnectionStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val logger = KotlinLogging.logger {}

/**
 * Android [LibraryConnectionStore]. Persists the saved [LibraryConnection]s — which can carry secrets
 * (GitHub PAT, OAuth refresh token, peer pairing code) — to `library_connections.json` under
 * `context.filesDir`, **encrypted at rest** with AES-256-GCM.
 *
 * The data key lives in the Android Keystore (`AndroidKeyStore`) under [KEY_ALIAS] and never leaves the
 * secure store: the OS keeps the raw key material, this class only asks the keystore-backed [Cipher] to
 * encrypt/decrypt. The on-disk blob is `magic || iv(12) || ciphertext+tag`; the GCM nonce is generated
 * by the cipher on every write and stored alongside the ciphertext.
 *
 * Legacy plaintext connection files (a bare JSON array, written before encryption landed) are detected
 * by the absence of the [CONNECTIONS_MAGIC] prefix; on first load such a file is decoded and then
 * immediately re-saved encrypted, transparently upgrading it in place.
 *
 * Writes stage to a temp file and then `renameTo` the target. Note this is **not** a guaranteed atomic
 * move on Android (`File.renameTo` is best-effort and not atomic across all filesystems), but it still
 * avoids a partial overwrite of the live file in the common case. A [Mutex] serialises in-process
 * access; a read of an absent/unreadable file (missing key, tampered ciphertext, corrupt JSON) yields an
 * empty list. Backward compatibility of the JSON payload relies on the polymorphic sealed serializer +
 * property defaults of [LibraryConnection] (`ignoreUnknownKeys = true`).
 *
 * @param context application context providing `filesDir`.
 * @param ioDispatcher dispatcher for the blocking file I/O.
 */
public class AndroidLibraryConnectionStore(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryConnectionStore {
    private val mutex = Mutex()
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val file: File get() = File(context.filesDir, FILE_NAME)
    private val tmpFile: File get() = File(context.filesDir, "$FILE_NAME.tmp")

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
                val updated = readUnsafe().filterNot { it == connection } + connection
                writeUnsafe(updated)
                updated
            }
        }

    override suspend fun remove(connection: LibraryConnection): List<LibraryConnection> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val updated = readUnsafe().filterNot { it == connection }
                writeUnsafe(updated)
                updated
            }
        }

    private fun readUnsafe(): List<LibraryConnection> {
        val f = file
        if (!f.exists()) return emptyList()
        return runCatching {
            val raw = f.readBytes()
            if (isLegacyPlaintext(raw)) {
                migrateLegacy(raw)
            } else {
                json.decodeFromString(serializer, decrypt(raw).decodeToString())
            }
        }.getOrElse { t ->
            logger.warn(t) { "LibraryConnectionStore: cannot read $f, falling back to empty" }
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
            val blob = encrypt(json.encodeToString(serializer, connections).encodeToByteArray())
            val tmp = tmpFile
            tmp.writeBytes(blob)
            if (!tmp.renameTo(file)) {
                // renameTo can fail if the target already exists on some filesystems; fall back to
                // an overwrite-in-place (loses atomicity but keeps the data consistent on success).
                file.writeBytes(tmp.readBytes())
                tmp.delete()
            }
        }.onFailure { t ->
            logger.warn(t) { "LibraryConnectionStore: cannot save connections to $file" }
            runCatching { tmpFile.delete() }
        }
    }

    /**
     * Returns the AES key from the Android Keystore, generating a keystore-bound 256-bit GCM key under
     * [KEY_ALIAS] on first use. The raw key material stays inside the keystore.
     */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES) { "unexpected GCM iv length ${iv.size}" }
        return CONNECTIONS_MAGIC + iv + cipher.doFinal(plaintext)
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        require(blob.size >= CONNECTIONS_MAGIC.size + GCM_IV_BYTES) { "encrypted blob too short" }
        val iv = blob.copyOfRange(CONNECTIONS_MAGIC.size, CONNECTIONS_MAGIC.size + GCM_IV_BYTES)
        val ciphertext = blob.copyOfRange(CONNECTIONS_MAGIC.size + GCM_IV_BYTES, blob.size)
        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val FILE_NAME = "library_connections.json"

        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "notepen_library_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128

        /** Prefix identifying an encrypted blob; absent in legacy plaintext JSON (which starts with `[`). */
        val CONNECTIONS_MAGIC = "NPLC1\n".encodeToByteArray()

        val serializer = ListSerializer(LibraryConnection.serializer())

        /** A file is legacy plaintext when it does not carry the encrypted-blob [CONNECTIONS_MAGIC]. */
        fun isLegacyPlaintext(raw: ByteArray): Boolean =
            raw.size < CONNECTIONS_MAGIC.size ||
                !CONNECTIONS_MAGIC.indices.all { raw[it] == CONNECTIONS_MAGIC[it] }
    }
}
