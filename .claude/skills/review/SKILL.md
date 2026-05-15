---
name: review
description: Review the current branch's diff against repo conventions in Kotlin project NotePen. Use when the user asks to "review", "check the changes", "look at the diff", or before opening a PR.
---

# /review

Сделай ревью текущей ветки Kotlin-проекта **NotePen**.

## Шаги

1. `git diff main...HEAD` — посмотри что изменилось.
2. `git log main..HEAD` — пойми намерение (по сообщениям коммитов).
3. Прочитай изменённые файлы целиком, не только diff-окна.
4. Проверь:
   - Соответствие Kotlin-конвенциям из `CLAUDE.md` (null safety, корутины, KDoc, immutability).
   - **Запреты** из `CLAUDE.md` → секция «Запрещено»: `!!`, `GlobalScope`, прямой `Dispatchers.*`, `@Suppress(...)`, `runBlocking` вне main/тестов, `Pair`/`Triple` в публичных API.
   - **SOLID**: SRP (нет `Manager`/`Helper`/`And` в именах), OCP (нет растущих `when`-каскадов на типы), DIP (domain не импортирует infrastructure).
   - Нет ли мёртвого кода / закомментированных блоков.
   - Покрыты ли изменения тестами (Gradle test tasks под flavor проекта).
   - Нет ли утечек секретов (`.env`, `local.properties`, ключи, токены).

5. `./gradlew ktlintCheck` — формат должен проходить.

6. `./gradlew detekt` — статанализ без ошибок.

7. Strict-режим: дополнительно проверь, что нет `rm -rf`, force push, временных хаков `// TODO: fix later`. `@Suppress(...)` запрещён без исключений — если встретился, это блокер.

## Формат отчёта

- **Critical** (блокеры) — что обязательно поправить до merge.
- **Suggestions** — необязательные улучшения.
- **Nits** — стилистика.

Если ничего не нашёл — так и скажи. Не выдумывай замечания ради объёма.
