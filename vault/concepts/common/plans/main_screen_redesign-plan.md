---
genre: concept
title: "Plan: Main Screen Redesign"
topic: main-screen
module: common
triggers:
  - "план главный экран"
  - "main screen plan"
  - "main_screen_redesign plan"
confidence: high
source: ai
updated: 2026-05-07T18:00:00Z
---

# Plan: Main Screen Redesign

**Feature:** main_screen_redesign  
**Modules:** `:shared`, `:app:byCompose:common`  
**Requirements:** `vault/concepts/common/requirements/main_screen_redesign.md` (58 AC)  
**Spec:** `vault/reference/common/spec/main_screen_redesign.md`  
**Corner Cases:** `vault/concepts/common/plans/main_screen_redesign-corner-cases.md`  
**Test Cases:** `vault/reference/common/test-cases/main_screen_redesign-test-cases.md`  

---

## Цель

Переработать главный экран NotePen: заменить заглушку (`ListContent`/`DefaultListComponent`) на полнофункциональный главный экран с историей недавних PDF-файлов, папками, превью и кнопкой открытия файла.

---

## Архитектурные решения

| Решение | Обоснование |
|---------|-------------|
| Порты объявлены в `:shared`, реализации в `commonMain`/`androidMain`/`jvmMain` `:common` | Сохраняет Clean Architecture: домен не зависит от инфраструктуры |
| ViewModel на StateFlow (не Decompose Component) | Проще тестировать изолированно; хостируется внутри Decompose-компонента через `lifecycle-coroutines` |
| Персистирование истории: JSON-файл (kotlinx.serialization-json) | Максимум 20 записей — Room/DataStore избыточны; атомарная запись через `File.writeText` + tmp-rename на Desktop, `filesDir` на Android |
| Персистирование папок: JSON-файл (единый файл `folders.json`) | Максимум 100 папок × ограниченное число файлов; атомарная запись аналогично |
| Кеш миниатюр: файловая директория приватного хранилища | Переживает перезапуск; LRU реализован в `ThumbnailRepositoryImpl` |
| Desktop атомарность: `Mutex` (in-process) + `FileLock` | `FileLock` не блокирует потоки одного JVM-процесса → нужен `Mutex` как первый уровень |
| Material 3 UI | Подключается из `compose.material3` (добавить в `commonMain` `:common`); тема — `ComposableAppTheme` из `:theme` |
| Адаптивная верстка: breakpoint 600 dp | `LazyColumn` (< 600 dp) / `LazyVerticalGrid(GridCells.Adaptive(280.dp))` (≥ 600 dp) |

---

## Зависимости, которые нужно добавить

| Модуль | Зависимость | Где |
|--------|-------------|-----|
| `:shared` | `kotlinx-coroutines-core` (уже транзитивно через Decompose) | — |
| `:shared` | `kotlinx-serialization-json` | `commonMain` |
| `:common` | `compose.material3` | `commonMain` |
| `:common` | `kotlinx-coroutines-core` | commonMain (транзитивно) |
| `:common` | `lifecycle-coroutines` (Essenty) | `commonMain` |
| `:common` | `kotlinx-serialization-json` | `commonMain` |

---

## Этапы реализации

| # | Название | Модуль | Область |
|---|---------- |--------|---------|
| 01 | Доменные модели + порты | `:shared` | commonMain |
| 02 | Use cases | `:shared` | commonMain |
| 03 | ViewModel + UiState + FilePicker expect | `:common` | commonMain |
| 04 | UI-компоненты + MainContent | `:common` | commonMain |
| 05 | Инфраструктура Android | `:common` | androidMain |
| 06 | Инфраструктура Desktop | `:common` | jvmMain |
| 07 | Навигация, wiring, lastPageIndex trigger | `:common` + `:shared` | commonMain + platform |

---

## Критические угловые случаи — покрытие по этапам

| CC | Тип | Этап | Тест |
|----|-----|------|------|
| CC-1 | Critical | Stage 02 (AddToHistoryUseCase) | Unit |
| CC-2 | Critical | Stage 02 (SafFuzzyMatchDetected, диалог с URI) | Unit |
| CC-5 | Critical | Stage 01 (flush-crash recovery) | Unit |
| CC-8 | Critical | Stage 02 (pessimistic eviction) | Unit |
| CC-23 | Critical | Stage 02 + Stage 07 | Unit |
| CC-24 | Critical | Stage 02 (eviction folder link) | Unit |

---

## Pre-mortem risks

| # | Линза | Риск | Вероятность | Влияние | Митигация | ACT-NOW? |
|---|-------|------|-------------|---------|-----------|---------- |
| R1 | Зависимости | `kotlinx-serialization-json` нет в версиях — нужно добавить в `libs.versions.toml` | Высокая | Блокирует Stage 01 | Добавить в Stage 01 как первый шаг | ✅ ACT-NOW |
| R2 | Архитектура | Existing `ListComponent`/`ListContent` удаляются; `DefaultRootComponent` нужно обновить — риск регрессии в навигации | Средняя | Ломает запуск приложения | Stage 07 последний — это правильно; smoke-тест навигации в Stage 07 | ✅ ACT-NOW |
| R3 | Платформа | Android `PdfRenderer` существует только с API 21+; minSdk=24 → безопасно | Низкая | Нет | Проверить в Stage 05 | — |
| R4 | Конкурентность | Desktop `Mutex` + `FileLock`: `FileLock` acquireExclusive — блокирующий вызов → нужен `withContext(IO)` | Высокая | Deadlock/ANR | В Stage 06 явно использовать `withContext(IO)` для FileLock | ✅ ACT-NOW |
| R5 | UI | Material 3 и Material 2 в одном модуле (`compose.material` + `compose.material3`) — конфликт `LocalContentColor`, `MaterialTheme` | Средняя | Некорректные цвета/крэш | В Stage 04 использовать только M3-импорты из `compose.material3`; проверить отсутствие M2-импортов | ✅ ACT-NOW |
| R6 | Тесты | ViewModel использует `Dispatchers.Main` → тесты без `Dispatchers.setMain` упадут | Средняя | Ложные FAIL в CI | В Stage 03 настроить `TestCoroutineDispatcher` в тестах | ✅ ACT-NOW |
| R7 | Память | OOM при генерации миниатюры (CC-11) — PdfBox держит документ в памяти | Средняя | Крэш | `generate()` обёрнут в `try-catch(Throwable)` с `Result.failure` | — |
| R8 | Данные | JSON-файл истории повреждён (краш во время записи) → пустая история при следующем запуске | Низкая | Потеря истории | Atomic write: писать во временный файл, затем `rename`; чтение невалидного JSON → пустой список | — |

**ACT-NOW (5 рисков):** R1 — добавить serialization-json в Stage 01; R2 — smoke-тест навигации в Stage 07; R4 — `withContext(IO)` для FileLock в Stage 06; R5 — только M3-импорты в Stage 04; R6 — TestCoroutineDispatcher в Stage 03.
