package ru.kyamshanov.notepen.document.domain

/**
 * Lowercase `sha256` hex digest (64 chars) of [bytes].
 *
 * Backed by the platform crypto provider via expect/actual (JVM and Android
 * both use `java.security.MessageDigest`), so no third-party dependency is
 * pulled in. Pure with respect to its input: identical bytes always yield the
 * identical digest — the property the content-addressed identity relies on.
 */
expect fun sha256Hex(bytes: ByteArray): String
