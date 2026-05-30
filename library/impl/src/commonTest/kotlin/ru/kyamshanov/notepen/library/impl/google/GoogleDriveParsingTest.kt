package ru.kyamshanov.notepen.library.impl.google

import ru.kyamshanov.notepen.library.api.LibraryRole
import ru.kyamshanov.notepen.sync.cloud.domain.DeviceTokenResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleDriveParsingTest {
    @Test
    fun parseDeviceCode_mapsVerificationUrl() {
        val body =
            """
            {"device_code":"DC","user_code":"WXYZ-1234","verification_url":"https://www.google.com/device",
             "expires_in":1800,"interval":5}
            """.trimIndent()
        val auth = parseGoogleDeviceCode(body)
        assertEquals("DC", auth.deviceCode)
        assertEquals("WXYZ-1234", auth.userCode)
        assertEquals("https://www.google.com/device", auth.verificationUri)
        assertEquals(5, auth.intervalSeconds)
        assertEquals(1800, auth.expiresInSeconds)
    }

    @Test
    fun parseTokenResult_authorizedCarriesRefreshTokenAndExpiry() {
        val body = """{"access_token":"AT","refresh_token":"RT","expires_in":3599,"token_type":"Bearer"}"""
        val result = parseGoogleTokenResult(body)
        assertTrue(result is DeviceTokenResult.Authorized)
        assertEquals("AT", result.accessToken)
        assertEquals("RT", result.refreshToken)
        assertEquals(3599, result.expiresInSeconds)
    }

    @Test
    fun parseTokenResult_mapsPendingAndSlowDownAndError() {
        assertTrue(parseGoogleTokenResult("""{"error":"authorization_pending"}""") is DeviceTokenResult.Pending)
        assertTrue(parseGoogleTokenResult("""{"error":"slow_down"}""") is DeviceTokenResult.SlowDown)
        val failed = parseGoogleTokenResult("""{"error":"access_denied"}""")
        assertTrue(failed is DeviceTokenResult.Failed)
        assertEquals("access_denied", failed.reason)
    }

    @Test
    fun parseRefreshResult_extractsAccessToken() {
        val refreshed = parseGoogleRefreshResult("""{"access_token":"NEW","expires_in":3599}""")
        assertEquals("NEW", refreshed.accessToken)
        assertEquals(3599, refreshed.expiresInSeconds)
    }

    @Test
    fun parseDriveFileList_keepsIdNameSizeVersion() {
        val body =
            """
            {"files":[
              {"id":"abc","name":"Book.pdf","size":"1024","version":"7","md5Checksum":"deadbeef"},
              {"id":"def","name":"Other.pdf"}
            ]}
            """.trimIndent()
        val files = parseDriveFileList(body)
        assertEquals(listOf("abc", "def"), files.map { it.id })
        assertEquals("Book.pdf", files.first().name)
        assertEquals(1024L, files.first().sizeBytes)
        assertEquals("7", files.first().version, "version is the change-detection token")
    }

    @Test
    fun buildRelatedUploadBody_wrapsMetadataAndMediaWithBoundary() {
        val media = byteArrayOf(1, 2, 3)
        val body = buildRelatedUploadBody("BOUND", """{"name":"x"}""", media)
        val text = body.decodeToString()
        assertTrue(text.contains("--BOUND"), "opens with the boundary")
        assertTrue(text.contains("""{"name":"x"}"""), "embeds the metadata JSON")
        assertTrue(text.trimEnd().endsWith("--BOUND--"), "closes with the terminating boundary")
    }

    @Test
    fun roleForScope_readonlyIsReader_writeIsLibrarian() {
        assertEquals(LibraryRole.Reader, GoogleOAuthConfig.roleForScope(GoogleOAuthConfig.SCOPE_DRIVE_READONLY))
        assertEquals(LibraryRole.Reader, GoogleOAuthConfig.roleForScope(null))
        assertEquals(LibraryRole.Librarian, GoogleOAuthConfig.roleForScope(GoogleOAuthConfig.SCOPE_DRIVE_FILE))
    }
}
