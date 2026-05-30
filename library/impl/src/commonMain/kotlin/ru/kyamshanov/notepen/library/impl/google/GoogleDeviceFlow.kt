package ru.kyamshanov.notepen.library.impl.google

import kotlinx.coroutines.delay
import ru.kyamshanov.notepen.sync.cloud.domain.DeviceAuthorization
import ru.kyamshanov.notepen.sync.cloud.domain.DeviceTokenResult

private const val SLOW_DOWN_BACKOFF_MS = 5_000L
private const val MILLIS_PER_SECOND = 1_000L

/**
 * Runs Google's full OAuth device flow for [scope] to completion: requests a device code, surfaces
 * it to the caller via [onCode] (so the UI can show the user code + verification URL), then polls at
 * the server-advised cadence — honouring `slow_down` back-off — until the user authorizes or the
 * flow fails.
 *
 * The loop has no client-side timeout: the device code expires server-side, after which [poll]
 * returns a [DeviceTokenResult.Failed] that ends the loop — so no wall-clock is needed here.
 *
 * @return the [DeviceTokenResult.Authorized] (with its refresh token) on success, or a failure if
 *   the user denied access, the code expired, or a network error occurred.
 */
public suspend fun runGoogleDeviceFlow(
    authenticator: GoogleDeviceAuthenticator,
    scope: String,
    onCode: (DeviceAuthorization) -> Unit,
): Result<DeviceTokenResult.Authorized> =
    runCatching {
        val authorization = authenticator.requestAuthorization(scope)
        onCode(authorization)
        var intervalMs = authorization.intervalSeconds.coerceAtLeast(1) * MILLIS_PER_SECOND
        var result: DeviceTokenResult = DeviceTokenResult.Pending
        while (result !is DeviceTokenResult.Authorized) {
            delay(intervalMs)
            result = authenticator.poll(authorization.deviceCode)
            when (result) {
                is DeviceTokenResult.Authorized -> Unit
                DeviceTokenResult.Pending -> Unit
                DeviceTokenResult.SlowDown -> intervalMs += SLOW_DOWN_BACKOFF_MS
                is DeviceTokenResult.Failed -> error("Google sign-in failed: ${result.reason}")
            }
        }
        result
    }
