---
genre: how-to
title: "Stage 06: Infrastructure Desktop (common jvmMain)"
topic: main-screen
module: common
stage: 06
feature: main_screen_redesign
---

# Stage 06: Инфраструктура Desktop (`:common` `jvmMain`)

**Модуль:** `:app:byCompose:common`  
**Sourceset:** `jvmMain`  
**Статус:** TODO  
**Зависит от:** Stage 01, Stage 05 (паттерны аналогичны)

---

## Цель

Реализовать все порты для Desktop (JVM)-платформы. Desktop-специфика: многооконность, двухуровневая блокировка для атомарности, `FileLock` с обязательным `withContext(IO)`.

**ACT-NOW R4:** все операции с `FileLock` — исключительно внутри `withContext(Dispatchers.IO)`.

---

## Шаг 0: Базовый путь хранилища

```kotlin
// Desktop: AppData / UserData directory
fun getAppDataDir(): java.io.File {
    val base = System.getProperty("user.home")
    return java.io.File(base, ".notepen").also { it.mkdirs() }
}
```

---

## Шаг 1: `FileHistoryRepositoryDesktop`

Двухуровневая блокировка (CC-10):
1. `Mutex` — исключает параллельный доступ из потоков одного JVM-процесса.
2. `FileLock` — исключает доступ из разных JVM-процессов (разные окна Desktop).

```kotlin
class FileHistoryRepositoryDesktop(
    private val dataDir: java.io.File = getAppDataDir(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FileHistoryRepository {

    private val historyFile get() = java.io.File(dataDir, "history.json")
    private val lockFile get() = java.io.File(dataDir, "history.lock")
    private val inProcessMutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun getAll(): List<RecentFile> = withContext(Dispatchers.IO) {
        try {
            historyFile.takeIf { it.exists() }?.let {
                json.decodeFromString<List<RecentFile>>(it.readText()).sortedByDescending { r -> r.openedAt }
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun upsert(file: RecentFile, lastPageIndex: Int) {
        withLockedIO {
            val current = readUnsafe()
            val (newList, _) = FileHistoryManager.applyUpsert(current, file.copy(lastPageIndex = lastPageIndex))
            writeAtomicUnsafe(newList)
        }
    }

    override suspend fun updateStatus(id: String, status: AvailabilityStatus) {
        withLockedIO {
            val updated = readUnsafe().map { if (it.id == id) it.copy(availabilityStatus = status) else it }
            writeAtomicUnsafe(updated)
        }
    }

    override suspend fun updateLastPage(uri: String, pageIndex: Int) {
        withLockedIO {
            val updated = readUnsafe().map { if (it.uri == uri) it.copy(lastPageIndex = pageIndex) else it }
            writeAtomicUnsafe(updated)
        }
    }

    override suspend fun rollbackUpsert(uri: String) {
        withLockedIO {
            writeAtomicUnsafe(readUnsafe().filter { it.uri != uri })
        }
    }

    private suspend fun <T> withLockedIO(block: () -> T): T =
        inProcessMutex.withLock {
            withContext(Dispatchers.IO) {
                // ACT-NOW R4: FileLock только внутри withContext(IO)
                java.io.RandomAccessFile(lockFile, "rw").use { raf ->
                    raf.channel.use { channel ->
                        val lock = channel.lock()   // блокирующий — ОК внутри IO
                        try { block() } finally { lock.release() }
                    }
                }
            }
        }

    private fun readUnsafe(): List<RecentFile> = try {
        historyFile.takeIf { it.exists() }?.let {
            json.decodeFromString<List<RecentFile>>(it.readText())
        } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun writeAtomicUnsafe(list: List<RecentFile>) {
        val tmp = java.io.File(dataDir, "history.tmp.json")
        tmp.writeText(json.encodeToString(list))
        tmp.renameTo(historyFile)
    }
}
```

---

## Шаг 2: `FolderRepositoryDesktop`

Аналогичная двухуровневая блокировка:

```kotlin
class FolderRepositoryDesktop(
    private val dataDir: java.io.File = getAppDataDir(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FolderRepository {

    private val foldersFile get() = java.io.File(dataDir, "folders.json")
    private val linksFile get() = java.io.File(dataDir, "folder_links.json")
    private val lockFile get() = java.io.File(dataDir, "folders.lock")
    private val inProcessMutex = kotlinx.coroutines.sync.Mutex()

    // Реализация аналогична FolderRepositoryAndroid, но с withLockedIO вместо mutex.withLock
    // withLockedIO: Mutex + FileLock (см. FileHistoryRepositoryDesktop)
    // Логика валидации, чтения, записи — идентична Android-версии
}
```

---

## Шаг 3: `FileAvailabilityCheckerDesktop`

```kotlin
class FileAvailabilityCheckerDesktop : FileAvailabilityChecker {

    override suspend fun check(uri: String): AvailabilityStatus = withContext(Dispatchers.IO) { checkSync(uri) }

    override fun checkSync(uri: String): AvailabilityStatus {
        val file = try { java.io.File(uri).canonicalFile } catch (_: Exception) { return AvailabilityStatus.FILE_ERROR }
        return when {
            !file.exists() -> AvailabilityStatus.NOT_FOUND
            !file.canRead() -> AvailabilityStatus.FILE_ERROR
            else -> AvailabilityStatus.AVAILABLE
        }
    }
}
```

---

## Шаг 4: `PdfThumbnailGeneratorDesktop`

Использует существующий Apache PdfBox:

```kotlin
class PdfThumbnailGeneratorDesktop : PdfThumbnailGenerator {

    override suspend fun generate(uri: String, widthPx: Int, heightPx: Int): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                org.apache.pdfbox.pdmodel.PDDocument.load(java.io.File(uri)).use { doc ->
                    if (doc.numberOfPages == 0) throw ThumbnailGenerationException("Empty PDF")
                    val renderer = org.apache.pdfbox.rendering.PDFRenderer(doc)
                    val scale = widthPx.toFloat() / doc.pages[0].mediaBox.width
                    val image: java.awt.image.BufferedImage = renderer.renderImage(0, scale)
                    val stream = java.io.ByteArrayOutputStream()
                    javax.imageio.ImageIO.write(image, "PNG", stream)
                    stream.toByteArray()
                }
            }.mapFailure { ThumbnailGenerationException("Thumbnail generation failed", it) }
        }
}
```

---

## Шаг 5: `ThumbnailRepositoryDesktop`

Аналогична Android-версии, но использует `getAppDataDir()` вместо `cacheDir`:

```kotlin
class ThumbnailRepositoryDesktop(
    private val dataDir: java.io.File = java.io.File(getAppDataDir(), "thumbnails").also { it.mkdirs() },
    private val maxCacheSizeBytes: Long = 50 * 1024 * 1024L,
) : ThumbnailRepository {
    // Полная реализация аналогична ThumbnailRepositoryAndroid
    // Ключевое отличие: путь хранилища из getAppDataDir()
}
```

---

## Шаг 6: `FilePicker` Desktop actual

```kotlin
// common/src/jvmMain/kotlin/ru/kyamshanov/notepen/mainscreen/platform/FilePicker.desktop.kt
actual class FilePicker {
    actual suspend fun pickPdfFile(): String? = withContext(Dispatchers.IO) {
        val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Открыть PDF", java.awt.FileDialog.LOAD)
        dialog.filenameFilter = java.io.FilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory ?: return@withContext null
        val file = dialog.file ?: return@withContext null
        java.io.File(dir, file).canonicalPath
    }
}
```

---

## Шаг 7: UriNormalizer JVM actual

```kotlin
// shared/src/jvmMain/.../UriNormalizer.jvm.kt
actual fun normalizeUnicode(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
```

---

## Шаг 8: `rememberPdfThumbnailPainter` Desktop actual

```kotlin
// common/src/jvmMain/.../ThumbnailPainter.desktop.kt
@Composable
actual fun rememberPdfThumbnailPainter(imageData: ByteArray): Painter {
    val bitmap = remember(imageData) {
        javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(imageData))
            .toComposeImageBitmap()
    }
    return BitmapPainter(bitmap)
}
```

---

## CC-15: Desktop sync (BL-4)

```kotlin
// Callback, который MainScreenComponent вызывает когда окно редактора закрыто:
fun onEditorClosed() {
    // Дожидаемся завершения flush, затем вызываем ScreenVisible
    viewModel.onIntent(MainScreenIntent.ScreenVisible)
}
```

---

## Тесты Stage 06

Юнит-тесты на JVM (commonTest + jvmTest):

| Тест | TC |
|------|----|
| `FileHistoryRepositoryDesktop.upsert` + `getAll()` | TC-02 |
| `FileHistoryRepositoryDesktop` — 21 запись → вытеснение | TC-19 |
| `FileAvailabilityCheckerDesktop` — несуществующий файл → NOT_FOUND | TC-28 |
| `PdfThumbnailGeneratorDesktop` — пустой PDF (0 страниц) → Result.failure | TC-44 (CC-4) |
| `ThumbnailRepositoryDesktop` — кеш-инвалидация при изменённом mtime | TC-47 (AC-22) |

---

## Acceptance Criteria, закрываемые этим этапом

AC-3 (Desktop path normalization), AC-9a (Desktop availability check), AC-17 (Desktop trigger), CC-10 (Desktop atomicity), CC-15 (Desktop sync), CC-16 (known limitation — документировать в spec).

---

## Контрольные точки Stage 06

- [ ] `./gradlew :app:byCompose:common:compileKotlin` — зелёный (включая jvmMain)
- [ ] `./gradlew :app:byCompose:common:test` (jvm тесты) — пройдены
- [ ] `./gradlew detekt ktlintCheck` — без ошибок
- [ ] **ACT-NOW R4:** grep по `channel.lock()` — проверить наличие `withContext(Dispatchers.IO)` во всех вхождениях
