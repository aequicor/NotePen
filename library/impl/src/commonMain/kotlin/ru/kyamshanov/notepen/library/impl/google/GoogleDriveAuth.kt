package ru.kyamshanov.notepen.library.impl.google

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.kyamshanov.notepen.sync.cloud.domain.DeviceAuthorization
import ru.kyamshanov.notepen.sync.cloud.domain.DeviceTokenResult

private const val DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
private const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
private const val REFRESH_GRANT_TYPE = "refresh_token"

private val googleAuthJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class GoogleDeviceCodeDto(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    // Google sends `verification_url`; the spec field is `verification_uri`. Accept either.
    @SerialName("verification_url") val verificationUrl: String? = null,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = DEFAULT_POLL_INTERVAL_SECONDS,
) {
    companion object {
        const val DEFAULT_POLL_INTERVAL_SECONDS = 5
    }
}

@Serializable
internal data class GoogleTokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Int? = null,
    val error: String? = null,
)

/** Pure mapping of Google's device-code response JSON to the shared domain type. */
internal fun parseGoogleDeviceCode(body: String): DeviceAuthorization =
    googleAuthJson.decodeFromString<GoogleDeviceCodeDto>(body).let {
        DeviceAuthorization(
            deviceCode = it.deviceCode,
            userCode = it.userCode,
            verificationUri = it.verificationUrl ?: it.verificationUri ?: "https://www.google.com/device",
            intervalSeconds = it.interval,
            expiresInSeconds = it.expiresIn,
        )
    }

/** Pure mapping of Google's token-poll response JSON to a [DeviceTokenResult]. */
internal fun parseGoogleTokenResult(body: String): DeviceTokenResult {
    val dto = googleAuthJson.decodeFromString<GoogleTokenDto>(body)
    dto.accessToken?.let {
        return DeviceTokenResult.Authorized(
            accessToken = it,
            refreshToken = dto.refreshToken,
            expiresInSeconds = dto.expiresIn,
        )
    }
    return when (dto.error) {
        "authorization_pending" -> DeviceTokenResult.Pending
        "slow_down" -> DeviceTokenResult.SlowDown
        null -> DeviceTokenResult.Failed("malformed token response")
        else -> DeviceTokenResult.Failed(dto.error)
    }
}

/** A freshly minted access token plus its lifetime, from a refresh-token exchange. */
public data class RefreshedAccessToken(
    public val accessToken: String,
    public val expiresInSeconds: Int?,
)

/** Pure mapping of Google's refresh-token response JSON to [RefreshedAccessToken]. */
internal fun parseGoogleRefreshResult(body: String): RefreshedAccessToken {
    val dto = googleAuthJson.decodeFromString<GoogleTokenDto>(body)
    val token = dto.accessToken ?: error("Token refresh failed: ${dto.error ?: "no access_token in response"}")
    return RefreshedAccessToken(accessToken = token, expiresInSeconds = dto.expiresIn)
}

/**
 * Drives Google's OAuth 2.0 device flow (the "limited-input device" flow) for an installed-app
 * client. Unlike GitHub's device flow, Google requires the [GoogleOAuthConfig.clientSecret] at the
 * token-exchange step (treated as non-confidential for installed clients).
 *
 * Response mapping is the pure [parseGoogleDeviceCode] / [parseGoogleTokenResult] /
 * [parseGoogleRefreshResult]; this class only performs the HTTP calls. The polling loop (delay +
 * cadence) belongs to the caller, mirroring `GitHubDeviceAuthenticator`.
 *
 * @param httpClient injected engine (CIO in the app).
 * @param config the OAuth client credentials.
 */
public class GoogleDeviceAuthenticator(
    private val httpClient: HttpClient,
    private val config: GoogleOAuthConfig,
) {
    /** Requests a device + user code authorizing [scope] (e.g. [GoogleOAuthConfig.SCOPE_DRIVE_READONLY]). */
    public suspend fun requestAuthorization(scope: String): DeviceAuthorization {
        val body =
            httpClient
                .post(DEVICE_CODE_URL) {
                    header(HttpHeaders.Accept, "application/json")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", config.clientId)
                                append("scope", scope)
                            },
                        ),
                    )
                }.bodyAsText()
        return parseGoogleDeviceCode(body)
    }

    /** Performs one token poll for [deviceCode]; the caller loops with delay until non-[DeviceTokenResult.Pending]. */
    public suspend fun poll(deviceCode: String): DeviceTokenResult {
        val body =
            httpClient
                .post(TOKEN_URL) {
                    header(HttpHeaders.Accept, "application/json")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", config.clientId)
                                append("client_secret", config.clientSecret)
                                append("device_code", deviceCode)
                                append("grant_type", DEVICE_GRANT_TYPE)
                            },
                        ),
                    )
                }.bodyAsText()
        return parseGoogleTokenResult(body)
    }

    /** Exchanges a [refreshToken] for a fresh access token. */
    public suspend fun refresh(refreshToken: String): RefreshedAccessToken {
        val body =
            httpClient
                .post(TOKEN_URL) {
                    header(HttpHeaders.Accept, "application/json")
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", config.clientId)
                                append("client_secret", config.clientSecret)
                                append("refresh_token", refreshToken)
                                append("grant_type", REFRESH_GRANT_TYPE)
                            },
                        ),
                    )
                }.bodyAsText()
        return parseGoogleRefreshResult(body)
    }
}

/** Supplies a currently-valid bearer access token, refreshing it transparently when needed. */
public interface AccessTokenSource {
    /**
     * Returns a usable bearer token. Pass [forceRefresh] = `true` after a `401` to mint a new one
     * (the cached token expired); otherwise a cached token is reused.
     */
    public suspend fun bearer(forceRefresh: Boolean = false): String
}

/**
 * [AccessTokenSource] that mints access tokens from a stored [refreshToken] via [authenticator].
 *
 * It caches the last access token and only refreshes on first use or when [bearer] is called with
 * `forceRefresh = true` (the store does this after a `401`). Expiry-time-based proactive refresh is
 * deliberately omitted to avoid a wall-clock dependency in this KMP module; the 401-retry path
 * covers token expiry.
 */
public class RefreshingAccessTokenSource(
    private val refreshToken: String,
    private val authenticator: GoogleDeviceAuthenticator,
) : AccessTokenSource {
    private val mutex = Mutex()
    private var cached: String? = null

    override suspend fun bearer(forceRefresh: Boolean): String =
        mutex.withLock {
            val current = cached
            if (!forceRefresh && current != null) {
                current
            } else {
                authenticator.refresh(refreshToken).accessToken.also { cached = it }
            }
        }
}
