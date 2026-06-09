package ru.kyamshanov.notepen.sync.domain.port

import ru.kyamshanov.notepen.sync.domain.SessionCipherException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// JVM (Desktop) implementation of the crypto primitives declared in
// CryptoPrimitives.kt (commonMain). Both NotePen targets run on a JVM, so the
// Android actual is byte-for-byte identical; the duplication is deliberate (this
// module has no shared jvm+android source set — see build.gradle.kts).

private const val HMAC_ALGORITHM = "HmacSHA256"
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
private const val AES_KEY_ALGORITHM = "AES"
private const val GCM_TAG_BITS = 128
private const val HASH_LEN = 32
private const val MAX_HKDF_BLOCKS = 255

actual fun hkdfSha256(
    secret: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    lengthBytes: Int,
): ByteArray {
    require(lengthBytes > 0) { "lengthBytes must be positive, was $lengthBytes" }
    require(lengthBytes <= MAX_HKDF_BLOCKS * HASH_LEN) {
        "lengthBytes $lengthBytes exceeds HKDF-SHA256 maximum ${MAX_HKDF_BLOCKS * HASH_LEN}"
    }

    // Extract: PRK = HMAC(salt, IKM). An empty salt is replaced with HashLen zero bytes (RFC 5869 §2.2).
    val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
    val prk = hmac(key = effectiveSalt, data = secret)

    // Expand: T(i) = HMAC(PRK, T(i-1) || info || i); OKM = T(1) || T(2) || ... truncated to length.
    val output = ByteArray(lengthBytes)
    var previousBlock = ByteArray(0)
    var generated = 0
    var counter = 1
    while (generated < lengthBytes) {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))
        mac.update(previousBlock)
        mac.update(info)
        mac.update(counter.toByte())
        previousBlock = mac.doFinal()
        val toCopy = minOf(previousBlock.size, lengthBytes - generated)
        previousBlock.copyInto(output, destinationOffset = generated, startIndex = 0, endIndex = toCopy)
        generated += toCopy
        counter++
    }
    return output
}

actual fun aesGcmSeal(
    key32: ByteArray,
    nonce12: ByteArray,
    aad: ByteArray,
    plaintext: ByteArray,
): ByteArray {
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(
        Cipher.ENCRYPT_MODE,
        SecretKeySpec(key32, AES_KEY_ALGORITHM),
        GCMParameterSpec(GCM_TAG_BITS, nonce12),
    )
    if (aad.isNotEmpty()) cipher.updateAAD(aad)
    return cipher.doFinal(plaintext)
}

actual fun aesGcmOpen(
    key32: ByteArray,
    nonce12: ByteArray,
    aad: ByteArray,
    ciphertext: ByteArray,
): ByteArray {
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
    cipher.init(
        Cipher.DECRYPT_MODE,
        SecretKeySpec(key32, AES_KEY_ALGORITHM),
        GCMParameterSpec(GCM_TAG_BITS, nonce12),
    )
    if (aad.isNotEmpty()) cipher.updateAAD(aad)
    return try {
        cipher.doFinal(ciphertext)
    } catch (e: GeneralSecurityException) {
        // AEADBadTagException (a GeneralSecurityException) on auth failure — wrap
        // so callers catch a domain type and never see the platform exception.
        throw SessionCipherException("AES-GCM authentication failed", e)
    }
}

actual fun secureRandomBytes(n: Int): ByteArray {
    require(n >= 0) { "n must be non-negative, was $n" }
    val bytes = ByteArray(n)
    SecureRandom().nextBytes(bytes)
    return bytes
}

private fun hmac(
    key: ByteArray,
    data: ByteArray,
): ByteArray {
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
    return mac.doFinal(data)
}
