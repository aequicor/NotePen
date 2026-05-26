# Изоляция ресурсов параллельных worktree-сессий

Подробная схема к скилу **parallel-sessions**. Цель: дать каждой сессии (= одному git worktree)
непересекающийся «лейн» исполнительных ресурсов.

## Что изолировано, а что общее

`claude --worktree <имя>` создаёт рабочий каталог `.claude/worktrees/<имя>` на ветке `worktree-<имя>`.

- **Изолировано самим worktree:** дерево файлов и каталог `build/`. Поэтому JVM-юнит-тесты и
  снапшот-тесты (см. скил **snapshot-testing**) разных сессий идут параллельно без коллизий — каждый
  пишет в свой `build/`.
- **Общее на машине, но безопасное:** `~/.gradle/caches` и `~/.konan`. Кэши контент-адресуемые;
  параллельное чтение/запись штатны. `~/.konan` переиспользуется между worktree при совпадении версии
  Kotlin/Native (артефакты разложены по версиям). Не чисти их вручную.
- **Общее и конфликтное (нужен лейн):** запущенные эмуляторы и симуляторы, ADB-серийники, порты
  dev-серверов/WASM, `applicationId` установленного на устройстве приложения, имена AVD/симуляторов.

## Формулы лейна

`WORKTREE_ID` — стабильное число `1..50`, выведенное из имени каталога worktree (детерминировано,
без файла-счётчика). Остальное считается от него:

| Переменная | Формула | Назначение |
|---|---|---|
| `WORKTREE_ID` | `(cksum(имя) % 50) + 1` | базовый id лейна |
| `ANDROID_EMULATOR_PORT` | `5554 + ID*2` | консольный порт эмулятора (чётный, в диапазоне 5554–5682) |
| `ANDROID_SERIAL` | `emulator-<port>` | adb/gradle бьют сюда автоматически, когда переменная экспортирована |
| `SERVER_PORT` | `8080 + ID` | порт Ktor/dev-сервера |
| `WASM_PORT` | `9090 + ID` | webpack-dev-server для wasmJs/js |
| `COMPOSE_DEV_PORT` | `9190 + ID` | Compose Hot Reload / desktop dev |
| `APP_ID_SUFFIX` | `.wt<ID>` | суффикс `applicationId`, чтобы ветки уживались на устройстве |
| `AVD_NAME` / `IOS_SIM_NAME` | `wt-<имя>` | имя AVD / iOS-симулятора лейна |

Коллизии id возможны (хэш по модулю 50), но редки. Чтобы зафиксировать значение вручную — экспортируй
`WORKTREE_ID` до загрузки лейна, формулы подхватят его.

## Раздача лейна по ОС

### direnv (macOS / Linux / git-bash) — рекомендуется

Шаблон `templates/envrc.sh` → положи как `.envrc` в корень репозитория. Секретов в нём нет, поэтому
**закоммить** его: git донесёт `.envrc` до каждого нового worktree сам, и он вычислит свой лейн из
имени каталога. В каждом новом worktree один раз выполни `direnv allow` (требование безопасности
direnv — он не загружает неподтверждённый `.envrc`).

Если предпочитаешь не коммитить `.envrc` — добавь его строкой в `.worktreeinclude`, тогда Claude Code
скопирует его в новые worktree.

### Без direnv: sourced-файл (bash/zsh)

Тот же `templates/envrc.sh`, но подключаемый вручную: `source ./.envrc` в начале работы в worktree
(или в `.bashrc`-хуке проекта). `export`-строки сработают и без direnv.

### Windows / PowerShell

`cksum` нет в PowerShell — выведи id детерминированным хэшем. Стартовый `.worktree-env.ps1`,
запускаемый через `. .\.worktree-env.ps1`:

```powershell
$slug  = Split-Path -Leaf (Get-Location)
$bytes = [Text.Encoding]::UTF8.GetBytes($slug)
$hash  = [Security.Cryptography.MD5]::Create().ComputeHash($bytes)
$id    = ([int]([BitConverter]::ToUInt32($hash,0) % 50)) + 1
$env:WORKTREE_ID         = $id
$env:ANDROID_SERIAL      = "emulator-$((5554 + $id*2))"
$env:SERVER_PORT         = (8080 + $id)
$env:WASM_PORT           = (9090 + $id)
$env:APP_ID_SUFFIX       = ".wt$id"
$env:IOS_SIM_NAME        = "wt-$slug"
```

Метод хэша другой, чем в `cksum`, поэтому id будет другим — это не важно: важна уникальность между
worktree, а не совпадение bash↔PowerShell. Выбери один способ на проект.

## Создание AVD / iOS-симулятора лейна

Имена детерминированы (`AVD_NAME` / `IOS_SIM_NAME`), создаются один раз:

```bash
# Android AVD под лейн
avdmanager create avd -n "$AVD_NAME" -k "system-images;android-34;google_apis;arm64-v8a" -f
emulator -avd "$AVD_NAME" -port "$ANDROID_EMULATOR_PORT" &

# iOS-симулятор (только macOS) — UDID сохрани в лейн
udid=$(xcrun simctl create "$IOS_SIM_NAME" "iPhone 16")
export IOS_SIM_UDID="$udid"
xcrun simctl boot "$IOS_SIM_UDID"
```

iOS-цели существуют только на macOS — на Windows/Linux эту часть пропусти.

## .worktreeinclude

Claude Code при `claude --worktree` копирует в новый worktree файлы, перечисленные в корневом
`.worktreeinclude` (по одному пути/глобу на строку). Это единственный способ донести **gitignored**
файлы, которые нужны сборке. Минимум для KMP/Android — `local.properties` (путь к Android SDK).
Шаблон — `templates/worktreeinclude`. Закоммиченные файлы (включая `.envrc`, если ты его коммитишь)
сюда добавлять не нужно — их и так несёт git.

## applicationIdSuffix

`templates/applicationIdSuffix.gradle.kts` берёт `APP_ID_SUFFIX` из окружения и вешает его на
`applicationId` и `versionName`. В основном чекауте переменная пуста — суффикса нет; в worktree-лейне
она `.wt<ID>`, и `adb install` ставит ветку рядом с основной сборкой, не перетирая её. Блок — в
`android { defaultConfig { … } }` модуля-приложения (для KMP Android Library DSL отличается — там
суффикс не применяется, библиотеке он не нужен).
