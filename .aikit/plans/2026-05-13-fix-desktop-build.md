# Fix desktop build

**Created:** 2026-05-13
**Branch:** master
**Source task:** Нужно пофиксить билд desktop

## Context (digest)

- Gradle 8.14.5, Kotlin DSL (embedded Kotlin 2.0.21), JDK 25 (temurin-25.0.3) установлен системным.
- JDK 25 несовместим с Kotlin DSL: `IllegalArgumentException: 25.0.3` из `JavaVersionUtilsKt.isAtLeastJava9` при компиляции KTS-скриптов → все Gradle-задачи падают.
- JBR 21.0.10 доступен: `/Users/kruz18/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home`. С ним `bash gradlew tasks` проходит.
- `:app:byCompose:uikit:compileKotlinJvm` падает с 4 ошибками в `GlassmorphismBackButton.kt`:
  - `Unresolved reference 'icons'` (×2) и `Unresolved reference 'Icons'` — импорты из `androidx.compose.material.icons`, которого нет в зависимостях `:uikit`; добавлять не будем.
  - `Unresolved reference 'spacing'` — `AppTheme.spacing` не объявлен в объекте `AppTheme` (`Theme.kt` commonMain в `:theme`); `LocalAppSpacing` (internal) уже существует в том же модуле.
- Решение для иконки: заменить `Icons.AutoMirrored.Filled.ArrowBack` на `ImageVector`, определённый inline через `ImageVector.Builder` (Material path data). Зависимости не меняются.
- `compose.material3` уже в `:uikit`; `androidx.compose.ui.graphics.vector.ImageVector` входит в `compose.ui`, которое тоже уже объявлено.

## Invariants

- Не добавлять новые зависимости ни в один модуль.
- Не изменять public API `:shared` или `:common`.
- Android-таргет остаётся компилируемым (`:app:byCompose:common:compileKotlinJvm` не ломается).
- Не использовать `./gradlew` напрямую — только через `bash gradlew` (JAVA_HOME настроен через `org.gradle.java.home`).

## Steps

### Step 1 — gradle-jdk-fix
- **Goal:** Направить Gradle daemon на JBR 21, чтобы конфигурационная фаза KTS-скриптов проходила без исключений.
- **DoD:** `bash gradlew tasks` завершается с exit 0.
- **Review:** heavy
- **What would be wrong:** Путь прописан жёстко для другой машины; `./gradlew` всё ещё использован в других местах.
- **Verify:** [shell: "bash gradlew tasks --no-configuration-cache 2>&1 | grep -E 'BUILD|FAILED'"]
- **Expect:** green
- **Shape:**
    - **files-glob:** "gradle.properties"
    - **max-diff-lines:** 5
    - **no-test-changes:** true
- **Assumptions:** JBR 21.0.10 установлен по пути `/Users/kruz18/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home` на целевой машине.

### Step 2 — uikit-compile-fix
- **Goal:** Устранить 4 ошибки компиляции в `GlassmorphismBackButton.kt` и добавить `AppTheme.spacing` в `:theme`.
- **DoD:** `bash gradlew :app:byCompose:desktop:compileKotlinDesktop` завершается с exit 0.
- **Review:** standard
- **What would be wrong:** Inline `ImageVector` задаёт неверный viewport или path, что даёт некорректную иконку во время выполнения; `AppTheme.spacing` объявлен не `@Composable` — не компилируется.
- **Verify:** [shell: "bash gradlew :app:byCompose:desktop:compileKotlinDesktop --no-configuration-cache 2>&1 | grep -E 'BUILD|FAILED|error:'"]
- **Expect:** green
- **Assumptions:** `ImageVector.Builder` и `androidx.compose.ui.graphics.vector.*` доступны через уже объявленный `compose.ui` в `:uikit`.

## Out of scope

- RTL/autoMirrored поведение для ArrowBack (оставляем как есть, без `LocalLayoutDirection` логики).
- Обновление Gradle wrapper до версии с нативной поддержкой JDK 25.
- Рефакторинг остальных компонентов `:uikit`.
