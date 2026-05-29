# Задача: реализовать универсальную абстракцию библиотеки (новый модуль :library) в NotePen

> Этот файл — самодостаточный бриф для агента в отдельной сессии. Скопируй его целиком (или сошлись на него) при старте новой сессии. Слово «workflow» в тексте включает оркестрацию.

Ты работаешь в репозитории NotePen — Kotlin Multiplatform приложение для аннотирования PDF/документов (Android + Desktop/JVM), package root `ru.kyamshanov.notepen`. Прочитай `CLAUDE.md` в корне — там команды, карта модулей и конвенции. Используй **Workflow** для оркестрации (исследование, параллельная реализация независимых частей, ревью/верификация); саму реализацию веди инкрементально по этапам M0→M6 с гейтами качества между ними. На границах этапов делай checkpoint с пользователем, не пытайся сделать всё за один проход.

## Что строим

Единый интерфейс библиотеки, скрывающий бэкенд (локальная папка / LAN-пир / GitHub / облако), с ролями Читатель/Библиотекарь, режимом синхронизации одного документа PC↔планшет и подключением к нескольким библиотекам одновременно. Кнопка «открыть свою библиотеку» + тумблер «открывать при старте».

## Зафиксированные решения (НЕ переобсуждать без причины)

1. **Идентичность = content-addressed.** `CanonicalBookId` = **`sha256` по ПОЛНОМУ содержимому файла**, считается один раз при первой локальной материализации, кэшируется (сайдкар `.notepen.id` на JVM / реестр по uri на Android). Для EPUB/FB2 — хешируем **исходный файл книги**, а НЕ сгенерированный PDF (тот недетерминирован). GitHub blob-sha используем только для обнаружения изменений, не как канонический id.
2. **Все три бэкенда в первой поставке:** Local + LAN + GitHub (Cloud/Drive/Dropbox — позже).
3. **Библиотекарь по LAN — В ОБЪЁМЕ** (требует нового сетевого протокола, см. M5).
4. **Android — только клиент** (peer + cloud); локальную папку по LAN не раздаёт (обходим сложность SAF/`content://` на хосте). Раздача по LAN — только desktop.

## Целевая структура модулей (без циклов)

- `:shared` ← добавить `DocumentIdentity` / `CanonicalBookId` + порт `DocumentIdentityProvider` (sha256 + кэш). Идентичность кладём в `:shared`, т.к. `:sync` уже зависит от него — избегаем связи `:sync → :library`.
- `:library:api` (зависит на `:shared`): `Library`, `LibraryBackend` (SPI), `LibraryRegistry`, `LibraryConnection`/`LibraryDescriptor`/`LibraryRole`/`LibraryCapabilities`, `LibraryEntry`, `LibraryBookId`, `OpenableDocument`, `NotLibrarianException`. `explicitApi()`, чистый Kotlin, без Ktor/SQLDelight/PDFBox/Android.
- `:library:impl` (зависит на `:library:api` + `:sync` + `:shared`): бэкенды + `DefaultLibraryRegistry` + персист подключений. Платформенные actuals только для filesystem-rooted локальной библиотеки.
- Зарегистрировать оба модуля в `settings.gradle.kts`, объявить зависимости в `gradle/libs.versions.toml` (type-safe `projects.*` accessors включены).

Ключевые интерфейсы (ориентир, уточни сигнатуры по месту):

```kotlin
interface Library {
    val descriptor: LibraryDescriptor
    val capabilities: LibraryCapabilities          // canAdd/canRemove/canReplace из роли
    val books: StateFlow<List<LibraryEntry>>        // обобщает LibraryFolder.items
    val connectionState: StateFlow<LibraryConnectionState>
    suspend fun refresh()
    suspend fun open(id: LibraryBookId): Result<OpenableDocument> // {localPath, identity, readOnly}
    suspend fun addBook(src: String): Result<LibraryEntry> = throw NotLibrarianException()
    suspend fun removeBook(id: LibraryBookId): Result<Unit> = throw NotLibrarianException()
    suspend fun replaceBook(id: LibraryBookId, src: String): Result<LibraryEntry> = throw NotLibrarianException()
}
interface LibraryBackend { val kind: LibraryBackendKind
    suspend fun connect(spec: LibraryConnection, scope: CoroutineScope): Result<Library>
    suspend fun probe(spec: LibraryConnection): Result<LibraryDescriptor> }
interface LibraryRegistry {
    val libraries: StateFlow<List<Library>>
    val mergedBooks: StateFlow<List<MergedLibraryEntry>>
    suspend fun connect(spec: LibraryConnection): Result<Library>
    suspend fun disconnect(id: LibraryId)
    suspend fun savedConnections(): List<LibraryConnection> }
```

## Существующий код, на который опираемся (ВЕРИФИЦИРУЙ перед правкой — мог измениться)

- **Главный экран:** `shared/.../MainComponent.kt`; `app/byCompose/common/.../mainscreen/ui/viewmodel/MainScreenViewModel.kt`, `.../ui/model/MainScreenUiState.kt`, `.../ui/screen/MainContent.kt`; порты `shared/.../mainscreen/domain/port/{FileHistoryRepository,FolderRepository,LibraryFolder,ThumbnailRepository,FileAvailabilityChecker}.kt`; `LibraryFolderItem` (в `LibraryFolder.kt`).
- **Бэкенд локальной библиотеки уже есть:** `sync/jvmMain/.../FileSystemLibraryManifestProvider.kt` + `app/byCompose/common/jvmMain/.../mainscreen/infrastructure/FileSystemLibraryFolder.kt` (порт `LibraryFolder`: `items: StateFlow`, `addCopy`).
- **Загрузка документа:** `app/.../common/.../tabs/PdfDocumentState.kt`, `TabSession.kt`, `OpenDocuments.kt`; `shared/.../pdf/domain/port/PdfDocumentLoader.kt`; `app/.../common/.../book/{EbookAwarePdfDocumentLoader,EbookToPdfConverter}.kt`; `DetailsContent.kt` (лямбда `syncDocumentIdFor` ~стр. 215–226 — здесь сейчас вычисляется documentId), `EditorPanel.kt` (`loader.load(filePath)`).
- **Sync:** `sync/.../domain/DocumentId.kt` (`documentIdFromFilePath` = `basename#FNV1a(path)` — заменяем на canonical id), `SyncEngine.kt`, `SyncEngineRegistry.kt`, `model/StrokeDelta.kt`, `model/NetworkMessage.kt`, `RemoteCatalogProvider.kt` (per-peer read allow-list, защита от path-traversal), `RemoteDocumentOpener.kt` (стримит `FileChunk`, кэширует в `receivedPdfDir`, регистрирует id), `HostAnnotationProjection.kt`, `HostHeadlessAnnotationHandler.kt`, `domain/port/LocalDocumentIdRegistry.kt` (`localPath ↔ documentId`), `domain/port/OpenDocumentRegistry.kt`; `sync/jvmMain/.../KtorPeerServer.kt`, `KtorSyncClient.kt`, `SqlDelightPendingDeltaQueue.kt`, `sqldelight/.../PendingDelta.sq`; `app/.../desktop/.../SyncRuntime.kt` (DI sync на desktop); `qr-connect/.../PairingUri.kt`, `HostQrPairingCoordinator.kt`.
- **Облако:** `sync/.../cloud/domain/CloudStorageProvider.kt` + `sync/.../cloud/infrastructure/GitHubContentsCloudProvider.kt` (device-flow OAuth, upload base64, optimistic SHA).
- **Хранилище/настройки:** `drawing/api/.../annotation/domain/port/AnnotationRepository.kt` (сайдкары ключуются по пути/uri — НЕ трогаем при миграции id); `shared/.../appsettings/domain/port/AppSettingsRepository.kt` + `JvmAppSettingsRepository.kt`/`AndroidAppSettingsRepository.kt` (сюда добавим флаг `openLibraryAtStartup`); `app/.../common/jvmMain/.../mainscreen/infrastructure/AppDataDir.kt` (`~/.notepen` / portable). Паттерн персиста: atomic temp-rename + Mutex (+ FileLock на JVM) — см. `FileHistoryRepositoryDesktop.kt`.
- **Навигация (Decompose):** `shared/.../RootComponent.kt` (sealed `Child`), `shared/.../DefaultRootComponent.kt` (sealed `Config`, `StackNavigation<Config>`, `childFactory`, инъекция фабрик-лямбд, `instanceId`), `app/.../common/.../RootContent.kt` (диспетчер UI), `shared/.../DetailsComponent.kt`/`DefaultDetailsComponent.kt`. Точки входа/DI (ручной, без контейнера): `app/byCompose/desktop/.../main.kt` и `app/byCompose/android/.../replacementPlace/MainActivity.kt` (на Android тяжёлые зависимости поднимаются в фоне через `HeavyDeps`).

**Новая Decompose-точка (`LibrarySources`) = стандартные 7 шагов:** (1) вариант `Config.LibrarySources(instanceId)`; (2) `RootComponent.Child.LibrarySourcesChild`; (3) интерфейс `LibrarySourcesComponent` в `:shared`; (4) `LibrarySourcesComponentImpl` в `:common`; (5) фабрика-лямбда в конструкторе `DefaultRootComponent`; (6) ветка в `RootContent`; (7) проводка в обеих точках входа.

## Этапы (порядок обязателен; между этапами — гейт качества и checkpoint)

- **M0 — Identity spine + скелет модулей.** `DocumentIdentity`/`CanonicalBookId` + `DocumentIdentityProvider` (sha256 полного содержимого, кэш) в `:shared`; перевести `:sync` и `DetailsContent.syncDocumentIdFor` на него; расширить `LocalDocumentIdRegistry` до `localPath → canonicalId`. Создать `:library:api`/`:impl` (пустые контракты). **Wire-формат documentId сохранить как `basename#<sha256-prefix>` (структура `NetworkMessage` не меняется).** Миграция: сайдкары аннотаций не трогаем (ключ — путь/uri); pending-дельты в SQLDelight под старым id слить/проиграть до перехода. Без изменений UI.
- **M1 — Library + LocalFolderLibraryBackend (поведение не меняется).** Адаптер над существующими `FileSystemLibraryFolder`/`FileSystemLibraryManifestProvider`. `DefaultLibraryRegistry` с одной локальной библиотекой. `MainScreenViewModel` читает `registry.mergedBooks`, UI визуально идентичен (одна секция).
- **M2 — Мультибиблиотека + PeerLanLibraryBackend + экран LibrarySources + персист + старт.** `PeerLanLibraryBackend` (читает `RemoteCatalog`, `open()`→`RemoteDocumentOpener`, роль Reader). Экран LibrarySources (7 шагов), персист `LibraryConnection` (JSON, atomic-write паттерн), кнопка «открыть свою библиотеку» (`serveOverLan=true` + `SyncRuntime.enable()`), флаг `openLibraryAtStartup` в `AppSettings` + авто-подключение `savedConnections()`. **Первый видимый релиз (local + LAN, чтение).** На Android — без локального-папка-бэкенда (registry может быть пустым, не null — убрать Android-спецслучай).
- **M3 — GitHubLibraryBackend** на `GitHubContentsCloudProvider` (чтение+кэш→затем upload), роль Librarian при write-токене.
- **M4 — Per-document live-sync.** Тонкий `LiveDocumentSyncController` поверх `SyncEngineRegistry` (пинит документ в `OpenDocumentRegistry`, помечает движок как активно вещающий) + тумблер в редакторе (`DetailsContent`/tool rail). Транспорт не меняется.
- **M5 — Librarian-по-LAN протокол (новый сетевой код, изолирован в конце).** Новые `NetworkMessage`: `LibraryAddRequest(targetLibraryId, displayName, fileSize, contentSha256, requestId)` + поток `FileChunk` **client→host** (сейчас только host→client — сделать двунаправленным), `LibraryRemoveRequest`, `LibraryReplaceRequest`, `LibraryMutationResult(requestId, Result)`. В `RemoteCatalogProvider` — per-peer **write allow-set** (`librarianPeerIds`), персист как `approvedPeerIds`. В паринге (`HostQrPairingCoordinator`/диалог одобрения) — выбор Reader/Librarian. Новый `HostLibraryMutationHandler` применяет мутацию к локальной `Library` хоста; каталог-StateFlow обновляется → пиры видят реактивно.
- **M6 — Закалка.** Дедуп книг между библиотеками по `CanonicalBookId`; eviction-политика кэша скачанных PDF; ре-sync по изменению GitHub blob-sha.

## Конвенции и гейты качества (соблюдать строго)

- Слои внутри модуля: `domain/{model,port,usecase,exception}` (чистый Kotlin, без Android/Ktor/SQLDelight/диспетчеров) ← `infrastructure/` + `ui`/`presentation`. Платформенные различия — только expect/actual, без `if(platform)`. Инжектируй `CoroutineDispatcher`/`CoroutineScope`, не используй `Dispatchers.*`/`GlobalScope`. `:*:api` — `explicitApi()` + KDoc.
- После каждого этапа: `./gradlew build` → `./gradlew test` (и `:library:jvmTest` для нового модуля) → `./gradlew detekt` → `./gradlew ktlintCheck`. Перед сдачей этапа — `./gradlew check`.
- detekt на легаси-находках не из твоего кода: пересоздай baseline модуля `./gradlew :<module>:detektBaseline`, не рефактори вокруг и не глуши `@Suppress`.
- ktlint автоформат: `./gradlew ktlintFormat` или один файл `./gradlew ktlintFormatFile -PktlintFile=<abs>`. **Важно:** PostToolUse ktlint-хук удаляет временно-неиспользуемые импорты — добавляй импорт вместе с его использованием в одной правке.
- Configuration cache включён — build logic должна оставаться совместимой. JDK: библиотеки/Android — JVM 11, desktop — JBR 21. Зависимости только через `gradle/libs.versions.toml`.
- **Рабочее дерево пользователя:** возможны незакоммиченные изменения. В начале проверь `git status`, согласуй scope коммитов, работай в отдельной ветке/worktree. Коммить только по запросу. Сообщение коммита заканчивай строкой `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

## Как применять Workflow

- **Старт:** запусти workflow-исследование, чтобы освежить карту кода (модули `mainscreen`, `tabs`, `sync`, `cloud`, навигация) и подтвердить, что упомянутые типы/файлы на месте.
- **Реализация:** ведущий агент драйвит последовательные зависимости (api до impl, M0 до бэкендов). Параллель через workflow — там, где безопасно: реализация трёх бэкендов после заморозки `:library:api` (изолируй в worktree, если правят общие файлы), и фан-аут ревью/верификации после каждого этапа.
- **Верификация:** после каждого этапа — adversarial-ревью диффа (корректность, нет ли циклов зависимостей, соблюдены ли слои) + прогон `./gradlew check`. Для ручной проверки видимых этапов (M2+) есть desktop MCP `notepen-desktop` и Android-хелпер (см. `.claude/`).

## Открытые подзадачи — реши по месту, при сомнении спроси пользователя

- Точная схема персиста `LibraryConnection` и формат `LibrarySourceConfig`.
- Где именно кэшировать `CanonicalBookId` на Android (реестр по `sha256(uri)`, как сайдкары аннотаций).
- UX выбора роли при паринге и индикатор Reader/Librarian на полке.

Начни с `git status`, чтения `CLAUDE.md` и `settings.gradle.kts`, затем workflow-исследования и M0. Останавливайся на checkpoint в конце каждого этапа.
