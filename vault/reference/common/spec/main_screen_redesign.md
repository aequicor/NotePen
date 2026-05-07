---
genre: reference
title: "Spec: Main Screen Redesign"
topic: main-screen
module: common
triggers:
  - "главный экран спецификация"
  - "main screen spec"
  - "recent files spec"
  - "история файлов"
  - "RecentFile"
  - "FileHistory"
  - "ThumbnailCache"
  - "папки"
  - "Folder"
  - "FolderRepository"
  - "FolderFileLink"
confidence: high
source: ai
updated: 2026-05-07T16:00:00Z
---

# Spec: Main Screen Redesign

**Module:** common (`:app:byCompose:common`) + shared (`:shared`)
**Status:** Draft
**Date:** 07.05.2026
**Author:** @SystemAnalyst
**Requirements:** `[[concepts/common/requirements/main_screen_redesign]]`
**Corner Cases:** `[[concepts/common/plans/main_screen_redesign-corner-cases]]`
**Test Cases:** `[[reference/common/test-cases/main_screen_redesign-test-cases]]`

---

## Overview

Переработка главного экрана NotePen добавляет стартовый экран с историей недавно открытых PDF-файлов и единой кнопкой открытия нового документа через нативный файловый диалог платформы.

Экран заменяет текущую точку входа (прямой запуск редактора) и обеспечивает быстрый доступ к повторно открываемым документам. Работает на Android (SAF) и Desktop (Windows/macOS/Linux) в рамках Compose Multiplatform.

Ключевые ограничения:
- История хранит максимум **20 записей**, вытеснение по LRU (самая старая по `openedAt`).
- Кеш миниатюр ограничен **50 МБ** (LRU по времени последнего обращения к миниатюре).
- Адаптивная вёрстка: одна колонка при ширине < 600 dp, сетка при ≥ 600 dp.
- Desktop: несколько окон приложения разделяют одну историю; операции вытеснения атомарны.
- Android: первичный ключ записи — `content://` URI; fuzzy-match по `displayName` + `fileSize` при смене URI после реавторизации SAF.

---

## Data Models

### RecentFile

Доменная сущность, представляющая одну запись в истории недавних файлов.
Размещается в модуле `:shared`.

```
Поле              Тип                    Обяз.  Описание
----------------  ---------------------  -----  -------------------------------------------------------
id                String                 да     Уникальный идентификатор записи (UUID v4, генерируется при первом добавлении)
uri               String                 да     Первичный ключ доступа к файлу.
                                                Desktop: absolute canonical path (resolved symlinks,
                                                trailing slash removed, NFC-normalized).
                                                Android: content:// URI as-is.
displayName       String                 да     Отображаемое имя файла (имя файла без пути / SAF displayName).
fileSize          Long                   нет    Размер файла в байтах (вторичный атрибут fuzzy-match Android).
openedAt          Instant                да     Момент, когда пользователь инициировал открытие файла
                                                (нажал на элемент списка или подтвердил выбор в диалоге).
                                                Не момент завершения загрузки в редакторе (AC-23).
availabilityStatus AvailabilityStatus   да     Статус доступности файла (см. AvailabilityStatus).
thumbnailKey      String                 нет    Ключ для поиска в ThumbnailCache (null = миниатюра ещё не запрошена).
fileMtime         Instant                нет    Время модификации файла на диске на момент последней генерации миниатюры.
                                                Используется для инвалидации кеша (AC-22).
lastPageIndex     Int                    да     Индекс страницы (0-based), на которой пользователь находился при последнем
                                                открытии файла. Сохраняется при каждом upsert (AC-18).
                                                При первом открытии файла (нет записи в истории) — значение 0.
                                                При открытии файла из истории — редактор получает lastPageIndex и
                                                открывает PDF на сохранённой странице.
```

**Инварианты:**
- `uri` уникален в пределах истории (не может быть двух записей с одинаковым нормализованным `uri`).
- `openedAt` не изменяется после создания записи — только при перемещении записи в начало списка при повторном открытии (тогда `openedAt` обновляется до текущего момента).

### AvailabilityStatus

Перечисление, описывающее состояние доступности файла в истории.
Размещается в модуле `:shared`.

```
Значение               Описание
---------------------  -------------------------------------------------------
UNKNOWN                Статус ещё не проверен (начальное состояние после загрузки истории).
AVAILABLE              Файл доступен и открываем.
NOT_FOUND              Файл не найден на диске (удалён или перемещён).
FILE_ERROR             Файл найден, но повреждён, зашифрован или недоступен по иной причине
                       (SecurityException SAF, нулевой размер и т.д.).
ARCHIVED_UNAVAILABLE   Файл был вытеснен из истории (eviction LRU) и при попытке открытия
                       из папки оказался недоступен. Сочетает семантику «архивная ссылка»
                       (файл отсутствует в RecentFile-истории) и «недоступен» (checkSync вернул
                       NOT_FOUND или FILE_ERROR). Используется исключительно в контексте
                       FolderFileLink: при открытии файла из папки, когда файл уже не в истории,
                       но checkSync не прошёл. При успешной проверке (файл доступен) —
                       файл возвращается в историю (upsert) и статус меняется на AVAILABLE.
```

**Правило pessimistic-статус (CC-8):** при вытеснении записи с `availabilityStatus = UNKNOWN`
алгоритм трактует её как `NOT_FOUND` (pessimistic). Восстановление не требуется.

### FileHistory

Агрегат, управляющий коллекцией записей истории. Размещается в модуле `:shared`.
Не хранится напрямую — персистируется через `FileHistoryRepository`.

```
Поле              Тип                    Описание
----------------  ---------------------  -------------------------------------------------------
entries           List<RecentFile>       Записи, отсортированные по openedAt DESC (самая свежая — первая).
maxSize           Int                    Максимальное число записей (константа: 20).
```

**Алгоритм добавления записи (AC-2, AC-4, AC-5, AC-9e, AC-54):**

1. Нормализовать `uri` входящего файла (см. раздел «Нормализация URI»).
2. Найти существующую запись с тем же нормализованным `uri`.
3. Если найдена — переместить в начало (обновить `openedAt` до текущего момента). Завершить.
4. Если не найдена и `entries.size < maxSize` — добавить новую запись в начало. Завершить.
5. Если `entries.size == maxSize` (список полон):
   a. Найти самую старую запись с `availabilityStatus == NOT_FOUND` или `FILE_ERROR`.
   b. Если такой нет — найти все записи с `availabilityStatus == UNKNOWN`; трактовать их как `NOT_FOUND` (pessimistic, CC-8); взять самую старую.
   c. Если все записи `AVAILABLE` — вытеснить самую старую `AVAILABLE` запись (обычный LRU по AC-5).
   d. Удалить найденную запись, добавить новую в начало.

**Eviction «папочного» файла — молчаливый переход в ARCHIVED (AC-54):**
При вытеснении (шаг 5d) записи, на которую ссылается хотя бы один `FolderFileLink`:
- `FolderFileLink` **не удаляется** (поведение по умолчанию, см. раздел FolderFileLink → «Жизненный цикл»).
- Никакого уведомления пользователю не отображается — переход в архивное состояние **молчаливый**.
- Статус файла в контексте папки становится `ARCHIVED_UNAVAILABLE` только при следующей попытке открытия из папки, когда `checkSync` вернёт `NOT_FOUND` или `FILE_ERROR` (lazy evaluation).
- До момента первой попытки открытия файл в папке отображается без явного статуса недоступности (статус не обновляется проактивно при eviction).

**Алгоритм вытеснения при `NOT_FOUND`/`FILE_ERROR` (AC-9e):**
Доступные (`AVAILABLE`) записи не вытесняются, пока в списке есть хотя бы одна `NOT_FOUND`, `FILE_ERROR` или `UNKNOWN` запись.

### ThumbnailCacheEntry

Запись в кеше миниатюр. Размещается в модуле `common`.

```
Поле              Тип                    Обяз.  Описание
----------------  ---------------------  -----  -------------------------------------------------------
key               String                 да     Ключ = нормализованный URI файла.
imageData         ByteArray              да     Закодированные данные изображения миниатюры первой страницы.
sizeBytes         Long                   да     Размер imageData в байтах.
lastAccessedAt    Instant                да     Момент последнего обращения к записи (для LRU-вытеснения).
generatedAt       Instant                да     Момент генерации миниатюры.
fileMtimeAtGeneration Instant            нет    mtime файла на момент генерации (для инвалидации AC-22).
```

**Правило LRU:** при добавлении новой миниатюры, если суммарный `sizeBytes` превышает 50 МБ,
вытесняются записи с наименьшим `lastAccessedAt` до тех пор, пока сумма не войдёт в лимит.

**Правило инвалидации (AC-22):** при запросе миниатюры проверяется `fileMtimeAtGeneration` vs.
актуальный `mtime` файла. Если `mtime` файла новее — кеш инвалидируется, миниатюра перегенерируется.

---

## Data Models — Папки (Folders)

### Folder

Доменная сущность, представляющая пользовательскую папку для группировки файлов из истории.
Размещается в модуле `:shared`.

```
Поле              Тип                    Обяз.  Описание
----------------  ---------------------  -----  -------------------------------------------------------
id                String                 да     Уникальный идентификатор папки (UUID v4, генерируется при создании).
                                                Первичный ключ — UUID; дубликаты по имени допустимы.
name              String                 да     Отображаемое имя папки. Максимум 255 символов.
                                                Пустая строка и строка из только пробелов — недопустимы (AC-32, AC-35).
createdAt         Instant                да     Момент создания папки.
```

**Инварианты:**
- `id` уникален в пределах хранилища.
- `name` не может быть пустым или состоять только из пробельных символов.
- `name.length ≤ 255` (в символах Unicode; проверяется до персистирования).
- Дубликаты `name` **допустимы** — ключом является `id` (UUID), а не имя (AC-33).

**Лимит:** одновременно может существовать не более **100 папок** (AC-41).
При достижении лимита операция `create` завершается ошибкой `FolderLimitExceededException`; папка не создаётся.

### FolderFileLink

Связующая запись (join entity) реализующая отношение многие-ко-многим между `Folder` и `RecentFile`.
Размещается в модуле `:shared`.

```
Поле              Тип                    Обяз.  Описание
----------------  ---------------------  -----  -------------------------------------------------------
folderId          String                 да     UUID папки (FK → Folder.id).
fileUri           String                 да     Нормализованный URI файла (FK → RecentFile.uri).
lastOpenedAt      Instant                да     Момент последнего открытия файла из данной папки.
                                                Копируется из RecentFile.openedAt при добавлении файла
                                                в папку (FolderRepository.addFile). Обновляется при
                                                каждом открытии файла из папки (вызов OpenRecentFileUseCase
                                                из контекста папки — ViewModel обновляет поле через
                                                FolderRepository после успешного upsert). Используется
                                                для сортировки папок: max(FolderFileLink.lastOpenedAt)
                                                по всем файлам папки (BL-12).
```

**Инварианты:**
- Комбинация `(folderId, fileUri)` уникальна — один файл не может быть добавлен в одну папку дважды.
- `fileUri` ДОЛЖЕН соответствовать существующей записи `RecentFile`; ссылки на несуществующие записи не допускаются (AC-37).
- При удалении папки (`Folder`) каскадно удаляются все связанные `FolderFileLink` (AC-29, AC-30).
  `RecentFile`-записи **не удаляются** — операция затрагивает только ссылки.

**Жизненный цикл FolderFileLink при вытеснении RecentFile из истории:**
`FolderFileLink` **НЕ удаляется** при вытеснении `RecentFile` алгоритмом LRU (eviction).
Папка сохраняет «архивную» ссылку на вытесненный файл.
При открытии файла из папки выполняется синхронная проверка доступности (`FileAvailabilityChecker.checkSync`, аналогично CC-19).
Если файл доступен — открывается в редакторе и вызывается `FileHistoryRepository.upsert` (добавляется обратно в историю).
Если файл недоступен — применяется стандартный BL-1 (сообщение пользователю, ссылка остаётся в папке).

---

## API Contracts (Интерфейсы/Порты)

Спецификация описывает контракты (порты), а не реализации. Реализации размещаются в инфраструктурном слое соответствующего модуля.

### Port: FileHistoryRepository

Порт для персистирования и чтения истории файлов. Декларируется в `:shared`.
Реализуется отдельно для Android (DataStore / Room) и Desktop (SQLite / файл).
Все операции изменения данных **атомарны** — Desktop-реализация обеспечивает взаимное исключение при параллельном доступе из нескольких окон (CC-10).

```kotlin
interface FileHistoryRepository {

    /**
     * Возвращает все записи истории, отсортированные по openedAt DESC.
     * Никогда не бросает исключение — при ошибке возвращает пустой список.
     *
     * Таймаут: реализация ДОЛЖНА завершиться не позднее чем через 5 секунд.
     * При превышении таймаута или ошибке I/O возвращается пустой список;
     * `isLoading` в ViewModel сбрасывается в false, показывается пустое состояние.
     * Бесконечный `isLoading = true` невозможен: ViewModel сбрасывает его
     * в блоке `finally` сразу после завершения (успешного или по таймауту) вызова `getAll()`.
     */
    suspend fun getAll(): List<RecentFile>

    /**
     * Добавляет или обновляет запись.
     * Если запись с таким же нормализованным URI уже существует — перемещает её в начало
     * (обновляет openedAt). Иначе добавляет новую запись и применяет алгоритм вытеснения.
     * Операция атомарна.
     * @param file Доменная сущность RecentFile для сохранения.
     * @param lastPageIndex Индекс страницы (0-based), на которой пользователь находился
     *                      при открытии. При первом добавлении или если значение неизвестно — 0.
     *                      Сохраняется в поле RecentFile.lastPageIndex (AC-18).
     * @throws HistoryFlushException при ошибке персистирования.
     */
    suspend fun upsert(file: RecentFile, lastPageIndex: Int = 0)

    /**
     * Обновляет поле availabilityStatus для записи с указанным id.
     * Если запись не найдена — операция игнорируется (idempotent).
     *
     * I/O-ошибка при записи НЕ игнорируется молча: реализация ДОЛЖНА выбросить
     * `HistoryFlushException`. ViewModel перехватывает исключение, логирует категорию
     * ошибки (без URI/путей) и устанавливает `errorEvent = HistoryFlushFailed`.
     * Расхождение in-memory/on-disk тем самым становится явным, а не скрытым.
     */
    suspend fun updateStatus(id: String, status: AvailabilityStatus)

    /**
     * Обновляет поле lastPageIndex для записи с указанным нормализованным URI.
     * Используется при выходе из редактора (onPause/onStop на Android, window close на Desktop)
     * для сохранения текущей видимой страницы без изменения openedAt и порядка в истории (BL-14).
     *
     * Если запись с указанным URI не существует в истории — операция игнорируется (no-op).
     * @param uri Нормализованный URI файла.
     * @param pageIndex Текущий видимый индекс страницы (0-based).
     * @throws HistoryFlushException при ошибке I/O записи в хранилище.
     *
     * Логирование: URI не логируется — потенциально чувствительные данные.
     * При ошибке логируется только категория исключения.
     */
    suspend fun updateLastPage(uri: String, pageIndex: Int)

    /**
     * Откатывает последнюю операцию upsert для указанного URI.
     * Используется при отмене навигации (CC-6): если запись была добавлена,
     * она удаляется; если была перемещена — возвращается на прежнюю позицию.
     * Если flush ещё не был завершён — операция upsert отменяется без персистирования.
     * @param uri Нормализованный URI записи для отката.
     *
     * Логирование: реализация НЕ ДОЛЖНА логировать значение `uri` —
     * URI является потенциально чувствительными данными пользователя.
     * При ошибке логируется только категория исключения (например, "IOException").
     * Контракт: метод ДОЛЖЕН выбросить `HistoryFlushException` при ошибке I/O,
     * чтобы вызывающий код знал об неудаче (не «тихое» проглатывание ошибки).
     */
    suspend fun rollbackUpsert(uri: String)
}
```

**Ошибки:**

| Исключение | Условие |
|------------|---------|
| `HistoryFlushException` | Ошибка записи в хранилище при `upsert`. Редактор всё равно открывается (AC-18). |

### Port: ThumbnailRepository

Порт для хранения и получения миниатюр. Декларируется в `common`.

**Стратегия хранения:**
- Кеш хранится **on-disk** (файловая директория приватного хранилища приложения).
- Кеш **переживает перезапуск** приложения: при повторном запуске уже сгенерированные миниатюры доступны без повторной генерации.
- Таймаут I/O-операций: каждый вызов `get` и `put` ДОЛЖЕН завершиться не позднее чем через **3 секунды**. При превышении таймаута `get` возвращает `null` (кеш-промах), `put` логирует категорию ошибки и завершается без исключения (генерация не блокируется).

```kotlin
interface ThumbnailRepository {

    /**
     * Возвращает закешированные данные миниатюры или null, если кеш отсутствует
     * или инвалидирован по mtime.
     * @param uri Нормализованный URI файла.
     * @param currentFileMtime Актуальное mtime файла для проверки инвалидации.
     * Таймаут: 3 секунды; при превышении возвращает null.
     */
    suspend fun get(uri: String, currentFileMtime: Instant?): ByteArray?

    /**
     * Сохраняет миниатюру в кеш. Применяет LRU-вытеснение при превышении лимита 50 МБ.
     * Таймаут: 3 секунды; при превышении логирует категорию ошибки, не выбрасывает исключение.
     */
    suspend fun put(uri: String, imageData: ByteArray, fileMtime: Instant?)

    /**
     * Возвращает суммарный размер кеша в байтах.
     */
    suspend fun totalSizeBytes(): Long
}
```

### Port: PdfThumbnailGenerator

Порт для генерации миниатюры первой страницы PDF. Декларируется в `common`.

```kotlin
interface PdfThumbnailGenerator {

    /**
     * Генерирует миниатюру первой страницы PDF по указанному URI.
     * Выполняется в изолированном контексте — OOM и исключения рендеринга
     * перехватываются внутри и возвращаются как Result.failure (CC-11).
     * @return Result.success(ByteArray) или Result.failure(ThumbnailGenerationException).
     */
    suspend fun generate(uri: String, widthPx: Int, heightPx: Int): Result<ByteArray>
}
```

**Ошибки:**

| Исключение | Условие |
|------------|---------|
| `ThumbnailGenerationException` | PDF повреждён, зашифрован, нулевой размер, OOM при рендеринге. |

### Port: FileAvailabilityChecker

Порт для проверки доступности файла. Декларируется в `common`.

```kotlin
interface FileAvailabilityChecker {

    /**
     * Проверяет доступность файла по URI.
     * На Android: проверяет URI через ContentResolver.
     * На Desktop: проверяет существование и читаемость файла по canonical path.
     * @return AvailabilityStatus (никогда UNKNOWN).
     */
    suspend fun check(uri: String): AvailabilityStatus

    /**
     * Синхронная проверка непосредственно перед открытием файла (CC-19).
     *
     * CRITICAL — диспетчер: метод НЕ является `suspend fun` намеренно, но
     * ДОЛЖЕН вызываться исключительно с `Dispatchers.IO` (через `withContext`
     * в вызывающем UseCase). Вызов с UI-диспетчера запрещён и является
     * нарушением контракта (блокировка главного потока).
     * `OpenRecentFileUseCase` гарантирует переключение на `Dispatchers.IO`
     * перед вызовом `checkSync`.
     *
     * Таймаут: реализация ДОЛЖНА вернуть результат не позднее чем через
     * 2 секунды. При превышении — возвращает `FILE_ERROR`.
     */
    fun checkSync(uri: String): AvailabilityStatus
}
```

### Port: FolderRepository

Порт для персистирования и чтения папок и их связей с файлами. Декларируется в `:shared`.
Реализуется в инфраструктурном слое (Android: Room; Desktop: SQLite).

```kotlin
interface FolderRepository {

    /**
     * Создаёт новую папку с указанным именем.
     * @param name Имя папки. Не может быть пустым или состоять только из пробелов.
     *             Максимум 255 символов.
     * @return Созданная папка с присвоенным UUID и временной меткой createdAt.
     * @throws FolderLimitExceededException если уже существует 100 папок.
     * @throws FolderNameInvalidException если name пустое или состоит только из пробелов.
     * @throws FolderNameTooLongException если name.length > 255.
     */
    suspend fun create(name: String): Folder

    /**
     * Удаляет папку по идентификатору.
     * Каскадно удаляет все FolderFileLink для данной папки.
     * RecentFile-записи не удаляются.
     * Если папка с указанным id не существует — операция игнорируется (idempotent).
     */
    suspend fun delete(id: String)

    /**
     * Добавляет файл в папку, создавая запись FolderFileLink.
     * @param folderId UUID папки.
     * @param uri Нормализованный URI файла. ДОЛЖЕН соответствовать существующей RecentFile.
     * @throws FolderNotFoundException если папка с folderId не существует.
     * @throws FileNotInHistoryException если RecentFile с указанным uri не существует.
     * @throws FileDuplicateInFolderException если файл уже добавлен в эту папку.
     */
    suspend fun addFile(folderId: String, uri: String)

    /**
     * Удаляет ссылку файла из папки (FolderFileLink).
     * RecentFile-запись не удаляется.
     * Если ссылка не существует — операция игнорируется (idempotent).
     * @param folderId UUID папки.
     * @param uri Нормализованный URI файла.
     */
    suspend fun removeFile(folderId: String, uri: String)

    /**
     * Переименовывает папку.
     * @param id UUID папки.
     * @param newName Новое имя. Применяются те же правила валидации, что и при создании:
     *                не пустое, не только пробелы, длина ≤ 255 символов,
     *                символы из whitelist (Unicode letters + digits + `-` + `_`).
     * @throws FolderNotFoundException если папка с id не существует.
     * @throws FolderNameInvalidException если newName пустое или состоит только из пробелов.
     * @throws FolderNameTooLongException если newName.length > 255.
     * @throws FolderNameCharsInvalidException если newName содержит недопустимые символы.
     */
    suspend fun rename(id: String, newName: String)

    /**
     * Возвращает все папки, отсортированные по max(lastOpenedAt) файлов внутри папки DESC.
     * Папка без файлов сортируется по createdAt ASC (последней среди папок без файлов).
     * При изменении lastOpenedAt любого файла в папке — папка поднимается вверх в списке.
     * Никогда не бросает исключение — при ошибке возвращает пустой список.
     */
    suspend fun getAll(): List<Folder>

    /**
     * Возвращает URI файлов, добавленных в указанную папку.
     * Порядок не гарантирован.
     * @throws FolderNotFoundException если папка с folderId не существует.
     */
    suspend fun getFilesInFolder(folderId: String): List<String>
}
```

**Ошибки:**

| Исключение | Условие |
|------------|---------|
| `FolderLimitExceededException` | Попытка создать папку сверх лимита 100 (AC-41). |
| `FolderNameInvalidException` | Имя пустое или состоит только из пробельных символов (AC-32, AC-35). |
| `FolderNameTooLongException` | Длина имени превышает 255 символов (AC-34). |
| `FolderNameCharsInvalidException` | Имя содержит символы вне whitelist: `[\p{L}\p{N}\-_]` (Unicode letters + Unicode digits + `-` + `_`); AC-48. |
| `FolderNotFoundException` | Обращение к несуществующей папке. |
| `FileNotInHistoryException` | `addFile` вызван с URI, отсутствующим в `FileHistoryRepository` (AC-37). |
| `FileDuplicateInFolderException` | Файл уже добавлен в данную папку. |

### expect/actual: FilePicker

Платформенный компонент для открытия нативного файлового диалога.
`expect` объявляется в `commonMain` модуля `common`; `actual` реализуется в `androidMain` и `desktopMain`.

```kotlin
// commonMain
expect class FilePicker {
    /**
     * Открывает нативный файловый диалог для выбора PDF-файла.
     * @return URI выбранного файла или null, если пользователь отменил выбор (AC-14).
     */
    suspend fun pickPdfFile(): String?
}
```

**Android actual:** использует `ActivityResultContracts.OpenDocument` (SAF).
Возвращает `content://` URI или `null`.

**Desktop actual:** использует нативный диалог JVM/OS.
Возвращает абсолютный канонический путь или `null`.

### ViewModel: MainScreenViewModel

ViewModel главного экрана. Размещается в `common`. Не зависит от платформы.
Взаимодействует с UseCase-слоем, не с репозиториями напрямую.

**Входящие события (Intent):**

```
Событие                          Описание
-------------------------------  -------------------------------------------------------
OpenFilePicker                   Пользователь нажал кнопку «Открыть файл».
OpenRecentFile(id: String)       Пользователь нажал на элемент списка.
                                 Защита от double-tap: повторный вызов игнорируется,
                                 пока предыдущая навигация не завершена (CC-7).
ScreenVisible                    Экран стал видимым (Android: onResume; Desktop: первый показ
                                 главного экрана при запуске ИЛИ закрытие окна редактора — AC-17).
                                 «Получение фокуса окном» само по себе НЕ является триггером.
CancelNavigation                 Навигация к редактору отменена до завершения flush (CC-6).
MergeSafRecords(                 Пользователь принял решение об объединении SAF-записей (AC-5b).
  keepId: String,
  discardId: String,
  newUri: String
)
RejectSafMerge(                  Пользователь отклонил предложение объединить SAF-записи (CC-1).
  existingId: String,
  newUri: String
)
CreateFolder(name: String)       Пользователь подтвердил создание папки в диалоге (AC-32).
DeleteFolder(id: String)         Пользователь подтвердил удаление папки в диалоге подтверждения (AC-39).
RequestDeleteFolder(id: String)  Пользователь инициировал удаление папки — ViewModel открывает
                                 диалог подтверждения (AC-39).
AddFileToFolder(                 Пользователь добавил файл в папку (AC-27).
  folderId: String,
  fileUri: String
)
RemoveFileFromFolder(            Пользователь удалил файл из папки (AC-30).
  folderId: String,
  fileUri: String
)
RenameFolder(                    Пользователь подтвердил переименование папки в диалоге.
  id: String,                    UUID папки.
  newName: String                Новое имя (прошедшее валидацию в UI).
)
DismissCreateFolderDialog        Пользователь закрыл диалог создания папки без сохранения
                                 (нажал Back / Escape / тап вне диалога).
                                 Введённый текст теряется — saved state применяется только
                                 при background-уходе, но не при явной отмене (AC-35/AC-36 исключение).
DismissDeleteFolderDialog        Пользователь закрыл диалог подтверждения удаления.
```

**Состояние экрана (UiState):**

```kotlin
data class MainScreenUiState(
    val recentFiles: List<RecentFileUiModel>,       // Отсортированы по openedAt DESC
    val folders: List<FolderUiModel>,               // Отсортированы по max(lastOpenedAt) файлов внутри DESC; папка без файлов — по createdAt ASC; пустой список → FoldersSection скрыта (AC-40)
    val isLoading: Boolean,                         // true во время начальной загрузки истории
    val navigationTarget: NavigationTarget?,        // non-null = ожидается навигация
    val safMergeDialog: SafMergeDialogState?,       // non-null = показать диалог слияния SAF (AC-5b)
    val createFolderDialog: CreateFolderDialogState?, // non-null = показать диалог создания папки (AC-32)
    val deleteFolderDialog: DeleteFolderDialogState?, // non-null = показать диалог подтверждения удаления (AC-39)
    val errorEvent: ErrorEvent?,                    // Одноразовое событие ошибки (показать snackbar)
)

sealed class NavigationTarget {
    data class Editor(val uri: String, val lastPageIndex: Int) : NavigationTarget()
    object FilePicker : NavigationTarget()
}

data class RecentFileUiModel(
    val id: String,
    val displayName: String,
    val openedAt: Instant,
    val availabilityStatus: AvailabilityStatus,
    val thumbnailState: ThumbnailState,
    val lastPageIndex: Int,   // 0-based; используется для отображения подсказки «стр. N» в превью (решение по отображению — на усмотрение @Designer).
)

sealed class ThumbnailState {
    object Loading : ThumbnailState()
    data class Ready(val imageData: ByteArray) : ThumbnailState()
    object Error : ThumbnailState()
}

data class SafMergeDialogState(
    val existingRecord: RecentFileUiModel,
    val newUri: String,
)

/**
 * Состояние диалога создания папки.
 * [currentName] — текущий текст в поле ввода (сохраняется при background/window-switch, AC-36).
 * [isConfirmEnabled] — false если currentName пустое или состоит только из пробелов (AC-35).
 */
data class CreateFolderDialogState(
    val currentName: String,
    val isConfirmEnabled: Boolean,
)

/**
 * Состояние диалога подтверждения удаления папки (AC-39).
 */
data class DeleteFolderDialogState(
    val folderId: String,
    val folderName: String,
)

data class FolderUiModel(
    val id: String,
    val name: String,
    val fileCount: Int,              // Количество файлов в папке; только для отображения
    val createdAt: Instant,
    val lastFileOpenedAt: Instant?,  // max(lastOpenedAt) файлов внутри; null если папка пуста (сортировка по createdAt)
)

sealed class ErrorEvent {
    object FileNotFound : ErrorEvent()
    object FileError : ErrorEvent()
    object HistoryFlushFailed : ErrorEvent()
    object ThumbnailGenerationFailed : ErrorEvent()
    object FolderLimitExceeded : ErrorEvent()      // Достигнут лимит 100 папок (AC-41)
    object FolderNameCharsInvalid : ErrorEvent()   // Имя содержит недопустимые символы
    object FolderOperationFailed : ErrorEvent()    // Прочие ошибки операций с папками
}
```

### UseCase: AddToHistoryUseCase

Отвечает за один бизнес-процесс: добавить запись в историю при открытии файла.
Размещается в `:shared`. Зависит от `FileHistoryRepository` (порт).

**Входные данные:**
```
uri           String    Нормализованный URI файла (нормализация выполнена до вызова)
displayName   String    Отображаемое имя файла
fileSize      Long?     Размер файла в байтах (Android SAF — опционально)
openedAt      Instant   Момент инициирования открытия (AC-23)
```

**Выходные данные:**
```
Result<AddHistoryResult>

AddHistoryResult:
  Added(record: RecentFile)           — новая запись добавлена
  Moved(record: RecentFile)           — существующая запись перемещена в начало
  SafFuzzyMatchDetected(              — Android SAF fuzzy-match: displayName+size совпали
    existing: RecentFile,               но URI отличается — требуется решение пользователя
    newUri: String
  )
```

### UseCase: CheckAvailabilityUseCase

Асинхронно обновляет статус доступности всех записей при показе главного экрана (AC-9a).
Размещается в `:shared`.

**Контракт:** принимает список `RecentFile`, возвращает `Flow<AvailabilityUpdate>` —
поток обновлений статуса по мере их поступления. UI обновляется инкрементально.

```
AvailabilityUpdate(id: String, status: AvailabilityStatus)
```

**Параллелизм проверок:** UseCase запускает проверки **параллельно** с ограничением
не более **5 одновременных** вызовов `FileAvailabilityChecker.check(uri)`.
Ограничение реализуется через `Semaphore(5)`.
Максимальное время завершения всего пакета из 20 записей — не более **2 × ⌈20/5⌉ = 8 секунд**
(4 волны по 5 проверок × 2 с таймаут каждой).
Порядок эмиссии `AvailabilityUpdate` не гарантирован — UI обновляется инкрементально
по мере поступления результатов.

**Таймаут на одну проверку:** каждый вызов `FileAvailabilityChecker.check(uri)` внутри
UseCase ДОЛЖЕН завершиться не позднее чем через **2 секунды** (аналогично `checkSync`).
При превышении таймаута для данной записи UseCase эмитирует `AvailabilityUpdate(id, FILE_ERROR)`
и продолжает обработку остальных записей. Зависший вызов `check` отменяется через
`withTimeout(2_000)` (или эквивалентный механизм корутин).

### UseCase: OpenRecentFileUseCase

Выполняет открытие файла из списка, включая синхронную проверку доступности (CC-19).
Размещается в `:shared`.

**Шаги:**
1. Синхронная проверка доступности файла (`FileAvailabilityChecker.checkSync`).
2. Если не доступен — вернуть `OpenFileResult.NotAvailable(updatedStatus)`.
3. Если доступен — вернуть `OpenFileResult.Success(uri)`.

**Выходные данные:**
```
OpenFileResult:
  Success(uri: String)
  NotAvailable(status: AvailabilityStatus)   — NOT_FOUND или FILE_ERROR
```

---

## Business Logic

### BL-1: Открытие файла из списка недавних (AC-10, AC-11, CC-7, CC-19)

1. Пользователь нажимает на элемент списка — `ViewModel` получает событие `OpenRecentFile(id)`.
2. Если навигация уже инициирована (`navigationTarget != null`) — событие игнорируется (debounce, CC-7).
3. `ViewModel` устанавливает `isNavigating = true`, записывает `openedAt = Instant.now()`.
4. Вызывает `OpenRecentFileUseCase`.
5. Если `NotAvailable(NOT_FOUND)` — обновляет `availabilityStatus` записи, устанавливает `errorEvent = FileNotFound`, сбрасывает `isNavigating`.
6. Если `NotAvailable(FILE_ERROR)` — аналогично, `errorEvent = FileError`.
7. Если `Success(uri)` — вызывает `AddToHistoryUseCase` (upsert, flush).
8. Если flush завершился `HistoryFlushException` — `errorEvent = HistoryFlushFailed`, навигация продолжается (AC-18).
9. `ViewModel` читает `RecentFile.lastPageIndex` для данной записи (если запись не существует или поле отсутствует — использует 0). Устанавливает `navigationTarget = NavigationTarget.Editor(uri = file.uri, lastPageIndex = lastPageIndex)`. `MainScreenViewModel` передаёт значение `lastPageIndex` as-is, без clamp — это интеграционный контракт. `EditorViewModel` (или эквивалентный компонент редактора) ОБЯЗАН при получении `NavigationTarget.Editor` с `lastPageIndex >= pageCount` использовать `max(0, pageCount - 1)` (AC-58).

### BL-2: Открытие файла через нативный диалог (AC-12–AC-14, CC-6)

1. Пользователь нажимает кнопку «Открыть файл» — `ViewModel` получает `OpenFilePicker`.
2. `ViewModel` устанавливает `navigationTarget = NavigationTarget.FilePicker`.
3. `FilePicker.pickPdfFile()` вызывается из UI.
4. Если `null` (отмена) — `navigationTarget` сбрасывается. Завершить (AC-14).
5. Если URI получен — `openedAt = Instant.now()` (AC-23).
6. URI нормализуется (см. «Нормализация URI»).
7. Вызывается `AddToHistoryUseCase`.
8. Если `SafFuzzyMatchDetected` — ViewModel устанавливает `safMergeDialog`, ожидает события `MergeSafRecords` или `RejectSafMerge`. Навигация к редактору приостанавливается.
9. Если flush успешен или flush завершился ошибкой — навигация к редактору продолжается (AC-18).
10. При отмене навигации до завершения flush (CC-6) — `ViewModel` получает `CancelNavigation`, вызывает `FileHistoryRepository.rollbackUpsert(uri)`.

### BL-3: Обработка SAF fuzzy-match (AC-5b, CC-1, CC-2)

Событие `SafFuzzyMatchDetected` означает, что `displayName` и `fileSize` нового URI совпадают с существующей записью, но URI отличается.

1. ViewModel устанавливает `safMergeDialog` с данными обоих URI для отображения пользователю.
2. Пользователь видит диалог с **полными URI обоих файлов** (CC-2).
3. Если пользователь принял слияние (`MergeSafRecords`):
   - Существующая запись обновляется: `uri = newUri`, `openedAt = openedAt_of_open_action`.
   - Старая запись с устаревшим URI удаляется.
   - Запись перемещается в начало.
4. Если пользователь отклонил слияние (`RejectSafMerge`, CC-1):
   - Существующая запись с устаревшим URI остаётся в списке и помечается `FILE_ERROR` (устаревший URI).
   - Новая запись с новым URI добавляется как отдельная.
   - Обе записи сосуществуют — дублей по нормализованному URI не возникает, поскольку URI разные.

### BL-4: Обновление статуса доступности при показе экрана (AC-9a, AC-17)

**Триггеры:**
- Android: `onResume` экрана.
- Desktop: (1) первый показ главного экрана при запуске; (2) закрытие окна редактора.
- **Исключение:** получение фокуса окном (window focus gained) само по себе НЕ является триггером проверки доступности — только закрытие окна редактора (AC-17).

**Алгоритм:**
1. `ViewModel` немедленно отображает список с сохранёнными статусами (не блокирует UI).
2. Запускает `CheckAvailabilityUseCase` в фоне.
3. По каждому `AvailabilityUpdate` обновляет соответствующий элемент в `recentFiles`.

**Desktop: синхронизация с flush (CC-15):**
Обновление списка на Desktop инициируется событием закрытия окна редактора только после получения сигнала о завершении flush (или его ошибки). Если flush завершился ошибкой — список показывает последнее известное состояние.

### BL-5: Генерация и кеширование миниатюр (AC-7, AC-8, AC-9d, AC-22, CC-11, CC-13, CC-18)

1. При отображении элемента списка ViewModel запрашивает миниатюру из `ThumbnailRepository`.
2. Если кеш актуален (mtime файла не изменился) — возвращает `ThumbnailState.Ready`.
3. Если кеш отсутствует или инвалидирован — устанавливает `ThumbnailState.Loading` (AC-8, плейсхолдер загрузки).
4. Запускает `PdfThumbnailGenerator.generate` в изолированном контексте (CC-11: OOM не пробрасывается).
5. Параллельность генерации ограничена: не более **4 параллельных** задач одновременно. Видимые элементы имеют приоритет (CC-13).
6. При успехе: сохраняет результат в `ThumbnailRepository.put`, устанавливает `ThumbnailState.Ready`.
7. При ошибке (`ThumbnailGenerationException`): устанавливает `ThumbnailState.Error` (AC-9d, плейсхолдер ошибки, визуально отличный от Loading).
8. При переходе приложения в фон (Android) — задачи приостанавливаются. При возврате на передний план — возобновляются для видимых элементов (CC-18).

### BL-6: Вытеснение истории (AC-5, AC-9b, AC-9e, CC-8, CC-9, CC-10)

Полный алгоритм описан в разделе «Алгоритм добавления записи» модели `FileHistory`.

**Desktop-атомарность (CC-10):** реализация `FileHistoryRepository` для Desktop обеспечивает взаимное исключение при параллельных операциях `upsert` из разных окон через **двухуровневую блокировку** (см. Implementation Notes — «Многооконность Desktop»). При одновременном достижении лимита двумя окнами вытесняется ровно одна самая старая запись; обе новые записи добавляются.

**Race condition (CC-9):** вытеснение определяется по `openedAt`. Только что добавленный файл имеет самое свежее значение — он не может быть вытеснен в ходе той же операции.

### BL-8: Создание папки (AC-24, AC-32, AC-33, AC-34, AC-35, AC-36, AC-41, AC-48, AC-49)

**Правила валидации имени папки (применяются одинаково при создании и переименовании, AC-48):**
- Допустимые символы (whitelist): Unicode letters (`\p{L}`), Unicode digits (`\p{N}`), дефис (`-`), подчёркивание (`_`).
- Проверка через regex: `^\p{L}\p{N}\-_]+$` применяется после trim; в коде — `^[\p{L}\p{N}\-_]+$`.
- Пробелы, знаки препинания и прочие символы вне whitelist **недопустимы**.
- При вставке текста (paste): берётся только первая строка вставленного фрагмента, затем из неё удаляются все символы вне `[\p{L}\p{N}\-_]` (strip). Если результат после strip пустой — строка не вставляется; кнопка OK остаётся заблокированной.
- Максимальная длина: 255 символов (после strip).
- `FolderNameCharsInvalidException` выбрасывается репозиторием, если имя прошло trim и проверку на пустоту, но не соответствует whitelist-regex (AC-48). Используется как защитный контракт уровня порта независимо от UI-фильтрации.

1. Пользователь инициирует создание папки — ViewModel устанавливает `createFolderDialog = CreateFolderDialogState(currentName = "", isConfirmEnabled = false)`.
2. Пользователь вводит имя. ViewModel обновляет `currentName` и пересчитывает `isConfirmEnabled`:
   - `isConfirmEnabled = true` если `currentName.trim().isNotEmpty()` **И** `currentName` содержит только допустимые символы (whitelist) **И** `currentName.length ≤ 255`.
   - Кнопка OK в диалоге заблокирована (`enabled = false`), пока `isConfirmEnabled = false` (AC-35).
3. `currentName` сохраняется в состоянии ViewModel — выживает при уходе в фон и смене конфигурации (AC-36).
   **Исключение:** нажатие Back / Escape / тап вне диалога = явная отмена (`DismissCreateFolderDialog`); `currentName` теряется (не сохраняется между сессиями открытия диалога).
4. Пользователь нажимает OK — ViewModel получает `CreateFolder(name)`.
5. ViewModel вызывает `FolderRepository.create(name.trim())`.
6. Если `FolderLimitExceededException` — ViewModel устанавливает `errorEvent = FolderLimitExceeded`; диалог закрывается (AC-41).
7. Если `FolderNameInvalidException` — недостижимо при корректном `isConfirmEnabled`; логируется как внутренняя ошибка; диалог закрывается.
8. При успехе: диалог закрывается; список `folders` обновляется; `FoldersSection` становится видимой (AC-40).
9. Дубликаты имён допустимы — создаётся вторая папка с тем же именем, но другим UUID (AC-33).

### BL-9: Удаление папки (AC-25, AC-29, AC-30, AC-39)

1. Пользователь инициирует удаление — ViewModel получает `RequestDeleteFolder(id)`.
2. ViewModel устанавливает `deleteFolderDialog = DeleteFolderDialogState(folderId = id, folderName = ...)` (AC-39).
3. Пользователь видит диалог подтверждения с именем папки.
4. Если пользователь подтверждает (`DeleteFolder(id)`) — ViewModel вызывает `FolderRepository.delete(id)`.
5. `delete` каскадно удаляет все `FolderFileLink` для данной папки; `RecentFile`-записи не затрагиваются (AC-29, AC-30).
6. Список `folders` обновляется. Если папок больше нет — `FoldersSection` скрывается (AC-40).
7. Если пользователь отменяет — `deleteFolderDialog = null`; действий с данными нет.

**Архивные записи после удаления папки (AC-55):**
При удалении папки каскадно удаляются только `FolderFileLink` для этой папки.
`RecentFile`-записи, в том числе вытесненные (архивные, на которые ссылался `FolderFileLink` удалённой папки), **остаются в хранилище**.
- Архивные `RecentFile`-записи, вытесненные LRU и не имеющие ссылок ни в одной папке, в UI **не отображаются** (они уже отсутствуют в `FileHistoryRepository`, т.е. не попадают в `getAll()`).
- Физически эти записи остаются в хранилище как потенциальная основа для будущей функции «Все файлы» (out-of-scope).
- Если архивная запись всё ещё ссылается через другой `FolderFileLink` (другие папки) — она продолжает существовать в тех папках (поведение не меняется).

### BL-10: Добавление файла в папку (AC-27, AC-37)

1. ViewModel получает `AddFileToFolder(folderId, fileUri)`.
2. Файл ДОЛЖЕН существовать в `FileHistoryRepository` (AC-37). Если нет — `FolderRepository.addFile` выбросит `FileNotInHistoryException`; ViewModel устанавливает `errorEvent = FolderOperationFailed`.
3. Вызывается `FolderRepository.addFile(folderId, fileUri)`.
4. При успехе: `FolderUiModel.fileCount` обновляется.
5. Если файл уже в папке (`FileDuplicateInFolderException`) — операция игнорируется тихо (idempotent с точки зрения UX).

### BL-13: Переименование папки

1. Пользователь инициирует переименование — ViewModel открывает диалог переименования (аналогично созданию) с `currentName = existingFolder.name`.
2. Применяются **идентичные** правила валидации, что и в BL-8 §validation (AC-51, AC-48):
   whitelist `[\p{L}\p{N}\-_]`, strip при paste (первая строка, удалить символы вне whitelist), max 255 символов после strip.
   `isConfirmEnabled` рассчитывается по той же формуле: `currentName.trim().isNotEmpty() AND соответствует whitelist-regex AND length ≤ 255`.
3. `currentName` сохраняется в ViewModel state при уходе в фон / смене конфигурации; сбрасывается при явной отмене (`DismissRenameFolderDialog` / Back / Escape).
4. Пользователь нажимает OK — ViewModel получает `RenameFolder(id, newName)`.
5. ViewModel вызывает `FolderRepository.rename(id, newName.trim())`.
6. При ошибках:
   - `FolderNotFoundException` — `errorEvent = FolderOperationFailed`.
   - `FolderNameInvalidException` — недостижимо при корректном UI; логируется, диалог закрывается.
   - `FolderNameTooLongException` — `errorEvent = FolderOperationFailed`; диалог закрывается.
   - `FolderNameCharsInvalidException` — недостижимо при корректном UI; логируется, `errorEvent = FolderNameCharsInvalid`.
7. При успехе: диалог закрывается; список `folders` обновляется (имя обновлено, порядок сортировки не меняется).

### BL-11: Недоступный файл в папке (AC-38, AC-53)

Файл, находящийся в папке, может иметь любой `AvailabilityStatus`. Файлы в папке отображаются с тем же `AvailabilityStatus`, что и соответствующая `RecentFile`-запись. Папка не скрывает и не изменяет статус доступности файла. При открытии недоступного файла из папки применяется стандартный BL-1 (проверка доступности, сообщение пользователю).

**ARCHIVED_UNAVAILABLE и UI при попытке открытия (AC-53):**
Если файл вытеснен из истории (`FolderFileLink` сохранён, `RecentFile` отсутствует) и `checkSync` возвращает `NOT_FOUND` или `FILE_ERROR`:
- Статус файла в `FolderFileLink`-контексте устанавливается в `ARCHIVED_UNAVAILABLE` (модель сохраняет этот статус).
- В UI файл отображается с **отдельным визуальным индикатором `ARCHIVED_UNAVAILABLE`** — визуально отличным как от `FILE_ERROR`-индикатора (файл найден, но повреждён/зашифрован), так и от чистого архивного состояния без ошибки доступности (AC-50). Конкретное визуальное решение определяет @Designer, однако спецификация требует три визуально различимых состояния элемента папки: `ARCHIVED` (вытеснен, доступность ещё не проверялась), `FILE_ERROR` (доступен в истории, но повреждён/недоступен по иной причине), `ARCHIVED_UNAVAILABLE` (вытеснен + checkSync вернул NOT_FOUND или FILE_ERROR).
- `ErrorEvent.FileError` применяется для `snackbar`-уведомления при попытке открытия (аналогично `FILE_ERROR` в BL-1).
- Модель `FolderFileLink` сохраняет `ARCHIVED_UNAVAILABLE` для потенциального будущего восстановления (upsert при следующей успешной проверке доступности, см. AC-50).

### BL-12: Структура главного экрана с секциями (AC-31, AC-40, AC-42)

Главный экран состоит из двух вертикальных секций:

- **RecentFilesSection** (верхняя): список недавних файлов. Отображается всегда, в том числе при пустой истории (пустое состояние с подсказкой).
- **FoldersSection** (нижняя): список папок. Скрыта, если `folders.isEmpty()` (AC-40). Видна сразу после создания первой папки.
  Папки сортируются по `max(FolderFileLink.lastOpenedAt)` по всем файлам в папке DESC (самая «активная» — первая). Если папка не содержит файлов — вместо `max(lastOpenedAt)` используется `Folder.createdAt` (папки без файлов сортируются по `createdAt` ASC после всех папок с файлами). При каждом открытии файла из папки `FolderFileLink.lastOpenedAt` обновляется → папка поднимается вверх (пересчёт сортировки после каждого `upsert`, BL-5, пункт изменения).

**Состояние «пустая история + есть папки» (AC-42):** обе секции видны. `RecentFilesSection` отображает пустое состояние (подсказка открыть файл); `FoldersSection` показывает список папок.

**Пустая папка (0 FolderFileLink) — легитимное состояние (AC-56):**
Папка без файлов является допустимым состоянием и **не удаляется автоматически**.
- `FolderUiModel.fileCount = 0` — отображается как «0 файлов» (или эквивалентная метка по дизайн-системе).
- `FolderUiModel.lastFileOpenedAt = null` — папка сортируется по `Folder.createdAt` ASC среди других пустых папок (после всех папок с файлами), согласно алгоритму сортировки BL-12.
- `FoldersSection` отображает пустые папки наравне с непустыми; автоудаление отсутствует.
- Пустая папка может быть создана пользователем изначально (без добавления файлов) или стать пустой после удаления всех файлов из неё (`RemoveFileFromFolder` для последнего файла).

### BL-14: Сохранение позиции страницы при выходе из редактора (AC-57)

**Триггер:**
- Android: `onPause` / `onStop` активности или фрагмента редактора.
- Desktop: закрытие окна редактора.

**Контракт:**
1. Редактор (EditorViewModel или платформенный lifecycle-компонент) определяет текущий видимый `pageIndex` (0-based).
2. Вызывается `FileHistoryRepository.updateLastPage(uri = нормализованный URI открытого файла, pageIndex = текущая страница)`.
3. Если запись с указанным URI не существует в истории — вызов является no-op (запись могла быть вытеснена LRU пока редактор был открыт). Никакой новой записи не создаётся.
4. Если `updateLastPage` завершается `HistoryFlushException` — ошибка логируется (только категория, без URI). Редактор продолжает закрытие без пользовательского уведомления (некритичный путь: позиция страницы не является обязательным инвариантом).
5. `openedAt` и порядок записи в истории **не изменяются** — `updateLastPage` обновляет только поле `lastPageIndex`.

**Интеграционный контракт (если EditorViewModel вне скоупа данной спецификации):**
Компонент, реализующий редактор, ДОЛЖЕН вызывать `FileHistoryRepository.updateLastPage` в точке жизненного цикла «выход из редактора» (до финального destroy/dismiss). Порт `FileHistoryRepository` декларирован в `:shared` и доступен в любом модуле, зависящем от `:shared`.

### BL-7: Нормализация URI

**Desktop (AC-5a):**
1. Разрешить символические ссылки (`canonical path`).
2. Удалить trailing slash.
3. Применить Unicode NFC нормализацию (CC-3).
4. На Windows и macOS: привести к нижнему регистру (case-insensitive FS).
5. На Linux: оставить регистр без изменений (case-sensitive FS).
6. Если Unicode NFC нормализация невозможна — использовать побайтовое сравнение (безопаснее ложного дублирования, CC-3).

**Android:** URI `content://` используется as-is (AC-5b). Нормализация не применяется.

---

## Edge Cases

Каждый Critical и High corner case из регистра явно адресован ниже.

| CC # | Severity | Условие | Поведение |
|------|----------|---------|-----------|
| CC-1 | Critical | SAF URI невалиден после реавторизации; пользователь отклоняет объединение | Новая запись с новым URI добавляется; старая запись с устаревшим URI помечается `FILE_ERROR` и остаётся в списке. Обе записи существуют как отдельные (BL-3, шаг 4). |
| CC-2 | Critical | Два разных файла с одинаковым `displayName` и `fileSize`; fuzzy-match ложно срабатывает | Диалог слияния отображает полные URI обоих файлов. При отклонении — обе записи остаются отдельными (BL-3, шаг 4). |
| CC-5 | Critical | Приложение крашнулось после flush истории, но до показа редактора | При следующем запуске файл присутствует в истории (flush был завершён). Статус доступности проверяется асинхронно по BL-4. |
| CC-8 | Critical | Вытеснение при заполненной истории; статус всех 20 записей `UNKNOWN` (проверка не завершена) | Pessimistic: запись с `UNKNOWN` трактуется как `NOT_FOUND`. Самая старая из `UNKNOWN`-записей вытесняется (BL-6). |
| CC-3 | High | Путь содержит Unicode (кириллица, иероглифы, emoji); нормализация ведёт себя по-разному на ОС | Unicode NFC применяется перед сравнением. При невозможности нормализации — побайтовое сравнение (BL-7). |
| CC-6 | High | Пользователь нажимает «Назад» до завершения flush (AC-18) | `CancelNavigation` → `FileHistoryRepository.rollbackUpsert(uri)`. Если flush уже завершён — запись удаляется (BL-2, шаг 10). |
| CC-7 | High | Double-tap на элемент списка | Второй тап игнорируется, пока `navigationTarget != null` (BL-1, шаг 2). История записывается ровно один раз. |
| CC-9 | High | История заполнена (20 доступных записей); новая запись вытесняет самую старую | Вытесняется запись с наименьшим `openedAt`. Только что добавленная запись имеет наибольший `openedAt` — не вытесняется (BL-6). |
| CC-10 | High | Desktop: два окна одновременно вытесняют самую старую запись | `FileHistoryRepository.upsert` атомарен для Desktop. Второй запрос видит обновлённое состояние; вытесняется ровно одна запись (BL-6). |
| CC-11 | High | OOM при генерации миниатюры большого PDF (500+ страниц) | `PdfThumbnailGenerator.generate` изолирует OOM; возвращает `Result.failure`; элемент получает `ThumbnailState.Error`; приложение продолжает работу (BL-5, шаг 4). |
| CC-12 | High | SAF `SecurityException` при открытии потока URI | `OpenRecentFileUseCase` перехватывает `SecurityException`; возвращает `NotAvailable(FILE_ERROR)`. Запись помечается `FILE_ERROR`, не `NOT_FOUND` (BL-1, шаг 6). |
| CC-15 | High | Desktop: окно редактора закрыто (триггер AC-17), но flush ещё не завершён | Обновление главного экрана ожидает сигнала о завершении flush (BL-4). При ошибке flush — показывается последнее известное состояние. |
| CC-17 | High | Новый пользователь видит пустое состояние; кнопка открытия файла не очевидна | Текстовая подсказка явно указывает на кнопку открытия файла или её местоположение (AC-1). @Designer учитывает этот сценарий. |
| CC-18 | High | Android: уход в фон во время генерации миниатюр; возврат в приложение | При возврате на передний план генерация возобновляется для видимых элементов (BL-5, шаг 8). |
| CC-19 | High | Файл удалён пока главный экран открыт; пользователь нажимает на него | Синхронная проверка `FileAvailabilityChecker.checkSync` перед открытием. Если недоступен — запись обновляется, пользователь видит сообщение (BL-1, шаг 4–6). |
| Q5 | High | `rollbackUpsert` завершился `IOException` (например, диск недоступен) | Реализация выбрасывает `HistoryFlushException`. ViewModel перехватывает, логирует категорию ошибки (без URI), показывает `errorEvent = HistoryFlushFailed`. Навигация **отменяется** (пользователь уже нажал «Назад»). UI возвращается к актуальному состоянию списка из последнего успешно персистированного снапшота. |
| Q10 | High | Desktop SQLite/FileIO: старые записи имеют `fileMtime = null` после миграции схемы | При чтении записи с `fileMtime = null` реализация трактует её как «mtime неизвестен». `ThumbnailRepository.get` с `currentFileMtime = null` всегда возвращает `null` (принудительная перегенерация). Новое значение `fileMtime` записывается при следующей генерации миниатюры. Миграция схемы НЕ ДОЛЖНА удалять существующие миниатюры. |
| Q12 | High | `RejectSafMerge`: старая запись помечается `FILE_ERROR` и остаётся в истории бессрочно, занимая один из 20 слотов | Запись с `FILE_ERROR` подпадает под алгоритм приоритетного вытеснения (BL-6, шаг 5a): при следующем добавлении нового файла в полный список она вытесняется **первой** среди записей с не-`AVAILABLE` статусом. Таким образом, `FILE_ERROR`-запись занимает слот лишь временно и не засоряет историю навсегда. |
| OQ-4 | High | Android backup восстанавливает историю после переустановки; все SAF-URI становятся `FILE_ERROR` (права сброшены) | История исключена из Android backup через `dataExtractionRules`. После переустановки показывается чистое пустое состояние. |
| Q-CC10-inprocess | Critical | Desktop: два окна одного JVM-процесса одновременно вызывают `upsert`; JVM FileLock не блокирует потоки внутри процесса | In-process `Mutex` захватывается до FileLock. Оба уровня блокировки обязательны (см. Implementation Notes — «Многооконность Desktop»). |
| Q-CAUC-parallel | High | `CheckAvailabilityUseCase`: 20 последовательных проверок × 2 с = 40 с задержки | Проверки выполняются параллельно пачками по 5 (`Semaphore(5)`); максимальное суммарное время ≤ 8 с (4 волны × 2 с). |
| AC-34 | High | Имя папки превышает 255 символов | `FolderRepository.create` выбрасывает `FolderNameTooLongException`; папка не создаётся; UI показывает сообщение об ошибке (BL-8). |
| AC-41 | Medium | Попытка создать папку при достигнутом лимите 100 папок | `FolderRepository.create` выбрасывает `FolderLimitExceededException`; папка не создаётся; `errorEvent = FolderLimitExceeded`; диалог закрывается (BL-8, шаг 6). |
| AC-35 | High | Пользователь вводит пустое или пробельное имя в диалоге создания папки | Кнопка OK заблокирована (`isConfirmEnabled = false`); `FolderRepository.create` не вызывается (BL-8, шаг 2). |
| AC-36 | Medium | Уход в фон / смена конфигурации при открытом диалоге создания папки | `currentName` сохраняется в ViewModel state; диалог восстанавливается с введённым текстом (BL-8, шаг 3). |
| AC-37 | Critical | `addFile` вызван с URI, которого нет в истории | `FolderRepository.addFile` выбрасывает `FileNotInHistoryException`; ViewModel устанавливает `errorEvent = FolderOperationFailed`; ссылка не создаётся (BL-10, шаг 2). |
| AC-38 | High | Файл в папке стал недоступным (удалён с диска) | Файл отображается в папке с `AvailabilityStatus = NOT_FOUND` или `FILE_ERROR` (аналогично списку истории). Попытка открыть — стандартный BL-1 (BL-11). |
| AC-39 | High | Удаление папки без диалога подтверждения | Удаление **всегда** требует диалога подтверждения. `DeleteFolder` без предшествующего `RequestDeleteFolder` (и показа диалога) недопустимо (BL-9). |
| AC-40 | Medium | Все папки удалены | `folders.isEmpty() == true` → `FoldersSection` скрывается (BL-12). |
| AC-33 | Medium | Попытка создать папку с именем, совпадающим с существующей | Создаётся новая папка с тем же именем но другим UUID. Дубликаты имён допустимы (BL-8, шаг 9). |
| AC-42 | Medium | Пустая история + есть папки | Обе секции видны. `RecentFilesSection` показывает пустое состояние; `FoldersSection` показывает список папок (BL-12). |
| AC-29-cascade | High | Удаление папки с файлами | Каскадно удаляются только `FolderFileLink`. `RecentFile`-записи остаются нетронутыми. Файлы продолжают отображаться в истории (BL-9, шаг 5). |
| Q1+Q5 | High | Файл вытеснен из истории LRU, но остаётся в папке | `FolderFileLink` не удаляется при eviction. При открытии из папки — синхронная проверка `checkSync` (CC-19). Если доступен — открывается, вызывается `upsert` (добавляется обратно в историю). Если недоступен — BL-1 (сообщение, ссылка остаётся); UI показывает статус `ARCHIVED_UNAVAILABLE` для данного файла внутри папки (AC-50). |
| AC-50 | High | Файл вытеснен из истории LRU и недоступен при открытии из папки | `AvailabilityStatus.ARCHIVED_UNAVAILABLE` устанавливается в FolderFileLink-контексте. Файл отображается в папке с **отдельным** визуальным индикатором `ARCHIVED_UNAVAILABLE` — визуально отличным от `FILE_ERROR` и от архивного состояния без ошибки доступности (три различимых состояния: ARCHIVED / FILE_ERROR / ARCHIVED_UNAVAILABLE, см. BL-11). Ссылка остаётся. При следующей успешной проверке статус сбрасывается к AVAILABLE и файл возвращается в историю. |
| Q2 | Medium | Пользователь переименовывает папку | `FolderRepository.rename(id, newName)`. Валидация: те же правила, что при создании (длина, символы, whitelist). Порядок сортировки не меняется при переименовании (BL-13). |
| Q3 | Medium | Back / Escape в диалоге создания папки | Диалог закрывается (`DismissCreateFolderDialog`); введённый текст теряется. Saved state применяется только при уходе в фон (background), не при явной отмене (BL-8, шаг 3). |
| Q4 | Medium | Порядок папок при изменении файла | Папки сортируются по `max(lastOpenedAt)` файлов внутри. При `upsert` любого файла из папки — папка поднимается вверх. Папка без файлов — по `createdAt` ASC (BL-12). |
| Q6 | High | Вставка (paste) недопустимых символов в имя папки | Берётся первая строка вставленного текста; недопустимые символы удаляются (strip). Если после strip строка пустая — вставка не производится; OK заблокирован. Whitelist: Unicode letters + digits + `-` + `_` (BL-8). |
| Q6-rename | High | Вставка (paste) недопустимых символов при переименовании папки | Та же логика strip, что в BL-8 (BL-13). |
| AC-53 | High | Вытесненный LRU файл в папке; `checkSync` возвращает NOT_FOUND или FILE_ERROR при попытке открытия | Статус FolderFileLink → `ARCHIVED_UNAVAILABLE` (модель сохраняет). В UI файл отображается с **отдельным** визуальным индикатором `ARCHIVED_UNAVAILABLE`, визуально отличным от `FILE_ERROR` и от чистого архивного состояния (`ARCHIVED`). `ErrorEvent.FileError` применяется для snackbar-уведомления при попытке открытия. Три визуально различимых состояния обязательны (см. BL-11, AC-50). |
| AC-54 | Medium | Eviction «папочного» файла LRU-алгоритмом | Переход файла в архивное состояние молчаливый — уведомление не отображается. `FolderFileLink` сохраняется. Статус `ARCHIVED_UNAVAILABLE` устанавливается лениво: только при следующей попытке открытия из папки (BL-1, FileHistory алгоритм добавления). |
| AC-55 | Medium | Удаление папки; папка содержала архивные (вытесненные LRU) файлы | Каскадно удаляются только FolderFileLink. Архивные RecentFile остаются в хранилище (потенциальная база «Все файлы»); в UI не отображаются (отсутствуют в `getAll()`). Если файл ссылается из другой папки — остаётся в ней (BL-9). |
| AC-56 | Low | Пользователь создал папку, не добавив файлы; или удалил последний файл из папки | Пустая папка (0 FolderFileLink) — легитимное состояние. Автоудаление отсутствует. `fileCount = 0`; сортировка по `Folder.createdAt` ASC среди пустых папок (BL-12). |
| AC-57 | High | Пользователь закрывает редактор; позиция страницы должна сохраниться для восстановления при следующем открытии | `FileHistoryRepository.updateLastPage(uri, pageIndex)` вызывается при выходе из редактора. Если запись не существует в истории (вытеснена LRU) — no-op, новая запись не создаётся. `openedAt` и порядок в истории не меняются (BL-14). Ошибка I/O логируется, пользователю не показывается. |
| AC-58 | High | Сохранённый `lastPageIndex` превышает фактическое число страниц PDF (файл изменён, страницы удалены) | `MainScreenViewModel` передаёт `lastPageIndex` as-is. `EditorViewModel` при получении `NavigationTarget.Editor` выполняет clamp: `effectivePage = max(0, min(lastPageIndex, pageCount - 1))`. Редактор открывается на последней доступной странице без ошибки (BL-1, шаг 9). |

---

## Error Handling

| Сценарий ошибки | Обработка | Видимое пользователю |
|-----------------|-----------|---------------------|
| Ошибка flush истории (`HistoryFlushException`) | Редактор открывается; `errorEvent = HistoryFlushFailed` | Ненавязчивое уведомление (snackbar): «Файл открыт, но не добавлен в историю недавних» |
| Файл не найден (`NOT_FOUND`) | `availabilityStatus` обновляется; навигация отменяется; **запись сохраняется в списке — пользователь может повторить попытку позже** | Сообщение: «Файл не найден» |
| Ошибка файла (`FILE_ERROR`) | `availabilityStatus` обновляется; навигация отменяется; **запись сохраняется в списке — пользователь может повторить попытку позже** | Сообщение: «Файл недоступен или повреждён» |
| OOM при генерации миниатюры | Исключение перехвачено в `PdfThumbnailGenerator`; `ThumbnailState.Error` | Плейсхолдер ошибки (визуально отличный от Loading) |
| Ошибка рендеринга миниатюры (повреждён PDF) | Аналогично OOM | Плейсхолдер ошибки |
| SAF `SecurityException` при открытии | `FILE_ERROR` + сообщение | Сообщение: «Нет разрешения на доступ к файлу» |
| Ошибка чтения истории из хранилища | `FileHistoryRepository.getAll()` возвращает пустой список; не бросает исключение | Показывается пустое состояние (как при первом запуске) |
| Ошибка `rollbackUpsert` (CC-6) | Выбрасывает `HistoryFlushException`; ViewModel перехватывает, логирует категорию; навигация всё равно отменяется | Уведомление snackbar «Не удалось сохранить изменения истории» |
| I/O-ошибка `updateStatus` | Выбрасывает `HistoryFlushException`; ViewModel перехватывает, логирует категорию без URI; `errorEvent = HistoryFlushFailed` | Ненавязчивое уведомление (snackbar). In-memory статус уже обновлён; сдвиг с on-disk явно сигнализируется, а не скрывается. |
| Таймаут `getAll()` (> 5 с) | ViewModel завершает `isLoading` в `finally`; возвращает пустой список | Показывается пустое состояние; бесконечного спиннера нет |
| Таймаут `check(uri)` в CheckAvailabilityUseCase (> 2 с) | UseCase эмитирует `AvailabilityUpdate(id, FILE_ERROR)` для данной записи; продолжает обработку остальных | Запись помечается плейсхолдером `FILE_ERROR` |
| Android переустановка (backup отключён) | История не восстанавливается; показывается пустое состояние | Пустой экран с подсказкой открыть файл |
| Desktop: in-process Mutex таймаут (> 3 с) | Захват FileLock не производится; операция `upsert` завершается `HistoryFlushException` | Snackbar «Файл открыт, но не добавлен в историю недавних»; редактор всё равно открывается (AC-18) |
| Лимит папок превышен (`FolderLimitExceededException`) | Папка не создаётся; `errorEvent = FolderLimitExceeded` | Ненавязчивое уведомление (snackbar): «Достигнут лимит папок (100)» |
| Имя папки пустое (`FolderNameInvalidException`) | Недостижимо при корректном UI; при получении — папка не создаётся, диалог закрывается | Внутренняя ошибка; snackbar «Не удалось создать папку» |
| Имя папки слишком длинное (`FolderNameTooLongException`) | Папка не создаётся; диалог закрывается | Snackbar «Имя папки превышает допустимую длину» |
| Файл не в истории (`FileNotInHistoryException`) | Ссылка не создаётся; `errorEvent = FolderOperationFailed` | Snackbar «Файл не найден в истории» |
| Прочая ошибка операции с папкой | `errorEvent = FolderOperationFailed` | Snackbar «Не удалось выполнить операцию с папкой» |
| Недопустимые символы в имени папки (`FolderNameCharsInvalidException`) | Недостижимо при корректном UI (whitelist `[\p{L}\p{N}\-_]` фильтрует при вводе/paste, AC-48); при получении — папка не создаётся/переименовывается; `errorEvent = FolderNameCharsInvalid` | Snackbar «Имя содержит недопустимые символы» |
| Файл вытеснен из истории и недоступен при открытии из папки (`ARCHIVED_UNAVAILABLE`, AC-50) | checkSync вернул NOT_FOUND или FILE_ERROR; статус FolderFileLink → ARCHIVED_UNAVAILABLE; BL-1 применяется (сообщение пользователю); ссылка остаётся в папке | Сообщение: «Файл не найден или недоступен» |
| Ошибка переименования папки (`FolderNotFoundException`) | Папка могла быть удалена параллельно; список `folders` обновляется; `errorEvent = FolderOperationFailed` | Snackbar «Не удалось выполнить операцию с папкой» |
| Ошибка `updateLastPage` при выходе из редактора (`HistoryFlushException`) | Ошибка логируется (только категория, без URI); редактор продолжает закрытие. Позиция страницы не сохранена — некритичная потеря (AC-57, BL-14) | Пользователю ничего не показывается |

---

## Security Considerations

- **Аутентификация:** не требуется. Приложение работает без пользовательских учётных записей.
- **URI безопасность (Android SAF):** приложение хранит `content://` URI, полученные через SAF.
  `ContentResolver.takePersistableUriPermission` вызывается **немедленно после получения URI**
  из `FilePicker.pickPdfFile()` — до любой бизнес-логики (до `AddToHistoryUseCase`, до нормализации).
  Ответственность: Android-actual реализация `FilePicker` вызывает `takePersistableUriPermission`
  внутри себя перед возвратом URI вызывающему коду. Это гарантирует, что URI уже имеет
  постоянные права в момент попадания в порт `AddToHistoryUseCase`.
  Порты `FileHistoryRepository` и `FileAvailabilityChecker` НЕ вызывают `takePersistableUriPermission` —
  на момент их вызова права уже взяты.
  При потере разрешения — `SecurityException` перехватывается, запись помечается `FILE_ERROR` (CC-12).
- **Чувствительные данные:** история файлов хранится в приватном хранилище приложения. Имена файлов и пути считаются потенциально чувствительными данными пользователя — не логируются (в лог попадают только категории ошибок без путей/URI).
- **Проверка входных данных:** URI и пути, полученные от платформенных диалогов и из хранилища, не исполняются — используются только для открытия файлов через системные API.
- **Android backup (OQ-4, закрыт):** `android:allowBackup` должен быть **отключён для файла истории** (`FileHistoryRepository` data-файл исключается через `android:fullBackupContent` / `dataExtractionRules`). После переустановки SAF URI теряют права, а восстановленная история содержала бы только `FILE_ERROR`-записи, что вводит пользователя в заблуждение. Решение: история не включается в backup; при первом запуске после переустановки пользователь видит пустое состояние. Реализация ДОЛЖНА добавить соответствующий XML-дескриптор исключения при настройке `FileHistoryRepository` на Android.

---

## Dependencies

### Внутренние

| Зависимость | Модуль | Описание |
|-------------|--------|----------|
| `FolderRepository` порт + реализация | `:shared` (порт), платформенный инфра-модуль (impl) | Персистирование `Folder` и `FolderFileLink` |
| Существующий PDF-движок рендеринга | `:app:byCompose:common` | `PdfThumbnailGenerator` использует тот же механизм рендеринга, что и редактор |
| Compose Multiplatform Navigation | `:app:byCompose:common` | Навигация между главным экраном и редактором |
| Coroutines / Flow | `:shared`, `:app:byCompose:common` | Асинхронные операции, `Flow<AvailabilityUpdate>` |

### Внешние (требуют lookup при реализации)

| Зависимость | Платформа | Назначение |
|-------------|-----------|-----------|
| DataStore Preferences / Room | Android | Реализация `FileHistoryRepository` и `FolderRepository` |
| SQLite / FileIO | Desktop | Реализация `FileHistoryRepository` и `FolderRepository` |
| `ActivityResultContracts.OpenDocument` | Android | `FilePicker` actual |
| JVM File Dialog (AWT FileDialog / JFileChooser) | Desktop | `FilePicker` actual |

### Дизайн

- **@Designer** — UI/UX главного экрана (компоненты, цвета, отступы, пустое состояние) разрабатывается отдельно и является входом для реализации вёрстки.

---

## Implementation Notes

### Многооконность Desktop

Desktop-реализация `FileHistoryRepository` использует **двухуровневое взаимное исключение** для атомарных операций `upsert` при нескольких окнах (AC-19, AC-20, CC-10):

**Уровень 1 — in-process Mutex (kotlinx.coroutines.sync.Mutex):**
JVM `FileLock` блокирует только между процессами и **не блокирует потоки внутри одного JVM-процесса**.
Поэтому все операции `upsert` / `updateStatus` / `rollbackUpsert` внутри одного процесса
(несколько окон в одном JVM) сериализуются через единый `Mutex`, объявленный как синглтон
на уровне инфраструктурного слоя Desktop-реализации.

**Уровень 2 — JVM FileLock (межпроцессная блокировка):**
После захвата in-process `Mutex` реализация захватывает `FileLock` на файл истории.
Это защищает от параллельного доступа нескольких независимых JVM-процессов
(запуск нескольких экземпляров приложения).

**Порядок захвата** (всегда): in-process Mutex → FileLock.
**Порядок освобождения** (всегда): FileLock → in-process Mutex.
Нарушение порядка приводит к дедлоку — реализация не должна его нарушать.

Тай-брейк при неразличимых временных метках (разница < 1 мс, AC-20): победитель определяется детерминированным лексикографическим сравнением идентификаторов сессий окон. Идентификатор сессии окна (window session ID) — UUID v4, генерируется при создании окна и не персистируется.

**Stale lock при краше процесса-владельца (CC-10):**
Файловая блокировка Desktop ДОЛЖНА поддерживать защиту от stale lock:

1. Реализация использует advisory lock с явным **таймаутом ожидания — 3 секунды**.
2. Если блокировка не освобождена за 3 секунды (владелец завис или упал без очистки):
   реализация принудительно захватывает блокировку (steal/force) и продолжает операцию.
3. Дедлок невозможен, поскольку каждый поток удерживает не более одной блокировки одновременно
   (операции `upsert` / `updateStatus` / `rollbackUpsert` не вложены).
4. Таймаут выбора steal/force — архитектурное решение реализации; спецификация устанавливает
   только внешний контракт (≤ 3 с до освобождения или принудительного захвата).

### Адаптивная вёрстка

Breakpoint — **600 dp** ширины контейнера главного экрана.
- `width < 600 dp` → `LazyColumn` (одна колонка), AC-15.
- `width ≥ 600 dp` → `LazyVerticalGrid` (несколько колонок; количество колонок определяется @Designer), AC-16.
- При смене ориентации на Android (CC-20): позиция прокрутки сохраняется через `rememberLazyListState` / `rememberLazyGridState`; уже сгенерированные миниатюры не перегенерируются (берутся из кеша).

### Параллельность генерации миниатюр

Concurrency limit: не более **4 параллельных** задач генерации (CC-13).
Реализуется через `Semaphore(4)` в корутинах.
Очередь генерации приоритизирует видимые элементы.

**Таймаут ожидания Semaphore и отмена при скролле:**
- Корутина генерации ДОЛЖНА ожидать слот семафора не более **10 секунд**.
  При превышении таймаута задача завершается, элемент получает `ThumbnailState.Error`.
- При уходе элемента за пределы видимой области (скролл) соответствующая корутина
  генерации ОТМЕНЯЕТСЯ (`Job.cancel()`), в том числе если она ждёт слот семафора.
  Это освобождает слот для видимых элементов и не приводит к утечке корутин.
- Отмена не оставляет запись в `ThumbnailState.Error`: при повторном появлении
  элемента в области видимости ViewModel заново запускает генерацию с нуля.

### Кеш миниатюр: размер

20 записей × 2.5 МБ ≈ 50 МБ. Лимит соответствует типичному использованию (CC-14).
Уменьшение лимита ниже 10 МБ не рекомендуется: приведёт к постоянным кеш-промахам.

### Known Limitations

- **CC-16 (Medium):** если файловая система не обновляет `mtime` при изменении файла (FAT32, некоторые сетевые FS) — кеш миниатюры не инвалидируется, показывается устаревшее превью. Дополнительная проверка по размеру файла рассматривается как улучшение в будущем.
- **CC-21 (Medium):** после переустановки приложения на Android все `content://` URI в истории становятся невалидными (SAF права сбрасываются). Пользователь видит список с записями в статусе `FILE_ERROR`; может открыть файлы повторно через диалог.
- **OQ-2 (закрыт):** поле `lastPageIndex: Int` (0-based) добавлено в доменную модель `RecentFile`. Сохраняется при каждом `upsert` (AC-18). При открытии файла из истории редактор получает `lastPageIndex` и открывает PDF на сохранённой странице. При первом открытии — значение 0. `FileHistoryRepository.upsert()` принимает параметр `lastPageIndex: Int = 0`. `RecentFileUiModel` содержит поле `lastPageIndex: Int` для отображения подсказки «стр. N» в превью (финальное решение по отображению — на усмотрение @Designer).
- **OQ-4 (закрыт):** история файлов исключена из Android backup. После переустановки пользователь видит пустое состояние — намеренное поведение (см. Security Considerations).
- **OQ-6 (открыт):** пустое состояние содержит только текстовую подсказку; call-to-action кнопка внутри блока пустого состояния вынесена за скоуп. Финальное решение — за PO/@Designer.
