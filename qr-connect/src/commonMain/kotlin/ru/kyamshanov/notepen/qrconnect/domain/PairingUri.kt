package ru.kyamshanov.notepen.qrconnect.domain

import ru.kyamshanov.notepen.sync.domain.model.DeviceInfo

/**
 * Pairing handshake payload transported through a QR code.
 *
 * Wire form: `notepen://pair?h=<host>&p=<port>&c=<code>&n=<deviceName>` where
 * the device name is URL-encoded. The scheme is custom and never sent to a
 * resolver — it exists only to identify NotePen pairing QRs and reject random
 * URLs the camera might pick up.
 */
data class PairingUri(
    val host: String,
    val port: Int,
    val code: String,
    val deviceName: String,
) {
    /** Serializes back to the canonical `notepen://pair?...` form. */
    fun encode(): String =
        "$SCHEME://$HOST?h=${encodeComponent(host)}" +
            "&p=$port" +
            "&c=${encodeComponent(code)}" +
            "&n=${encodeComponent(deviceName)}"

    /**
     * Returns a [DeviceInfo] suitable for `SyncClient.connect(server, code, selfInfo)`.
     * The peer id is unknown until the server replies with `PairAccepted` — using
     * the host:port composite as a placeholder is fine: the real id arrives in
     * [ru.kyamshanov.notepen.sync.domain.model.NetworkMessage.PairAccepted].
     */
    fun toServerDeviceInfo(): DeviceInfo = DeviceInfo(id = "$host:$port", name = deviceName, host = host, port = port)

    companion object {
        const val SCHEME: String = "notepen"
        const val HOST: String = "pair"

        /**
         * Parses [raw] into a [PairingUri], or returns `null` if the string is not a
         * well-formed NotePen pairing URI. Validation is intentionally strict — any
         * deviation from the expected scheme/host/required keys is rejected so the
         * scanner cannot be tricked by an arbitrary QR.
         */
        fun parse(raw: String): PairingUri? {
            val prefix = "$SCHEME://$HOST?"
            if (!raw.startsWith(prefix)) return null
            val params =
                raw
                    .substring(prefix.length)
                    .split('&')
                    .mapNotNull { pair ->
                        val eq = pair.indexOf('=')
                        if (eq <= 0) null else pair.substring(0, eq) to pair.substring(eq + 1)
                    }.toMap()
            val host = params["h"]?.let(::decodeComponent) ?: return null
            val port = params["p"]?.toIntOrNull() ?: return null
            val code = params["c"]?.let(::decodeComponent) ?: return null
            val name = params["n"]?.let(::decodeComponent) ?: return null
            if (host.isBlank() || code.isBlank() || port !in 1..PORT_MAX) return null
            return PairingUri(host = host, port = port, code = code, deviceName = name)
        }

        private const val PORT_MAX = 65_535
    }
}

private fun encodeComponent(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val b = byte.toInt() and 0xFF
        val safe =
            (b in 'A'.code..'Z'.code) ||
                (b in 'a'.code..'z'.code) ||
                (b in '0'.code..'9'.code) ||
                b == '-'.code ||
                b == '_'.code ||
                b == '.'.code ||
                b == '~'.code
        if (safe) {
            out.append(b.toChar())
        } else {
            out.append('%')
            out.append(HEX[(b ushr 4) and 0xF])
            out.append(HEX[b and 0xF])
        }
    }
    return out.toString()
}

private fun decodeComponent(value: String): String {
    val bytes = ByteArray(value.length)
    var size = 0
    var i = 0
    while (i < value.length) {
        val c = value[i]
        when {
            c == '%' && i + 2 < value.length -> {
                val hi = hexDigit(value[i + 1])
                val lo = hexDigit(value[i + 2])
                if (hi < 0 || lo < 0) return value
                bytes[size++] = ((hi shl 4) or lo).toByte()
                i += 3
            }
            c == '+' -> {
                bytes[size++] = ' '.code.toByte()
                i++
            }
            else -> {
                bytes[size++] = c.code.toByte()
                i++
            }
        }
    }
    return bytes.copyOf(size).decodeToString()
}

private fun hexDigit(c: Char): Int =
    when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> 10 + (c - 'a')
        in 'A'..'F' -> 10 + (c - 'A')
        else -> -1
    }

private val HEX =
    charArrayOf(
        '0',
        '1',
        '2',
        '3',
        '4',
        '5',
        '6',
        '7',
        '8',
        '9',
        'A',
        'B',
        'C',
        'D',
        'E',
        'F',
    )
