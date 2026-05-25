---
name: security-review
description: Security methodology for KMP+Ktor project NotePen — how to run a diff-aware review, the threat checklist (injection, auth/IDOR, crypto, secrets, serialization, Ktor network/TLS/CORS, concurrency, logging) and the severity rubric. Use when asked for a "security review", "audit", "check for vulnerabilities", during the pipeline security stage, or before merging code touching auth, crypto, IO, serialization, or secrets.
---

# security-review

Diff-aware security-ревью изменений в **NotePen**. Анализируй **изменения**, а не весь репозиторий.

## Шаги

1. `git diff main...HEAD` — что изменилось.
2. Для каждого изменения пройдись по таблице угроз.
3. Сомнительное поведение библиотеки проверяй по её исходникам (скил **docs-on-demand**) или CVE через `WebFetch` — не по памяти.
4. Отсеки ложные срабатывания: репортуй только то, для чего есть **конкретный путь эксплуатации**.
5. Отчёт по уровням severity.

## Таблица угроз (KMP / Ktor)

| Категория | На что смотреть |
|---|---|
| Injection | Конкатенация пользовательского ввода в SQL/command/path; `ProcessBuilder`/`Runtime.exec` со склейкой |
| AuthN/AuthZ | Пропущенные проверки доступа, IDOR (объект по id без проверки владельца), hardcoded creds, слабый хэш паролей |
| Crypto | Своя крипта, статичные IV/соли, `Random` вместо `SecureRandom`, ECB |
| Secrets | Ключи/токены в коде, `local.properties`/`.env`/`google-services.json`/keystore в git, секреты в `BuildConfig` и логах |
| Serialization | kotlinx.serialization polymorphic с непроверенным input; Jackson default typing; `ObjectInputStream` |
| Ktor network | Отключённая проверка TLS у client engine; `anyHost()` в CORS; cleartext; SSRF по пользовательскому URL без allowlist |
| Конкуренция | TOCTOU; гонки между корутинами на общем `MutableState`/файле/соединении |
| Logging | PII/токены в логах; логирование тела запроса/ответа в проде |
| Android (если есть) | `exported=true` без permission; `WebView` + JS + внешний контент; `allowBackup=true` для приватных данных |

## Severity rubric

- **Critical** — удалённый RCE, утечка секретов в проде, обход аутентификации.
- **High** — IDOR/доступ к чужим данным, инъекция с реальным вводом, отключённый TLS.
- **Medium** — слабая крипта/хэш, недостаточная валидация на доверенном пути.
- **Low / Info** — глубина защиты, потенциальная утечка в логах debug-сборки.

**Гейт пайплайна:** Critical/High блокируют коммит — возврат к стадии разработки.

## Что НЕ делать

- Не выдумывать уязвимости ради объёма; не разворачивать теорию без пути эксплуатации.
- Не анализировать неизменённый код, кроме как для контекста изменений.
