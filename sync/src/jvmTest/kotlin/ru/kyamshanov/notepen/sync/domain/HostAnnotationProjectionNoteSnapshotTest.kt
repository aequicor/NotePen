package ru.kyamshanov.notepen.sync.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationBundle
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationLayer
import ru.kyamshanov.notepen.annotation.domain.model.AnnotationViewState
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPath
import ru.kyamshanov.notepen.annotation.domain.model.DrawingPoint
import ru.kyamshanov.notepen.annotation.domain.model.EraserSettings
import ru.kyamshanov.notepen.annotation.domain.model.MarkerSettings
import ru.kyamshanov.notepen.annotation.domain.model.NormalizedRect
import ru.kyamshanov.notepen.annotation.domain.model.PageExtent
import ru.kyamshanov.notepen.annotation.domain.model.PageNote
import ru.kyamshanov.notepen.annotation.domain.model.PenSettings
import ru.kyamshanov.notepen.annotation.domain.model.StickyHighlight
import ru.kyamshanov.notepen.annotation.domain.port.AnnotationRepository
import ru.kyamshanov.notepen.mainscreen.domain.model.Folder
import ru.kyamshanov.notepen.mainscreen.domain.port.FolderRepository
import ru.kyamshanov.notepen.sync.domain.model.BookId
import ru.kyamshanov.notepen.sync.domain.model.LibraryBook
import ru.kyamshanov.notepen.sync.domain.model.LibraryManifest
import ru.kyamshanov.notepen.sync.domain.port.LibraryManifestProvider
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GAP 3 (initial sync snapshot): the host must surface existing notes — alongside
 * strokes — when a peer joins mid-session. Verifies that
 * [HostAnnotationProjection.noteSnapshotDtos] emits one [StrokeDelta.NoteUpserted]
 * per disk-loaded note (mirroring [HostAnnotationProjection.snapshotDtos] for
 * strokes) without disturbing the stroke snapshot.
 */
class HostAnnotationProjectionNoteSnapshotTest {
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val docId = "doc-1"
    private val hostUri = "/library/doc-1.pdf"

    private val note =
        PageNote(
            noteId = "note-1",
            rects = listOf(NormalizedRect(left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.4f)),
            pageIndex = 2,
            quote = "hello",
            context = "ctx",
            body = "the note body",
            colorArgb = 0xFF112233L,
        )

    private val stroke =
        DrawingPath(
            points = listOf(DrawingPoint(x = 0.1f, y = 0.1f, isNewPath = true)),
            strokeId = "stroke-1",
        )

    @AfterTest
    fun teardown() {
        scope.coroutineContext[Job]?.cancel()
    }

    @Test
    fun noteSnapshotDtos_emitsOneUpsertedPerDiskNote() =
        runBlocking {
            val provider = providerServing(docId, hostUri)
            // Populate the per-peer uri map so resolveUri(docId) is non-null.
            provider.buildSnapshotFor("peer-1")
            val repository =
                FakeAnnotationRepository(
                    AnnotationBundle(
                        pages = mapOf(0 to listOf(stroke)),
                        notes = mapOf(2 to listOf(note)),
                    ),
                )
            val registry = SyncEngineRegistry(deviceId = "host", scope = scope)
            val projection = HostAnnotationProjection(registry, provider, repository)

            val notes = projection.noteSnapshotDtos(docId)
            assertTrue(notes != null && notes.size == 1, "exactly one note upsert expected")
            val upserted = notes.single()
            assertEquals("note-1", upserted.strokeId)
            assertEquals(2, upserted.pageIndex)
            assertEquals(0L, upserted.clock)
            assertEquals(AnnotationLayer.HOST, upserted.authorDeviceId)
            val roundTripped = upserted.note.toDomain()
            assertEquals(note.rects, roundTripped.rects)
            assertEquals(note.body, roundTripped.body)
            assertEquals(note.colorArgb, roundTripped.colorArgb)

            // Stroke snapshot must still work (no regression).
            val strokes = projection.snapshotDtos(docId)
            assertTrue(strokes != null && strokes.size == 1, "exactly one stroke expected")
            assertEquals("stroke-1", strokes.single().strokeId)
        }

    private fun providerServing(
        documentId: String,
        uri: String,
    ): RemoteCatalogProvider =
        RemoteCatalogProvider(
            hostName = "host",
            manifestProvider = SingleBookManifestProvider(documentId, uri),
            folderRepository = NoteSnapshotEmptyFolderRepository,
        )
}

/** Serves one book whose [BookId.value] is [documentId] and resolves to [uri]. */
private class SingleBookManifestProvider(
    private val documentId: String,
    private val uri: String,
) : LibraryManifestProvider {
    override suspend fun current(): LibraryManifest =
        LibraryManifest(
            books =
                listOf(
                    LibraryBook(
                        id = BookId(documentId),
                        relativePath = "doc-1.pdf",
                        displayName = "Doc 1.pdf",
                        fileSize = 1L,
                        modifiedAt = 0L,
                    ),
                ),
        )

    override suspend fun resolveAbsolutePath(id: BookId): String? = if (id.value == documentId) uri else null
}

/** Returns a fixed [AnnotationBundle] from [load]; other ops are no-ops. */
private class FakeAnnotationRepository(
    private val bundle: AnnotationBundle,
) : AnnotationRepository {
    override suspend fun save(
        pdfPath: String,
        annotations: Map<Int, List<DrawingPath>>,
        scale: Int,
        pen: PenSettings,
        marker: MarkerSettings,
        eraser: EraserSettings,
        currentPage: Int,
        currentPageOffset: Int,
        favoritePageIndices: Set<Int>,
        pageExtents: Map<Int, PageExtent>,
        highlights: Map<Int, List<StickyHighlight>>,
        notes: Map<Int, List<PageNote>>,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun load(pdfPath: String): Result<AnnotationBundle> = Result.success(bundle)

    override suspend fun loadViewState(pdfPath: String): Result<AnnotationViewState?> = Result.success(null)

    override suspend fun saveViewState(
        pdfPath: String,
        viewState: AnnotationViewState,
    ): Result<Unit> = Result.success(Unit)
}

private object NoteSnapshotEmptyFolderRepository : FolderRepository {
    override suspend fun getAll(): List<Folder> = emptyList()

    override suspend fun getFilesInFolder(folderId: String): List<String> = emptyList()

    override suspend fun create(
        name: String,
        parentId: String?,
    ): Folder = error("unused")

    override suspend fun rename(
        id: String,
        newName: String,
    ) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun addFile(
        folderId: String,
        uri: String,
    ) = Unit

    override suspend fun removeFile(
        folderId: String,
        uri: String,
    ) = Unit
}
