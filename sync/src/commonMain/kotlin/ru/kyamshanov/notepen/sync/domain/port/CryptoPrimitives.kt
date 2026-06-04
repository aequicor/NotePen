package ru.kyamshanov.notepen.sync.domain.port

/**
 * Minimal platform crypto port backing [ru.kyamshanov.notepen.sync.domain.SessionCipher].
 *
 * Lives in `domain/port` (not `infrastructure`) because the pure-Kotlin domain
 * `SessionCipher` consumes it, and domain must never depend on infrastructure.
 * The public surface is platform-agnostic (only `ByteArray` / `Int`); the actual
 * implementations in `jvmMain` / `androidMain` are thin wrappers over
 * `javax.crypto` / `java.security` (every NotePen target is JVM-based, so both
 * ship the JCA). They are declared as top-level expect functions — matching the
 * `okio_*` / `encodeBase64` expect/actual style elsewhere in this module — and
 * are duplicated identically across the two platform source sets because there is
 * no shared jvm+android source set in the build.
 *
 * These functions are intentionally low-level and stateless; all framing, nonce
 * management and replay protection live in the pure-Kotlin [SessionCipher].
 */

/**
 * HKDF (RFC 5869) extract-and-expand over HMAC-SHA-256.
 *
 * Derives [lengthBytes] of key material from input keying material [secret],
 * an optional [salt] and a context-binding [info] label.
 *
 * @param secret input keying material (IKM); the shared pairing secret.
 * @param salt optional salt; may be empty (treated as a zero-filled HashLen block per RFC 5869).
 * @param info context/application-specific info string; binds the output to a purpose.
 * @param lengthBytes number of output bytes requested; must be in `1..255 * 32` (8160).
 * @return exactly [lengthBytes] of derived key material.
 */
expect fun hkdfSha256(
    secret: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    lengthBytes: Int,
): ByteArray

/**
 * AES-256-GCM authenticated encryption (`AES/GCM/NoPadding`, 128-bit tag).
 *
 * @param key32 32-byte AES-256 key.
 * @param nonce12 12-byte (96-bit) nonce; MUST be unique per key.
 * @param aad additional authenticated data; covered by the tag but not encrypted (may be empty).
 * @param plaintext data to encrypt.
 * @return ciphertext with the 16-byte authentication tag appended.
 */
expect fun aesGcmSeal(
    key32: ByteArray,
    nonce12: ByteArray,
    aad: ByteArray,
    plaintext: ByteArray,
): ByteArray

/**
 * AES-256-GCM authenticated decryption (`AES/GCM/NoPadding`, 128-bit tag).
 *
 * @param key32 32-byte AES-256 key.
 * @param nonce12 12-byte (96-bit) nonce used when sealing.
 * @param aad additional authenticated data that was supplied to [aesGcmSeal].
 * @param ciphertext ciphertext with the 16-byte authentication tag appended.
 * @return the recovered plaintext.
 * @throws ru.kyamshanov.notepen.sync.domain.SessionCipherException on authentication failure
 *   (wrong key, tampered ciphertext/tag, or mismatched [aad]/[nonce12]).
 */
expect fun aesGcmOpen(
    key32: ByteArray,
    nonce12: ByteArray,
    aad: ByteArray,
    ciphertext: ByteArray,
): ByteArray

/**
 * Returns [n] cryptographically strong random bytes from the platform CSPRNG
 * (`java.security.SecureRandom`).
 *
 * @param n number of random bytes to generate; must be `>= 0`.
 */
expect fun secureRandomBytes(n: Int): ByteArray
