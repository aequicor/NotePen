package ru.kyamshanov.notepen.sync.cloud.domain

/**
 * A pending OAuth device-flow authorization: the app shows [userCode] and asks
 * the user to enter it at [verificationUri] in any browser, then polls for the
 * token using [deviceCode] no more often than every [intervalSeconds].
 */
data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

/** Outcome of one poll for the device-flow access token. */
sealed interface DeviceTokenResult {
    /** The user approved; [accessToken] is a bearer token for the provider API. */
    data class Authorized(
        val accessToken: String,
    ) : DeviceTokenResult

    /** The user has not approved yet — keep polling at the current interval. */
    data object Pending : DeviceTokenResult

    /** Polled too fast — add 5s to the interval and keep polling. */
    data object SlowDown : DeviceTokenResult

    /** Terminal failure (code expired, access denied, device flow disabled, …). */
    data class Failed(
        val reason: String,
    ) : DeviceTokenResult
}
