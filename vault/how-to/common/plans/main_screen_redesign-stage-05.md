---
genre: how-to
title: "Stage 05: Infrastructure Android (common androidMain)"
topic: main-screen
module: common
stage: 05
feature: main_screen_redesign
---

# Stage 05: Инфраструктура Android (`:common` `androidMain`)

**Модуль:** `:app:byCompose:common`  
**Sourceset:** `androidMain`  
**Статус:** TODO  
**Зависит от:** Stage 01 (порты)

---

## Цель

Реализовать все порты для Android-платформы. Никакой бизнес-логики — только I/O и платформенные API.

---

## Шаг 0: Зависимости Android

В `app/byCompose/common/build.gradle.kts` в `androidMain.dependencies`:

```kotlin
implementation(libs.kotlinx.serialization.json)
// PdfRenderer — встроен в Android API >= 21 (minSdk=24 → безопасно)
```

---

## Шаг 1: `FileHistoryRepositoryAndroid`

Стратегия: JSON-файл в `context.filesDir/history.json`. Атомарная запись через tmp-файл + rename.

```kotlin
class FileHistoryRepositoryAndroid(
    private val context: android.content.Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FileHistoryRepository {

    private val historyFile get() = java.io.File(context.filesDir, "history.json")
    private val mutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun getAll(): List<RecentFile> = withContext(Dispatchers.IO) {
        try {
            val text = historyFile.takeIf { it.exists() }?.readText() ?: return@withContext emptyList()
            json.decodeFromString<List<RecentFile>>(text)
                .sortedByDescending { it.openedAt }
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun upsert(file: RecentFile, lastPageIndex: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = getAll().toMutableList()
            val (newList, _) = FileHistoryManager.applyUpsert(
                current,
                file.copy(lastPageIndex = lastPageIndex),
            )
            writeAtomic(newList)
        }
    }

    override suspend fun updateStatus(id: String, status: AvailabilityStatus) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = getAll().map { if (it.id == id) it.copy(availabilityStatus = status) else it }
            writeAtomic(updated)
        }
    }

    override suspend fun updateLastPage(uri: String, pageIndex: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = getAll().map { if (it.uri == uri) it.copy(lastPageIndex = pageIndex) else it }
            writeAtomic(updated)
        }
    }

    override suspend fun rollbackUpsert(uri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = getAll().filter { it.uri != uri }
            writeAtomic(current)
        }
    }

    private fun writeAtomic(list: List<RecentFile>) {
        val tmp = java.io.File(context.filesDir, "history.tmp.json")
        tmp.writeText(json.encodeToString(list))
        tmp.renameTo(historyFile)
    }
}
```

---

## Шаг 2: `FolderRepositoryAndroid`

Стратегия: JSON-файл `context.filesDir/folders.json` + `folder_links.json`.

```kotlin
class FolderRepositoryAndroid(
    private val context: android.content.Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FolderRepository {

    private val foldersFile get() = java.io.File(context.filesDir, "folders.json")
    private val linksFile get() = java.io.File(context.filesDir, "folder_links.json")
    private val mutex = kotlinx.coroutines.sync.Mutex()

    private fun readFolders(): List<Folder> = try {
        foldersFile.takeIf { it.exists() }?.let { json.decodeFromString<List<Folder>>(it.readText()) } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun readLinks(): List<FolderFileLink> = try {
        linksFile.takeIf { it.exists() }?.let { json.decodeFromString<List<FolderFileLink>>(it.readText()) } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun writeFolders(folders: List<Folder>) {
        val tmp = java.io.File(context.filesDir, "folders.tmp.json")
        tmp.writeText(json.encodeToString(folders))
        tmp.renameTo(foldersFile)
    }

    private fun writeLinks(links: List<FolderFileLink>) {
        val tmp = java.io.File(context.filesDir, "folder_links.tmp.json")
        tmp.writeText(json.encodeToString(links))
        tmp.renameTo(linksFile)
    }

    override suspend fun create(name: String): Folder = withContext(Dispatchers.IO) {
        mutex.withLock {
            validateName(name)
            val folders = readFolders()
            if (folders.size >= 100) throw FolderLimitExceededException()
            val folder = Folder(id = generateUuid(), name = name.trim(), createdAt = System.currentTimeMillis())
            writeFolders(folders + folder)
            folder
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeFolders(readFolders().filter { it.id != id })
            writeLinks(readLinks().filter { it.folderId != id })
        }
    }

    override suspend fun addFile(folderId: String, uri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val folders = readFolders()
            if (folders.none { it.id == folderId }) throw FolderNotFoundException(folderId)
            val links = readLinks()
            if (links.any { it.folderId == folderId && it.fileUri == uri }) throw FileDuplicateInFolderException(folderId, uri)
            writeLinks(links + FolderFileLink(folderId, uri, System.currentTimeMillis()))
        }
    }

    override suspend fun removeFile(folderId: String, uri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            writeLinks(readLinks().filter { !(it.folderId == folderId && it.fileUri == uri) })
        }
    }

    override suspend fun rename(id: String, newName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            validateName(newName)
            val folders = readFolders()
            if (folders.none { it.id == id }) throw FolderNotFoundException(id)
            writeFolders(folders.map { if (it.id == id) it.copy(name = newName.trim()) else it })
        }
    }

    override suspend fun getAll(): List<Folder> = withContext(Dispatchers.IO) {
        val folders = readFolders()
        val links = readLinks()
        folders.sortedByDescending { folder ->
            links.filter { it.folderId == folder.id }.maxOfOrNull { it.lastOpenedAt } ?: Long.MIN_VALUE
        }
    }

    override suspend fun getFilesInFolder(folderId: String): List<String> = withContext(Dispatchers.IO) {
        if (readFolders().none { it.id == folderId }) throw FolderNotFoundException(folderId)
        readLinks().filter { it.folderId == folderId }.map { it.fileUri }
    }

    private fun validateName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw FolderNameInvalidException(name)
        if (trimmed.length > 255) throw FolderNameTooLongException(trimmed.length)
        if (!trimmed.matches(Regex("[\\p{L}\\p{N}\\-_]+"))) throw FolderNameCharsInvalidException(trimmed)
    }
}
```

---

## Шаг 3: `FileAvailabilityCheckerAndroid`

```kotlin
class FileAvailabilityCheckerAndroid(
    private val context: android.content.Context,
) : FileAvailabilityChecker {

    override suspend fun check(uri: String): AvailabilityStatus = withContext(Dispatchers.IO) {
        checkSync(uri)
    }

    override fun checkSync(uri: String): AvailabilityStatus = try {
        context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
            AvailabilityStatus.AVAILABLE
        } ?: AvailabilityStatus.FILE_ERROR
    } catch (_: SecurityException) {
        AvailabilityStatus.FILE_ERROR    // CC-12: SAF разрешение истекло
    } catch (_: java.io.FileNotFoundException) {
        AvailabilityStatus.NOT_FOUND
    } catch (_: Exception) {
        AvailabilityStatus.FILE_ERROR
    }
}
```

---

## Шаг 4: `PdfThumbnailGeneratorAndroid`

Использует Android `PdfRenderer` (API 21+, minSdk=24 → безопасно).

```kotlin
class PdfThumbnailGeneratorAndroid(
    private val context: android.content.Context,
) : PdfThumbnailGenerator {

    override suspend fun generate(uri: String, widthPx: Int, heightPx: Int): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = context.contentResolver.openFileDescriptor(android.net.Uri.parse(uri), "r")
                    ?: throw ThumbnailGenerationException("Cannot open file descriptor")
                descriptor.use { fd ->
                    android.graphics.pdf.PdfRenderer(fd).use { renderer ->
                        if (renderer.pageCount == 0) throw ThumbnailGenerationException("Empty PDF")
                        renderer.openPage(0).use { page ->
                            val bitmap = android.graphics.Bitmap.createBitmap(widthPx, heightPx, android.graphics.Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val stream = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 85, stream)
                            stream.toByteArray()
                        }
                    }
                }
            }.mapFailure { cause ->
                // CC-11: OOM и прочие ошибки → ThumbnailGenerationException
                ThumbnailGenerationException("Thumbnail generation failed", cause)
            }
        }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    exceptionOrNull()?.let { Result.failure(transform(it)) } ?: this
```

---

## Шаг 5: `ThumbnailRepositoryAndroid`

Диск-кеш в `context.cacheDir/thumbnails/`. LRU-вытеснение при превышении 50 МБ.

```kotlin
class ThumbnailRepositoryAndroid(
    private val context: android.content.Context,
    private val maxCacheSizeBytes: Long = 50 * 1024 * 1024L,
) : ThumbnailRepository {

    private val cacheDir get() = java.io.File(context.cacheDir, "thumbnails").also { it.mkdirs() }
    private val mutex = kotlinx.coroutines.sync.Mutex()

    private fun keyToFile(uri: String): java.io.File =
        java.io.File(cacheDir, uri.hashCode().toString() + ".png")

    override suspend fun get(uri: String, currentFileMtime: Long?): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(3_000) {
                    val file = keyToFile(uri)
                    if (!file.exists()) return@withTimeout null
                    // инвалидация по mtime (AC-22)
                    val metaFile = java.io.File(cacheDir, uri.hashCode().toString() + ".meta")
                    if (currentFileMtime != null && metaFile.exists()) {
                        val storedMtime = metaFile.readText().toLongOrNull()
                        if (storedMtime != null && storedMtime != currentFileMtime) {
                            file.delete(); metaFile.delete()
                            return@withTimeout null
                        }
                    }
                    file.readBytes()
                }
            } catch (_: Exception) { null }
        }

    override suspend fun put(uri: String, imageData: ByteArray, fileMtime: Long?) =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(3_000) {
                    mutex.withLock {
                        keyToFile(uri).writeBytes(imageData)
                        fileMtime?.let { java.io.File(cacheDir, uri.hashCode().toString() + ".meta").writeText(it.toString()) }
                        evictIfNeeded()
                    }
                }
            } catch (e: Exception) {
                io.github.oshai.kotlinlogging.KotlinLogging.logger {}.warn { "Thumbnail cache write failed: ${e::class.simpleName}" }
            }
        }

    override suspend fun totalSizeBytes(): Long = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun evictIfNeeded() {
        val files = cacheDir.listFiles()?.filter { it.extension == "png" }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        var i = 0
        while (total > maxCacheSizeBytes && i < files.size) {
            val f = files[i++]
            total -= f.length()
            f.delete()
            java.io.File(f.path.replace(".png", ".meta")).delete()
        }
    }
}
```

---

## Шаг 6: FilePicker Android actual

```kotlin
// common/src/androidMain/kotlin/ru/kyamshanov/notepen/mainscreen/platform/FilePicker.android.kt
actual class FilePicker(private val launcher: ActivityResultLauncher<Array<String>>) {
    private val resultChannel = kotlinx.coroutines.channels.Channel<String?>(1)

    fun onResult(uri: android.net.Uri?) {
        resultChannel.trySend(uri?.toString())
    }

    actual suspend fun pickPdfFile(): String? {
        launcher.launch(arrayOf("application/pdf"))
        return resultChannel.receive()
    }
}
```

> Детали интеграции с `rememberLauncherForActivityResult` в `MainContent.android.kt` — Stage 07.

---

## UriNormalizer actual (Android)

```kotlin
// shared/src/androidMain/.../UriNormalizer.android.kt
actual fun normalizeUnicode(s: String): String = s  // content:// не нормализуется; Desktop path NFC в jvmMain
```

---

## Тесты Stage 05

Тесты инфраструктурного уровня выполняются на Android-устройстве/эмуляторе (instrumented):

| Тест | TC |
|------|----|
| `FileHistoryRepositoryAndroid.upsert` → `getAll()` возвращает запись | TC-02 |
| `FileHistoryRepositoryAndroid.upsert` с 21 записями → oldest NOT_FOUND вытесняется | TC-19 |
| `FileHistoryRepositoryAndroid.updateLastPage` → `getAll()` обновляет lastPageIndex | TC-91 |
| `FileAvailabilityCheckerAndroid` — SecurityException → FILE_ERROR | TC-36 (CC-12) |
| `ThumbnailRepositoryAndroid` — превышение 50 МБ → вытеснение | TC-46 |

---

## Acceptance Criteria, закрываемые этим этапом

AC-2, AC-5, AC-9c, AC-9d, AC-9e, AC-18, AC-22, AC-57 (infrastructure), CC-11, CC-12.

---

## Контрольные точки Stage 05

- [ ] `./gradlew :app:byCompose:common:compileKotlin` — зелёный (включая androidMain)
- [ ] Instrumented tests на эмуляторе — пройдены
- [ ] `./gradlew detekt ktlintCheck` — без ошибок
