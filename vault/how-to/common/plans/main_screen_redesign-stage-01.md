---
genre: how-to
title: "Stage 01: Domain models + port interfaces (shared)"
topic: main-screen
module: shared
stage: 01
feature: main_screen_redesign
---

# Stage 01: Доменные модели, исключения и порты (`:shared`)

**Модуль:** `:shared`  
**Sourceset:** `commonMain`  
**Статус:** TODO  

---

## Цель

Определить всю доменную модель и порты (интерфейсы репозиториев/сервисов) для главного экрана. Реализации портов пишутся в Stage 05–06. Никакой бизнес-логики здесь нет — только типы и контракты.

---

## Шаг 0: Добавить зависимости

В `gradle/libs.versions.toml` добавить:
```toml
[versions]
kotlinx-serialization = "1.7.3"   # уже есть

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.9.0" }
```

В `shared/build.gradle.kts` в `commonMain.dependencies` добавить:
```kotlin
implementation(libs.kotlinx.serialization.json)
```

В `app/byCompose/common/build.gradle.kts` в `commonMain.dependencies` добавить:
```kotlin
implementation(libs.lifecycle.coroutines)    // уже есть в versions
implementation(libs.kotlinx.serialization.json)
implementation(compose.material3)
```

---

## Шаг 1: Пакетная структура

Создать пакет `ru.kyamshanov.notepen.mainscreen` в `shared/src/commonMain/kotlin/`:

```
mainscreen/
  domain/
    model/
      RecentFile.kt
      AvailabilityStatus.kt
      Folder.kt
      FolderFileLink.kt
    exception/
      Exceptions.kt
    port/
      FileHistoryRepository.kt
      ThumbnailRepository.kt
      PdfThumbnailGenerator.kt
      FileAvailabilityChecker.kt
      FolderRepository.kt
    usecase/   ← пустая папка, заполняется в Stage 02
```

---

## Шаг 2: Доменные модели

### `RecentFile.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RecentFile(
    val id: String,
    val uri: String,
    val displayName: String,
    val fileSize: Long? = null,
    val openedAt: Long,           // epochMillis (Instant не сериализуем без адаптера)
    val availabilityStatus: AvailabilityStatus = AvailabilityStatus.UNKNOWN,
    val thumbnailKey: String? = null,
    val fileMtime: Long? = null,  // epochMillis
    val lastPageIndex: Int = 0,
)
```

> Примечание: используем `Long` (epochMillis) вместо `kotlinx.datetime.Instant` для минимальной зависимости. `Instant` можно добавить в Stage 07 при необходимости.

### `AvailabilityStatus.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AvailabilityStatus {
    UNKNOWN,
    AVAILABLE,
    NOT_FOUND,
    FILE_ERROR,
    ARCHIVED_UNAVAILABLE,
}
```

### `Folder.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,   // epochMillis
)
```

### `FolderFileLink.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FolderFileLink(
    val folderId: String,
    val fileUri: String,
    val lastOpenedAt: Long,   // epochMillis
)
```

---

## Шаг 3: Исключения

### `Exceptions.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.exception

class HistoryFlushException(message: String, cause: Throwable? = null) : Exception(message, cause)

class FolderLimitExceededException : Exception("Folder limit (100) exceeded")
class FolderNameInvalidException(name: String) : Exception("Folder name invalid: '$name'")
class FolderNameTooLongException(length: Int) : Exception("Folder name too long: $length chars")
class FolderNameCharsInvalidException(name: String) : Exception("Folder name contains invalid chars: '$name'")
class FolderNotFoundException(id: String) : Exception("Folder not found: $id")
class FileNotInHistoryException(uri: String) : Exception("File not in history: $uri")
class FileDuplicateInFolderException(folderId: String, uri: String) : Exception("File $uri already in folder $folderId")

class ThumbnailGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

---

## Шаг 4: Порты

### `FileHistoryRepository.kt`

Скопировать интерфейс из спецификации дословно:
- `getAll(): List<RecentFile>`
- `upsert(file: RecentFile, lastPageIndex: Int = 0)`
- `updateStatus(id: String, status: AvailabilityStatus)`
- `updateLastPage(uri: String, pageIndex: Int)`
- `rollbackUpsert(uri: String)`

### `ThumbnailRepository.kt`

- `get(uri: String, currentFileMtime: Long?): ByteArray?`
- `put(uri: String, imageData: ByteArray, fileMtime: Long?)`
- `totalSizeBytes(): Long`

### `PdfThumbnailGenerator.kt`

- `generate(uri: String, widthPx: Int, heightPx: Int): Result<ByteArray>`

### `FileAvailabilityChecker.kt`

- `suspend fun check(uri: String): AvailabilityStatus`
- `fun checkSync(uri: String): AvailabilityStatus`  
  (Документировать: вызывать только с `Dispatchers.IO`)

### `FolderRepository.kt`

Скопировать интерфейс из спецификации дословно:
- `create(name: String): Folder`
- `delete(id: String)`
- `addFile(folderId: String, uri: String)`
- `removeFile(folderId: String, uri: String)`
- `rename(id: String, newName: String)`
- `getAll(): List<Folder>`
- `getFilesInFolder(folderId: String): List<String>`

---

## Шаг 5: Вспомогательный объект — нормализация URI

### `UriNormalizer.kt`

```kotlin
package ru.kyamshanov.notepen.mainscreen.domain.model

object UriNormalizer {
    /**
     * Desktop: canonical path, trailing slash removed, NFC-normalized.
     * Android: content:// URI as-is (no normalization).
     */
    fun normalize(uri: String): String =
        if (uri.startsWith("content://")) uri
        else uri.trimEnd('/', '\\').let { normalizeUnicode(it) }

    private fun normalizeUnicode(s: String): String = s  // expect/actual для NFC в Stage 05/06
}
```

> `normalizeUnicode` станет `expect fun` в Stage 05/06 (NFC-нормализация через `java.text.Normalizer`).

---

## Тесты Stage 01

Файл: `shared/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/domain/`

| Тест | TC |
|------|----|
| `RecentFile` сериализация/десериализация JSON | — |
| `AvailabilityStatus` значения enum соответствуют ожидаемым | — |
| `UriNormalizer.normalize` для Desktop-пути с trailing slash | TC-01 |
| `UriNormalizer.normalize` для Android content:// — возвращает без изменений | TC-01 |
| Все исключения конструируются без NPE | — |

---

## Acceptance Criteria, закрываемые этим этапом

AC-2 (RecentFile.uri уникален), AC-9a (AvailabilityStatus.UNKNOWN), AC-22 (fileMtime), AC-23 (openedAt), AC-32 (Folder.name валидация-контракт), AC-33 (Folder.id UUID), AC-34 (255 chars limit), AC-41 (100 folders limit), AC-57/58 (lastPageIndex поле).

---

## Контрольные точки Stage 01

- [ ] `./gradlew :shared:compileKotlin` — зелёный
- [ ] `./gradlew :shared:test` — все тесты Stage 01 пройдены
- [ ] `./gradlew detekt ktlintCheck` — без ошибок
