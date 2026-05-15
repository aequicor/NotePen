---
name: kotlin-build
description: How to drive Gradle builds in Kotlin project NotePen efficiently — daemon, configuration cache, build cache, --offline, task selection per flavor (JVM / KMP / Android). Use when the user asks to build, test, or troubleshoot Gradle performance.
---

# kotlin-build

Гид по корректной работе с Gradle в проекте **NotePen**.

## Базовые правила

- Используй wrapper: `./gradlew`, не глобальный `gradle`.
- Не отключай configuration cache и build cache без причины — они дают кратный выигрыш.
- Долгие сборки запускай в фоне и опрашивай статус, не блокируй чат.
- Никогда не передавай секреты через `-P` или env — они попадают в configuration cache.

## Полезные флаги

| Флаг | Когда |
|---|---|
| `--configuration-cache` | По умолчанию включён, если в `gradle.properties` `org.gradle.configuration-cache=true`. Для диагностики — `--no-configuration-cache` |
| `--build-cache` | По умолчанию включён, если `org.gradle.caching=true`. Грузит из локального + (опц.) remote cache |
| `--offline` | Изолирует от сети. Использовать для воспроизведения «у меня работает» |
| `--stacktrace` | Полный stack trace падения |
| `--warning-mode all` | Видеть deprecations |
| `--scan` | Подгрузка scan'а на scans.gradle.com — **не использовать без явного запроса пользователя** (утечка данных проекта) |
| `--rerun-tasks` | Принудительный re-run, игнорируя up-to-date |

## Выбор task под flavor

- Сборка: `./gradlew build`
- Все тесты: `./gradlew allTests`
- Только JVM-тесты для скорости: `./gradlew :module:jvmTest`
- Native-таргет (для host platform): `./gradlew :module:linkDebugExecutableMacosArm64` (или соответствующий)
- Не используй `Dispatchers.IO` / `Dispatchers.Main` в `commonMain` — они недоступны на всех таргетах

## Гигиена daemon

- Daemon живёт между запусками — это норма.
- Если сборка зависла или ведёт себя странно: `./gradlew --stop`, повторить.
- Не использовать `--no-daemon` без причины — сильно замедляет повторные сборки.

## Что НЕ делать

- Не править `gradle/wrapper/gradle-wrapper.properties` без явного запроса (это смена версии Gradle).
- Не апгрейдить плагины «заодно» — Kotlin / AGP / KSP / Compose Compiler жёстко связаны по версиям.
- Не использовать `gradle.properties` пользователя (`~/.gradle/gradle.properties`) для project-specific настроек.
