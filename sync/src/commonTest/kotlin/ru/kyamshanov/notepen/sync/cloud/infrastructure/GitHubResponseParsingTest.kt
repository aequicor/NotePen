package ru.kyamshanov.notepen.sync.cloud.infrastructure

import ru.kyamshanov.notepen.sync.cloud.domain.DeviceTokenResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubResponseParsingTest {
    @Test
    fun parsesDeviceCodeResponseIgnoringUnknownFields() {
        val json =
            """
            {"device_code":"dc","user_code":"WDJB-MJHT",
             "verification_uri":"https://github.com/login/device",
             "expires_in":900,"interval":5,"verification_uri_complete":"ignored"}
            """.trimIndent()
        val auth = parseDeviceCode(json)
        assertEquals("dc", auth.deviceCode)
        assertEquals("WDJB-MJHT", auth.userCode)
        assertEquals("https://github.com/login/device", auth.verificationUri)
        assertEquals(5, auth.intervalSeconds)
        assertEquals(900, auth.expiresInSeconds)
    }

    @Test
    fun parsesAuthorizedToken() {
        val result = parseTokenResult("""{"access_token":"gho_abc","token_type":"bearer","scope":"repo"}""")
        assertEquals(DeviceTokenResult.Authorized("gho_abc"), result)
    }

    @Test
    fun mapsPendingError() {
        assertEquals(DeviceTokenResult.Pending, parseTokenResult("""{"error":"authorization_pending"}"""))
    }

    @Test
    fun mapsSlowDownError() {
        assertEquals(DeviceTokenResult.SlowDown, parseTokenResult("""{"error":"slow_down","interval":10}"""))
    }

    @Test
    fun mapsTerminalErrorsToFailed() {
        assertTrue(parseTokenResult("""{"error":"expired_token"}""") is DeviceTokenResult.Failed)
        assertTrue(parseTokenResult("""{"error":"access_denied"}""") is DeviceTokenResult.Failed)
        assertTrue(parseTokenResult("""{"error":"device_flow_disabled"}""") is DeviceTokenResult.Failed)
    }

    @Test
    fun parsesDirectoryListingFilesOnly() {
        val json =
            """
            [{"name":"layer-a.json","path":"book/layer-a.json","sha":"s1","size":12,"type":"file"},
             {"name":"sub","path":"book/sub","sha":"s2","size":0,"type":"dir"}]
            """.trimIndent()
        val files = parseDirectoryListing(json)
        assertEquals(1, files.size)
        assertEquals("book/layer-a.json", files[0].path)
        assertEquals("s1", files[0].sha)
        assertEquals(12L, files[0].sizeBytes)
    }

    @Test
    fun parsesPutResponseRevision() {
        val json =
            """{"content":{"name":"layer-a.json","path":"book/layer-a.json","sha":"newsha","size":34,"type":"file"},
                "commit":{"sha":"abc"}}"""
        val file = parsePutResponse(json)
        assertEquals("book/layer-a.json", file.path)
        assertEquals("newsha", file.sha)
        assertEquals(34L, file.sizeBytes)
    }
}
