---
description: Review the current branch against Kotlin repo conventions
argument-hint: [base-branch]
---

# /review

Сделай ревью текущей ветки Kotlin-проекта **NotePen** против `$1` (по умолчанию `main`).

Активируй скил **review** и следуй его инструкциям. Дополнительно проверь Kotlin-специфику: `!!`, `GlobalScope`, прямое использование `Dispatchers.*`, отсутствие KDoc на новых публичных API.

Если изменения крупные — параллельно делегируй второй проход субагенту **code-reviewer** для независимой оценки.

