package ru.kyamshanov.notepen.sync.infrastructure

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import ru.kyamshanov.notepen.sync.domain.model.NetworkMessage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileTransferReceiverTest {
    private val destDir: File = Files.createTempDirectory("notepen-rx").toFile()

    @AfterTest
    fun cleanup() {
        destDir.deleteRecursively()
        // The traversal tests deliberately try to escape destDir; make sure the
        // sibling "outside" probe directory is also cleaned regardless of outcome.
        outsideProbe().deleteRecursively()
    }

    /** Sibling of [destDir] a `../` traversal would land in if the fix regressed. */
    private fun outsideProbe(): File = File(destDir.parentFile, "${destDir.name}-OUTSIDE")

    private fun start(
        fileName: String,
        payload: ByteArray,
        documentId: String = "",
        totalSize: Long = payload.size.toLong(),
        totalChunks: Int = 1,
    ) = NetworkMessage.FileTransferStart(
        transferId = "t-1",
        fileName = fileName,
        totalChunks = totalChunks,
        totalSize = totalSize,
        sha256 = "",
        documentId = documentId,
    )

    private fun chunk(
        payload: ByteArray,
        chunkIndex: Int = 0,
        totalChunks: Int = 1,
    ) = NetworkMessage.FileChunk(
        transferId = "t-1",
        fileName = "ignored",
        chunkIndex = chunkIndex,
        totalChunks = totalChunks,
        dataBase64 = encodeBase64(payload),
    )

    private fun flow(vararg msgs: NetworkMessage): Flow<NetworkMessage> = flowOf(*msgs)

    @Test
    fun pathTraversalFileName_writesInsideDestDirOnly() =
        runTest {
            val payload = "EVIL-PAYLOAD".encodeToByteArray()
            val received =
                FileTransferReceiver(
                    incoming = flow(start(fileName = "../../evil", payload = payload), chunk(payload)),
                    destDir = destDir.absolutePath,
                ).awaitFile()

            // The written file must be a direct child of destDir, not an escape.
            val written = File(received.destPath)
            assertTrue(
                written.canonicalPath.startsWith(destDir.canonicalPath + File.separator),
                "wrote outside destDir: ${written.canonicalPath}",
            )
            assertEquals(destDir.canonicalFile, written.canonicalFile.parentFile)
            assertEquals(payload.toList(), written.readBytes().toList())

            // Nothing escaped into a parent-relative location.
            assertTrue(!File(destDir.parentFile, "evil").exists())
            assertTrue(!File(destDir.parentFile.parentFile, "evil").exists())
        }

    @Test
    fun oversizedTotalSize_rejectedBeforeAllocation() =
        runTest {
            // Long.MAX_VALUE would overflow ByteArray(totalSize.toInt()) to a
            // negative size (NegativeArraySizeException) if it ever reached
            // allocation — the header validation must reject it first. No chunk is
            // sent, so a throw can only come from header validation.
            val ex =
                assertFailsWith<IllegalArgumentException> {
                    FileTransferReceiver(
                        incoming = flow(start(fileName = "big.pdf", payload = ByteArray(0), totalSize = Long.MAX_VALUE)),
                        destDir = destDir.absolutePath,
                    ).awaitFile()
                }
            assertTrue(ex.message?.contains("totalSize") == true, "unexpected message: ${ex.message}")
            // Nothing was written.
            assertTrue(destDir.listFiles().orEmpty().isEmpty())
        }

    @Test
    fun negativeTotalSize_rejectedBeforeAllocation() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                FileTransferReceiver(
                    incoming = flow(start(fileName = "x.pdf", payload = ByteArray(0), totalSize = -1L)),
                    destDir = destDir.absolutePath,
                ).awaitFile()
            }
        }

    @Test
    fun chunkIndexOutOfRange_rejected() =
        runTest {
            val payload = "data".encodeToByteArray()
            assertFailsWith<IllegalArgumentException> {
                FileTransferReceiver(
                    incoming =
                        flow(
                            start(fileName = "x.pdf", payload = payload, totalChunks = 1),
                            chunk(payload, chunkIndex = 5, totalChunks = 1),
                        ),
                    destDir = destDir.absolutePath,
                ).awaitFile()
            }
        }

    @Test
    fun chunkBytesExceedingDeclaredTotal_rejected() =
        runTest {
            val payload = "0123456789".encodeToByteArray()
            assertFailsWith<IllegalArgumentException> {
                FileTransferReceiver(
                    // Declares only 2 bytes but the single chunk carries 10.
                    incoming = flow(start(fileName = "x.pdf", payload = payload, totalSize = 2L), chunk(payload)),
                    destDir = destDir.absolutePath,
                ).awaitFile()
            }
        }

    @Test
    fun benignTransfer_writesExpectedBytes() =
        runTest {
            val payload = "hello world".encodeToByteArray()
            val received =
                FileTransferReceiver(
                    incoming = flow(start(fileName = "doc.pdf", payload = payload), chunk(payload)),
                    destDir = destDir.absolutePath,
                ).awaitFile()

            assertEquals("doc.pdf", File(received.destPath).name)
            assertEquals(payload.toList(), File(received.destPath).readBytes().toList())
        }
}
