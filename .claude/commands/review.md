---
description: Review the current branch against repo conventions
argument-hint: [base-branch]
---

# /review

Ревью текущей ветки **NotePen** против `$1` (по умолчанию `main`).

## Шаги

1. `git diff $1...HEAD` — что изменилось. `git log $1..HEAD` — намерение.
2. Прочитай изменённые файлы целиком, не только diff-окна.
3. Проверь против `CLAUDE.md`:
   - **Запрещено**: `!!`, `GlobalScope`, прямой `Dispatchers.*`, `@Suppress(...)`, `runBlocking` вне main/тестов, `Pair`/`Triple` в публичных API, секреты в коде.
   - **SOLID**: SRP (нет `Manager`/`Helper`/`And`), OCP (нет растущих `when` на типы), DIP (domain не импортирует infrastructure).
   - KDoc на новых публичных API; нет мёртвого/закомментированного кода.
   - Изменения покрыты тестами.

4. `./gradlew ktlintCheck` — формат проходит.

5. `./gradlew detekt` — статанализ без ошибок.

6. Безопасность: отдельным проходом делегируй субагенту **security-reviewer**.

## Формат отчёта

- **Critical** (блокеры) — что обязательно до merge.
- **Suggestions** — необязательные улучшения.
- **Nits** — стилистика.

Ничего не нашёл — так и скажи. Не выдумывай замечания ради объёма.
