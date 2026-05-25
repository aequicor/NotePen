---
description: Run ktlint + detekt + tests for the project and report
argument-hint: [gradle-module]
---

# /build-check

Прогони цикл качества для **NotePen** и верни короткий отчёт.

Если передан `$1` — гоняй на этом Gradle-модуле (`:$1:...`), иначе на корневом проекте.

## Шаги

1. `./gradlew ktlintCheck` — формат. Упало — сначала `./gradlew ktlintFormat`, потом повтори `ktlintCheck`.

2. `./gradlew detekt` — статанализ.

3. Тесты под тип проекта:
`./gradlew allTests`.

Долгие команды — в фоне с опросом статуса, не блокируй чат.

Упала сборка с непонятной ошибкой Gradle (configuration cache, конфликт версий, плагины) — делегируй разбор субагенту **gradle-troubleshooter**.

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

Все прошли — «Status: green».
