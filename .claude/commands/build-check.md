---
description: Run ktlint + detekt + tests for the Kotlin project and report
argument-hint: [gradle-module]
---

# /build-check

Прогони цикл качества для проекта **NotePen** и верни короткий отчёт.

Если передан `$1` — гоняй на этом Gradle-модуле (`:$1:...`), иначе на корневом проекте.

## Шаги

1. `./gradlew ktlintCheck` — формат. Если упало — сначала попробуй `./gradlew ktlintFormat`, потом повтори `ktlintCheck`.

2. `./gradlew detekt` — статанализ.

3. `./gradlew test` — тесты (для KMP — `allTests`; для Android — `testDebugUnitTest`).

Долгие команды запускай в фоне и опрашивай статус. Не ждать синхронно блокирующе.

Если упала сборка с непонятной ошибкой Gradle (конфигурационный кэш, конфликт версий, плагины) — делегируй разбор субагенту **gradle-troubleshooter**.

## Формат отчёта

```
## ktlint
pass | fail (N issues)

## detekt
pass | fail (N issues)

## tests
pass | fail (N of M failed)

## Failures
- <test> — <file:line> — <одна строка причины>
```

Если все три прошли — «Status: green».
