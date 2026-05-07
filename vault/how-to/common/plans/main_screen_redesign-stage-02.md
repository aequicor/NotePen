---
genre: how-to
title: "Stage 02: Use cases (shared)"
topic: main-screen
module: shared
stage: 02
feature: main_screen_redesign
---

# Stage 02: Use cases (`:shared`)

**Модуль:** `:shared`  
**Sourceset:** `commonMain`  
**Статус:** TODO  
**Зависит от:** Stage 01 (порты, модели)

---

## Цель

Реализовать три use case и алгоритм добавления/вытеснения истории в доменном агрегате `FileHistoryManager`. Use case-ы изолированы от инфраструктуры и тестируются через fake-реализации портов.

---

## Шаг 1: `FileHistoryManager` — агрегат вытеснения

Файл: `shared/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/domain/model/FileHistoryManager.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.model

/**
 * Чистая логика добавления/вытеснения истории (без I/O).
 * Алгоритм: см. спецификацию FileHistory → «Алгоритм добавления записи».
 */
object FileHistoryManager {

    const val MAX_SIZE = 20

    /**
     * Применяет upsert-логику: возвращает новый список + вытесненный URI (если есть).
     */
    fun applyUpsert(
        entries: List<RecentFile>,
        newFile: RecentFile,
    ): Pair<List<RecentFile>, String?> {  // (newList, evictedUri)
        val mutable = entries.toMutableList()

        // Шаг 2: найти существующую запись по нормализованному URI
        val existingIndex = mutable.indexOfFirst { it.uri == newFile.uri }
        if (existingIndex >= 0) {
            val updated = mutable[existingIndex].copy(openedAt = newFile.openedAt)
            mutable.removeAt(existingIndex)
            mutable.add(0, updated)
            return Pair(mutable, null)
        }

        // Шаг 4: если место есть — просто добавить
        if (mutable.size < MAX_SIZE) {
            mutable.add(0, newFile)
            return Pair(mutable, null)
        }

        // Шаг 5: место нет — вытеснение (AC-9e, CC-8)
        val evictIndex = findEvictIndex(mutable)
        val evictedUri = mutable[evictIndex].uri
        mutable.removeAt(evictIndex)
        mutable.add(0, newFile)
        return Pair(mutable, evictedUri)
    }

    /** Возвращает индекс записи для вытеснения согласно приоритету AC-9e + CC-8. */
    fun findEvictIndex(entries: List<RecentFile>): Int {
        // Шаг 5a: самая старая NOT_FOUND или FILE_ERROR
        entries.indexOfLast { it.availabilityStatus == AvailabilityStatus.NOT_FOUND
                || it.availabilityStatus == AvailabilityStatus.FILE_ERROR }
            .takeIf { it >= 0 }?.let { return it }

        // Шаг 5b: UNKNOWN трактуется как NOT_FOUND (pessimistic, CC-8)
        entries.indexOfLast { it.availabilityStatus == AvailabilityStatus.UNKNOWN }
            .takeIf { it >= 0 }?.let { return it }

        // Шаг 5c: обычный LRU — самая старая AVAILABLE запись
        return entries.indices.maxByOrNull { -entries[it].openedAt }
            ?: entries.lastIndex
    }
}
```

---

## Шаг 2: `AddToHistoryUseCase`

Файл: `shared/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/domain/usecase/AddToHistoryUseCase.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.usecase

import ru.kyamshanov.notepen.mainscreen.domain.model.*
import ru.kyamshanov.notepen.mainscreen.domain.port.FileHistoryRepository

sealed class AddHistoryResult {
    data class Added(val record: RecentFile) : AddHistoryResult()
    data class Moved(val record: RecentFile) : AddHistoryResult()
    data class SafFuzzyMatchDetected(
        val existing: RecentFile,
        val newUri: String,
    ) : AddHistoryResult()
}

class AddToHistoryUseCase(
    private val repository: FileHistoryRepository,
) {
    suspend fun execute(
        uri: String,
        displayName: String,
        fileSize: Long?,
        openedAt: Long,
        lastPageIndex: Int = 0,
    ): Result<AddHistoryResult> = runCatching {
        val normalizedUri = UriNormalizer.normalize(uri)

        val existing = repository.getAll()

        // Android SAF fuzzy-match (AC-5b, CC-1, CC-2)
        if (normalizedUri.startsWith("content://")) {
            val candidate = existing.firstOrNull { rec ->
                rec.uri != normalizedUri &&
                    rec.displayName == displayName &&
                    fileSize != null && rec.fileSize == fileSize
            }
            if (candidate != null) {
                return@runCatching AddHistoryResult.SafFuzzyMatchDetected(candidate, normalizedUri)
            }
        }

        val existingRecord = existing.firstOrNull { it.uri == normalizedUri }
        val newRecord = RecentFile(
            id = existingRecord?.id ?: generateUuid(),
            uri = normalizedUri,
            displayName = displayName,
            fileSize = fileSize,
            openedAt = openedAt,
            availabilityStatus = AvailabilityStatus.AVAILABLE,
            lastPageIndex = lastPageIndex,
        )

        repository.upsert(newRecord, lastPageIndex)

        if (existingRecord != null) AddHistoryResult.Moved(newRecord)
        else AddHistoryResult.Added(newRecord)
    }
}
```

> `generateUuid()` — expect/actual: `kotlin.uuid.Uuid.random().toString()` (Kotlin 2.0+) или `java.util.UUID.randomUUID().toString()`.

---

## Шаг 3: `CheckAvailabilityUseCase`

Файл: `shared/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/domain/usecase/CheckAvailabilityUseCase.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.usecase

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.kyamshanov.notepen.mainscreen.domain.model.*
import ru.kyamshanov.notepen.mainscreen.domain.port.*

data class AvailabilityUpdate(val id: String, val status: AvailabilityStatus)

class CheckAvailabilityUseCase(
    private val checker: FileAvailabilityChecker,
    private val repository: FileHistoryRepository,
) {
    /**
     * Параллельно проверяет доступность всех записей.
     * Semaphore(5) — максимум 5 параллельных проверок (AC-9a, spec).
     * Таймаут на одну проверку: 2 секунды.
     */
    fun execute(files: List<RecentFile>): Flow<AvailabilityUpdate> = channelFlow {
        val semaphore = kotlinx.coroutines.sync.Semaphore(5)
        files.forEach { file ->
            launch {
                semaphore.withPermit {
                    val status = try {
                        withTimeout(2_000) { checker.check(file.uri) }
                    } catch (_: TimeoutCancellationException) {
                        AvailabilityStatus.FILE_ERROR
                    } catch (_: Exception) {
                        AvailabilityStatus.FILE_ERROR
                    }
                    repository.updateStatus(file.id, status)
                    send(AvailabilityUpdate(file.id, status))
                }
            }
        }
    }
}
```

---

## Шаг 4: `OpenRecentFileUseCase`

Файл: `shared/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/domain/usecase/OpenRecentFileUseCase.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kyamshanov.notepen.mainscreen.domain.model.AvailabilityStatus
import ru.kyamshanov.notepen.mainscreen.domain.port.FileAvailabilityChecker

sealed class OpenFileResult {
    data class Success(val uri: String) : OpenFileResult()
    data class NotAvailable(val status: AvailabilityStatus) : OpenFileResult()
}

class OpenRecentFileUseCase(
    private val checker: FileAvailabilityChecker,
) {
    suspend fun execute(uri: String): OpenFileResult {
        val status = withContext(Dispatchers.IO) { checker.checkSync(uri) }
        return when (status) {
            AvailabilityStatus.AVAILABLE -> OpenFileResult.Success(uri)
            else -> OpenFileResult.NotAvailable(status)
        }
    }
}
```

---

## Шаг 5: Утилита `generateUuid`

Файл: `shared/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/domain/model/UuidGenerator.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.model

/** expect/actual для генерации UUID v4. */
expect fun generateUuid(): String
```

`shared/src/jvmMain/.../UuidGenerator.jvm.kt`:
```kotlin
actual fun generateUuid(): String = java.util.UUID.randomUUID().toString()
```

`shared/src/androidMain/.../UuidGenerator.android.kt`:
```kotlin
actual fun generateUuid(): String = java.util.UUID.randomUUID().toString()
```

---

## Тесты Stage 02 (Critical CC)

Файл: `shared/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/domain/usecase/`

| Тест | CC / AC |
|------|---------|
| `FileHistoryManager.applyUpsert` — существующий URI перемещается в начало | AC-4 |
| `FileHistoryManager.applyUpsert` — список не полный → добавить без вытеснения | AC-2 |
| `FileHistoryManager.findEvictIndex` — NOT_FOUND вытесняется вместо AVAILABLE | AC-9e |
| `FileHistoryManager.findEvictIndex` — UNKNOWN трактуется как NOT_FOUND (pessimistic) | **CC-8 Critical** |
| `FileHistoryManager.findEvictIndex` — все AVAILABLE → вытесняется самая старая по openedAt | AC-5 |
| `FileHistoryManager.findEvictIndex` — только что добавленный файл не вытесняется при CC-9 | **CC-9 High** |
| `AddToHistoryUseCase` — SAF fuzzy-match: displayName+size совпадают, URI разные → SafFuzzyMatchDetected | **CC-1 Critical**, **CC-2 Critical** |
| `AddToHistoryUseCase` — content:// URI не проходит fuzzy-match при size=null | AC-5b |
| `CheckAvailabilityUseCase` — Semaphore(5) ограничивает параллелизм | AC-9a |
| `CheckAvailabilityUseCase` — timeout 2s → FILE_ERROR (не зависание) | AC-9a |
| `OpenRecentFileUseCase` — AVAILABLE → Success | CC-19 High |
| `OpenRecentFileUseCase` — NOT_FOUND → NotAvailable | CC-19 |

---

## Acceptance Criteria, закрываемые этим этапом

AC-2, AC-4, AC-5, AC-5b, AC-9a, AC-9b, AC-9e, AC-18 (upsert), AC-23, AC-54 (eviction link), CC-1, CC-2, CC-8, CC-9, CC-19.

---

## Контрольные точки Stage 02

- [ ] `./gradlew :shared:compileKotlin` — зелёный
- [ ] `./gradlew :shared:test` — все тесты Stage 02 пройдены
- [ ] `./gradlew detekt ktlintCheck` — без ошибок
