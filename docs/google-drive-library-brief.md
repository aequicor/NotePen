# Бумажка для согласования: облачная библиотека Google (Drive)

> Статус: **СОГЛАСОВАНО (2026-05-30)**. Зафиксированные решения:
> 1. **Google Drive** (не GCS).
> 2. **Reader тоже проходит OAuth** (`drive.readonly`), встроенного API-key нет → OAuth-инфраструктура переезжает в **D0** (а не D1).
> 3. **Новый порт `DriveLikeStore`** в `:library` (общий `CloudStorageProvider` не трогаем).
> Пересмотренный план этапов — см. раздел 5.
> Контекст: расширяем существующую абстракцию `:library` (`Library`/`LibraryBackend`/`LibraryRegistry`, роли `Reader`/`Librarian`) новым облачным бэкендом. Слот `LibraryBackendKind.Cloud` и `LibraryConnection.Cloud` уже зарезервированы в API, но реализации нет. Образец для подражания — `GitHubLibraryBackend` (репозиторий-как-полка).

## 1. Что значит «Google cloud» здесь

Трактуем как **Google Drive** (потребительский, OAuth), а НЕ Google Cloud Storage (GCS-бакеты).

Почему Drive, а не GCS:
- Drive ложится на ту же модель, что и GitHub-бэкенд: «общая папка = книжная полка», вход через OAuth, без серверной инфраструктуры — совпадает с no-cloud-account идеологией приложения.
- GCS требует GCP-проект, биллинг, сервис-аккаунты/HMAC-ключи и IAM-модель, у которой нет аналога в текущем UI и системе ролей.

**Это главное решение для согласования** (см. раздел 6, вопрос 1). Всё ниже расписано под Drive.

## 2. Модель: «папка Drive = полка», роли

Одна **папка Google Drive** играет роль книжной полки (как каталог `books/` у GitHub-бэкенда). Каждый файл в папке → одна `LibraryEntry` (`libraryBookId` = Drive `fileId`, `displayName` = имя файла в Drive).

**Разделение ролей** (как у GitHub — роль выводится из учётных данных подключения):

| Роль | Кто это | Что может | Откуда берётся доступ |
|---|---|---|---|
| **Reader (Читатель)** | подключился к расшаренной папке | `refresh`, `open` (скачать+читать). Мутации бросают `NotLibrarianException` (дефолт `Library`) | read-доступ к папке (см. вопрос 2 — API-key или OAuth read-scope) |
| **Librarian (Библиотекарь)** | владелец/редактор папки, прошёл OAuth | то же + `addBook` (загрузка), `replaceBook` (обновление). `removeBook` — в D2 (Drive `files.delete` это умеет) | OAuth-токен с write-scope + право редактирования папки |

`LibraryCapabilities.fromRole(role)` уже даёт нужную матрицу прав — переиспользуем без изменений. Бейдж роли на полке (`AssistChip` «Читатель»/«Библиотекарь») уже отрисовывается — новый бэкенд просто проставляет `descriptor.role`.

### Как работает ЧТЕНИЕ книги (любая роль)
1. `refresh()` → `DriveStore.listChildren(folderId)` (Drive `files.list?q='<folderId>' in parents and trashed=false`) → `books` обновляется.
2. Пользователь открывает книгу → `open(libraryBookId)`: `DriveStore.download(fileId)` (Drive `files.get?alt=media`) → байты кэшируются на диск → `OpenableDocument(localPath, identity=null, readOnly = role==Reader)`. `identity` (CanonicalBookId) считается при локальной материализации, как и у GitHub.

### Как работает ДОБАВЛЕНИЕ книги (только Librarian)
1. `addBook(srcPath)`: `requireLibrarian()` → читаем локальный файл → `DriveStore.create(folderId, name, bytes)` (Drive `files.create`, multipart) → возвращается **новый** `fileId` → новая `LibraryEntry`, `books` обновляется.
2. `replaceBook(id, srcPath)`: `DriveStore.update(fileId, bytes, previousVersion)` (Drive `files.update` + `If-Match`/ETag для оптимистичной блокировки).
3. Reader, вызвавший мутацию, получает `NotLibrarianException` (поведение по умолчанию интерфейса).

## 3. Три проблемы, которые ломают «наивный» дизайн (по итогам adversarial-ревью)

Прямое переиспользование `CloudStorageProvider` (как у GitHub) НЕ работает. Ниже — что меняем.

### 3.1. (HIGH) Drive адресуется по fileId, а не по пути
`CloudStorageProvider` спроектирован вокруг пути: `upload(path, …)` = create-or-update по собранному вызывающим пути (`"books/$name"`), `list(directoryPath)` — по относительному пути. У Drive:
- `create` возвращает **новый opaque `fileId`**, заранее неизвестный → нельзя «собрать путь» для нового файла;
- `update` требует существующий `fileId`;
- листинг — это запрос по `parentFolderId`, понятия «путь» нет.

Подсунуть `fileId` в `CloudFile.path` недостаточно: ломается и `addBook` (адресует ещё не созданный файл), и семантика `list`. Добавления опционального имени в `CloudFile` хватает только на отображение, не на адресацию.

**Решение (рекомендуется):** НЕ трогаем `CloudStorageProvider` (его делит с нами sync-движок — лишний blast radius). Вводим **отдельный id-адресный порт** в `:library` для Drive-подобных хранилищ:

```kotlin
// :library:api (domain, чистый Kotlin, explicitApi)
public data class RemoteFile(
    public val id: String,          // Drive fileId — стабильная идентичность
    public val name: String,        // отображаемое имя (title), отдельно от id
    public val sizeBytes: Long,
    public val version: String,     // ETag/headRevisionId — токен оптимистичной блокировки и смены контента
)
public interface DriveLikeStore {
    public suspend fun listChildren(folderId: String): List<RemoteFile>
    public suspend fun download(fileId: String): ByteArray
    public suspend fun create(parentFolderId: String, name: String, bytes: ByteArray): RemoteFile
    public suspend fun update(fileId: String, bytes: ByteArray, previousVersion: String): RemoteFile
    public suspend fun delete(fileId: String) // D2 — включает removeBook
}
```

Этот порт чисто покрывает add/open/replace/remove/refresh. GitHub остаётся на своём `CloudStorageProvider` — ничего не ломаем. (Альтернатива — переписать `CloudStorageProvider` в id-адресный и адаптировать GitHub: чище концептуально, но затрагивает `:sync`; см. вопрос 3.)

### 3.2. (HIGH) Нет цикла обновления OAuth-токена
Access-токен Google живёт ~1 час. Сейчас `DeviceTokenResult.Authorized` несёт только `accessToken`, а провайдер строится один раз при `connect` с фиксированным bearer. → Сессия Библиотекаря умрёт через час прямо посреди работы.

**Решение (объём — отдельный этап D1, это net-new инфраструктура, НЕ «переиспользование»):**
- Расширяем `DeviceTokenResult.Authorized` опциональными `refreshToken: String? = null`, `expiresInSeconds: Int? = null` (бэк-совместимо с GitHub).
- Новый порт `AccessTokenSource { suspend fun bearer(): String }` инжектится в `GoogleDriveStore`; он отдаёт свежий токен, при истечении — рефрешит по refresh-токену через token-endpoint Google. Плюс ретрай на `401`.
- `refreshToken` персистится в `LibraryConnection.Cloud` (новое опциональное поле).

### 3.3. (HIGH) Reader на Drive не может быть анонимным
GitHub-приём «пустой токен → без заголовка Authorization → анонимное чтение публичного репо» у Drive v3 аналога НЕ имеет: `files.list`/`files.get` требуют минимум **API-key** (даже для «доступ по ссылке»). → «пустые креды = Reader» ломает этап «сначала только чтение».

**Решение:** Reader = «API-key (или OAuth read-scope) поверх расшаренного `folderId`», а не «без credential». Нужно решить, где живёт ключ (см. вопрос 2).

### Прочее (MED/LOW, учтено в плане)
- **client_secret:** Google device-flow требует `client_secret` на обмене токена (у GitHub — только `client_id`). Пишем **отдельный** `GoogleDeviceAuthenticator` (не перегружаем GitHub-овский); installed-app secret Google трактует как не-конфиденциальный. На desktop возможна альтернатива loopback+PKCE без secret (см. вопрос 4).
- **Секрет в plaintext:** refresh-токен Drive — долгоживущий и ценнее scoped-PAT. Прецедент есть (GitHub-токен уже в plaintext `library_connections.json`), т.е. *консистентно*, но фиксируем как осознанный долг; минимизируем урон узким scope (`drive.file`, см. вопрос 5).
- **Токен версии ≠ контент-хэш:** для Drive `version` — это ETag/ревизия, а НЕ md5 (md5 у Google-native/больших файлов отсутствует). Используем ETag/`If-Match`; не копируем в Drive-библиотеку content-equality допущения GitHub.
- **`GitHubDeviceAuthenticator` сейчас нигде не подключён** (живой GitHub-флоу просто вставляет токен в `addGitHubLibrary(repo, token)`). Значит OAuth-UI (показ user-code, открытие verificationUri, цикл поллинга с interval/slow_down/backoff/timeout) — работа с нуля, бюджетируем в D1.
- **Eviction кэша:** добавить `driveCacheDir` (desktop: `getAppDataDir()`, Android: `context.cacheDir`) и дописать его в `downloadCacheDirs` (main.kt:323) и Android-аналог, иначе кэш растёт безгранично.

## 4. Затрагиваемые модули и точки интеграции

- `:library:api` — `DriveLikeStore` + `RemoteFile` (новый id-адресный порт).
- `:library:impl` — `GoogleDriveLibrary` (реализация `Library` на `DriveLikeStore`) + `GoogleDriveLibraryBackend` (`kind = Cloud`, читает `LibraryConnection.Cloud`, строит дескриптор/роль). Образец — `GitHubLibrary`/`GitHubLibraryBackend`.
- `:library:api/LibraryConnection.Cloud` — расширить опциональными полями (`folderId` через `accountId`, `refreshToken: String? = null`, `scope: String? = null`), defaults для бэк-совместимости.
- `:sync` (`cloud/infrastructure`) — `GoogleDriveStore` (Ktor → Drive v3 REST) + `GoogleDeviceAuthenticator` + `AccessTokenSource`. Слой `:library:impl → :sync` уже существует — нового цикла зависимостей НЕТ.
- `:app:byCompose:common` — UI добавления (диалог Drive в `AddLibraryMenu`/`LibrarySourcesContent` + поля `LibrarySourcesUiState` + интенты `LibrarySourcesViewModel`); OAuth device-code экран (D1).
- DI: desktop `main.kt` (в список `backends` `DefaultLibraryRegistry` + ленивый `HttpClient` + `driveCacheDir` + `CacheEvictor`); Android `MainActivity.kt` (тот же список, под `HeavyDeps`).

## 5. План по этапам (с гейтами качества и checkpoint между ними)

- **D0 — Чтение (Reader) + OAuth.** Т.к. чтение Drive требует OAuth (решение 2), OAuth-инфраструктура входит в D0: `DriveLikeStore`/`RemoteFile` (`:library:api`) + `GoogleDriveStore` (read: list/download), `GoogleDeviceAuthenticator`, `AccessTokenSource` (refresh + 401-retry), `GoogleOAuthConfig` (`:sync`) + `GoogleDriveLibrary`/`Backend` (`:library:impl`) + DI (desktop+Android) + кэш/eviction + UI добавления (folderId + device-code OAuth, scope `drive.readonly`). **Первый видимый результат: вошли через Google и читаем расшаренную папку.** Мутации — дефолтный `NotLibrarianException`.
- **D1 — Запись (Librarian).** scope `drive.file`, `create`/`update`, `addBook`/`replaceBook`, выбор роли при добавлении.
- **D2 — Закалка.** `delete`/`removeBook`, дедуп по `CanonicalBookId` между библиотеками, hardening кэша/токенов.

> **Внешняя зависимость:** для реального end-to-end OAuth нужен Google OAuth client (Client ID + secret), который заводится в Google Cloud Console владельцем приложения. Код пишется с инъекцией `GoogleOAuthConfig`; реальные значения подставляются через конфиг/`BuildConfig`/env, не хардкодятся. Без них собирается и тестируется, но живой вход требует выданных кредов.

После каждого этапа: `./gradlew build → test (+ :library:jvmTest, :sync:jvmTest) → detekt → ktlintCheck`; перед сдачей этапа `./gradlew check`. Слои `domain/{model,port,usecase,exception}` чистые; платформенные различия только expect/actual; инжектим `CoroutineDispatcher`/`CoroutineScope`.

## 6. Открытые решения (нужны для старта)

1. **Drive или GCS?** Рекомендую **Google Drive**. (Если реально нужен GCS-бакет — дизайн другой, переписываю.)
2. **Как Reader получает доступ на чтение?** (a) встроенный ограниченный **Drive API-key** в бинарь для чтения «по ссылке» (просто, но ключ распространяется); (b) **OAuth read-scope** даже для чтения (нет ключа в бинаре, но Reader тоже проходит OAuth — тяжелее UX, и OAuth уезжает в D0). Рекомендую (a) для D0.
3. **SPI:** отдельный новый `DriveLikeStore` в `:library` (рекомендую, малый blast radius) **или** переписать общий `CloudStorageProvider` в id-адресный и адаптировать GitHub (чище, но трогает `:sync`)?
4. **client_secret на desktop:** device-flow со встроенным installed-app secret (единый код desktop+Android) **или** loopback+PKCE без secret на desktop?
5. **OAuth scope:** `drive.file` (только файлы, созданные приложением — минимальный урон, но Reader не увидит чужой контент папки) **или** `drive`/`drive.readonly` (видно всю расшаренную папку, шире права)? Рекомендую: read через API-key/`drive.readonly`, запись Библиотекаря — `drive.file`.

---
После согласования (минимум — вопросы 1–3) приступаю к D0, затем создаю MR.
