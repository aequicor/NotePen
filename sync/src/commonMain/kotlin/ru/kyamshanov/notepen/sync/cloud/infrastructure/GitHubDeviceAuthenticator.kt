package ru.kyamshanov.notepen.sync.cloud.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.cloud.domain.DeviceAuthorization
import ru.kyamshanov.notepen.sync.cloud.domain.DeviceTokenResult

private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

private val githubAuthJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class DeviceCodeDto(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int,
)

@Serializable
internal data class TokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    val error: String? = null,
)

/** Pure mapping of GitHub's device-code response JSON to the domain type. */
internal fun parseDeviceCode(body: String): DeviceAuthorization =
    githubAuthJson.decodeFromString<DeviceCodeDto>(body).let {
        DeviceAuthorization(
            deviceCode = it.deviceCode,
            userCode = it.userCode,
            verificationUri = it.verificationUri,
            intervalSeconds = it.interval,
            expiresInSeconds = it.expiresIn,
        )
    }

/** Pure mapping of GitHub's token-poll response JSON to a [DeviceTokenResult]. */
internal fun parseTokenResult(body: String): DeviceTokenResult {
    val dto = githubAuthJson.decodeFromString<TokenDto>(body)
    dto.accessToken?.let { return DeviceTokenResult.Authorized(it) }
    return when (dto.error) {
        "authorization_pending" -> DeviceTokenResult.Pending
        "slow_down" -> DeviceTokenResult.SlowDown
        null -> DeviceTokenResult.Failed("malformed token response")
        else -> DeviceTokenResult.Failed(dto.error)
    }
}

/**
 * Drives GitHub's OAuth device flow for a **public** client (no client secret —
 * GitHub's device flow does not use one). Response mapping is the pure
 * [parseDeviceCode]/[parseTokenResult]; this class only performs the two HTTP
 * calls. The polling loop (delay + cadence) belongs to the caller, so this stays
 * main-safe and free of timing logic.
 *
 * @param httpClient injected engine (CIO in the app).
 * @param clientId the OAuth App's public client id.
 */
class GitHubDeviceAuthenticator(
    private val httpClient: HttpClient,
    private val clientId: String,
) {
    /** Requests a device + user code for [scope] (e.g. `"repo"`). */
    suspend fun requestAuthorization(scope: String): DeviceAuthorization {
        val body =
            httpClient
                .post(DEVICE_CODE_URL) {
                    header(HttpHeaders.Accept, "application/json")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", clientId)
                                append("scope", scope)
                            },
                        ),
                    )
                }.bodyAsText()
        return parseDeviceCode(body)
    }

    /** Performs one token poll for [deviceCode]; the caller loops with delay. */
    suspend fun poll(deviceCode: String): DeviceTokenResult {
        val body =
            httpClient
                .post(ACCESS_TOKEN_URL) {
                    header(HttpHeaders.Accept, "application/json")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", clientId)
                                append("device_code", deviceCode)
                                append("grant_type", DEVICE_GRANT_TYPE)
                            },
                        ),
                    )
                }.bodyAsText()
        return parseTokenResult(body)
    }
}
