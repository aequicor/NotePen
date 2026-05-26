---
name: parallel-sessions
description: How to run NotePen across several parallel Claude Code sessions without collisions — create a git worktree per session (claude --worktree), give each session its own device/port/applicationId lane, copy gitignored files into new worktrees via .worktreeinclude, and tune gradle.properties for N concurrent daemons. Use when starting parallel work, when a fresh worktree needs its resource lane, when emulators/ports/installs collide between sessions, or when concurrent builds thrash RAM.
---

# parallel-sessions

Готовит **NotePen** к одновременной работе нескольких сессий Claude Code. Каждая
сессия живёт в своём git worktree; цель скила — чтобы сессии не дрались за эмуляторы, симуляторы,
порты, `applicationId` и RAM.

## Когда применять

- Начинаешь работать над фичей в новой параллельной сессии.
- Свежему worktree нужно выдать лейн (порты/серийник/суффикс), которого ещё нет.
- Сессии конфликтуют: установка APK перетирает чужую, dev-сервер не поднимается («порт занят»),
  команды бьют не в тот эмулятор.
- Параллельные сборки упёрлись в RAM/своп.

## Модель изоляции (коротко)

| Ресурс | Состояние | Действие |
|---|---|---|
| Файлы, `build/` worktree | изолированы автоматически | ничего — JVM-тесты и снапшоты параллелятся сами |
| `~/.gradle`, `~/.konan` | общие, безопасные (кэш контент-адресуемый) | не трогать, не чистить вручную |
| Эмулятор/симулятор, ADB-серийник | **общие — зона коллизий** | свой на сессию (см. лейн) |
| Порты dev-сервера / WASM | **общие — зона коллизий** | свой на сессию (см. лейн) |
| `applicationId` на устройстве | **общий — зона коллизий** | суффикс на сессию (`applicationIdSuffix`) |

Полная схема, формулы лейна и заметки по ОС — в [references/isolation.md](references/isolation.md).

## Рабочий цикл

1. **Создай worktree сессии нативно:** `claude --worktree <фича>` → каталог `.claude/worktrees/<фича>`,
   ветка `worktree-<фича>`. Не используй ручной `git worktree add`, если хватает нативного.

2. **Дай новым worktree нужные gitignored-файлы.** Положи в корень репозитория `.worktreeinclude`
   (шаблон: [templates/worktreeinclude](templates/worktreeinclude)) — Claude Code копирует
   перечисленные пути в каждый создаваемый worktree. Минимум — `local.properties` (путь к Android SDK,
   машинно-зависимый, в `.gitignore`).

3. **Выдай worktree лейн.**

   Через **direnv**: положи в корень `.envrc` (шаблон: [templates/envrc.sh](templates/envrc.sh)) — он
   сам выводит уникальный лейн из имени каталога worktree. В `.envrc` нет секретов — закоммить его,
   тогда git донесёт его до каждого worktree автоматически. В каждом новом worktree один раз:
   `direnv allow`. Подробнее и портируемая альтернатива (sourced-файл / PowerShell) —
   [references/isolation.md](references/isolation.md).

4. **Дай веткам уживаться на одном устройстве.** Добавь в Android-модуль блок из
   [templates/applicationIdSuffix.gradle.kts](templates/applicationIdSuffix.gradle.kts): он берёт
   `APP_ID_SUFFIX` из лейна, и две ветки ставятся как разные приложения.

5. **Затюнь параллельную сборку.** Слей блок из
   [templates/gradle-parallel.properties](templates/gradle-parallel.properties) в `gradle.properties`
   проекта. Ключевое — `-Xmx` ÷ числа параллельных сессий и configuration/build cache.

## Дисциплина команд

- Держи `ANDROID_SERIAL` экспортированным (лейн делает это) — тогда `adb` и `./gradlew install*`
  сами бьют в твой эмулятор. **Никогда** не запускай `adb`-команды устройства, `xcrun simctl` или
  установку без серийника/UDID своего лейна.
- iOS: у `simctl` нет аналога `ANDROID_SERIAL` — всегда передавай UDID явно
  (`xcrun simctl … "$IOS_SIM_UDID"`).
- Не чисти `~/.gradle` / `~/.konan` и не сноси чужие worktree (`git worktree remove --force`,
  `git worktree prune`) — это общие ресурсы других сессий.
