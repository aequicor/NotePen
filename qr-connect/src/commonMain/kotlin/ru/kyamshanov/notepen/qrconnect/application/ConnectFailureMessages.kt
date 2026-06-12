package ru.kyamshanov.notepen.qrconnect.application

import ru.kyamshanov.notepen.sync.domain.exception.SyncConnectException
import ru.kyamshanov.notepen.sync.domain.model.ConnectFailureKind
import ru.kyamshanov.notepen.sync.domain.model.LocalNetworkHealth

/**
 * Turns a connect failure into the user-facing message shown by the pairing
 * UIs (QR scan, manual connect, LAN discovery).
 *
 * A blocked local network fails *silently* — outbound TCP just times out — so
 * a bare "не удалось подключиться" sends the user to reboot the PC when the
 * actual fix is "turn off the VPN" or "grant the local-network permission".
 * The [LocalNetworkHealth] snapshot turns those cases into actionable hints.
 */
object ConnectFailureMessages {
    /** Message for a failed connect attempt. */
    fun describe(
        error: Throwable?,
        health: LocalNetworkHealth?,
    ): String {
        val kind = (error as? SyncConnectException)?.kind ?: ConnectFailureKind.UNKNOWN
        val base =
            when (kind) {
                ConnectFailureKind.TIMED_OUT ->
                    "Хост не ответил. Убедитесь, что устройства в одной сети Wi-Fi."
                ConnectFailureKind.LOCAL_NETWORK_BLOCKED ->
                    "Система заблокировала доступ к локальной сети."
                ConnectFailureKind.CONNECTION_REFUSED ->
                    "Соединение отклонено — убедитесь, что NotePen на ПК запущен и сервер включён."
                ConnectFailureKind.PAIRING_REJECTED ->
                    error?.message ?: "Код подключения отклонён."
                ConnectFailureKind.UNKNOWN ->
                    error?.message ?: "Не удалось подключиться."
            }
        // Pairing was rejected over a working transport — network hints would mislead.
        if (kind == ConnectFailureKind.PAIRING_REJECTED) return base
        return base + hints(health)
    }

    /** Message for an attempt that hit the outer connect deadline. */
    fun timedOut(health: LocalNetworkHealth?): String =
        "Таймаут подключения — хост не ответил. Убедитесь, что устройства в одной сети Wi-Fi." + hints(health)

    private fun hints(health: LocalNetworkHealth?): String =
        buildString {
            if (health == null) return@buildString
            if (!health.localNetworkPermissionGranted) {
                append(" Разрешите NotePen доступ к локальной сети: Настройки → Приложения → NotePen → Разрешения.")
            }
            if (health.vpnActive) {
                append(
                    " Включён VPN — он может перехватывать трафик локальной сети." +
                        " Отключите VPN или добавьте NotePen в его исключения.",
                )
            }
        }
}
