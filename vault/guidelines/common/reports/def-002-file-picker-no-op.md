---
genre: guidelines
title: "Bug Fix Report: DEF-002 — FilePicker no-op on Desktop"
topic: file-picker
module: common
triggers:
  - "DEF-002"
  - "file picker no-op"
  - "кнопка открыть не работает desktop"
confidence: high
source: ai
updated: 2026-05-07T22:00:00Z
---

# Bug Fix Report: DEF-002 — Кнопка «Открыть» не открывает нативный файловый диалог (Desktop)

**Date:** 07.05.2026
**Author:** BugFixer Agent
**Status:** Fixed
**TC:** TC-127
**Commit:** f2da6da

---

## Bug Description

На Desktop при нажатии кнопки «Открыть» на главном экране ничего не происходило —
нативный файловый диалог (`java.awt.FileDialog`) не появлялся.

## Root Cause

Трёхуровневая проблема:

1. **`main.kt` — заглушка**: при создании `MainScreenComponent` параметр
   `onOpenFilePicker` был задан как `{}` (no-op лямбда). Реальная реализация
   (`FilePicker().pickPdfFile()`) никогда не вызывалась.

2. **`RootContent.kt` — ветка FilePicker не реализована**: в `LaunchedEffect`
   ветка `NavigationTarget.FilePicker` содержала только комментарий
   `"FilePicker открывается из MainContent"` без фактического вызова
   `onOpenFilePicker()` и без диспатча интента `FilePickerResult` обратно во ViewModel.

3. **`MainScreenViewModel.handleFilePickerResult` — утечка состояния при отмене**:
   при `uri == null` (пользователь нажал «Отмена» в диалоге) функция делала
   ранний `return`, не сбрасывая `navigationTarget` — состояние оставалось
   застрявшим в `NavigationTarget.FilePicker`.

## Fix Applied

### Изменения

| Файл | Изменение |
|------|-----------|
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/screen/MainScreenComponent.kt` | Сигнатура `onOpenFilePicker: () -> Unit` → `suspend () -> String?`; возвращает путь к выбранному файлу или null при отмене |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/RootContent.kt` | В ветке `NavigationTarget.FilePicker`: вызов `onOpenFilePicker()`, диспатч `FilePickerResult` с результатом |
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModel.kt` | `handleFilePickerResult`: при `uri == null` сбрасывает `navigationTarget = null` вместо раннего return |
| `app/byCompose/desktop/src/desktopMain/kotlin/main.kt` | `onOpenFilePicker = { FilePicker().pickPdfFile() }` — подключена реальная реализация через `java.awt.FileDialog` |

## Regression Tests

| Тест-файл | Тест | Покрытие |
|-----------|------|----------|
| `app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt` | `openFilePicker_setsFilePickerTarget_thenFilePickerResult_navigatesToEditor` | OpenFilePicker → FilePicker target; FilePickerResult(uri) → Editor target |
| `app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/ui/viewmodel/MainScreenViewModelTest.kt` | `filePickerResult_nullUri_clearsFilePickerNavigationTarget` | FilePickerResult(null) сбрасывает navigationTarget |

## Verification

- [x] Юнит-тесты проходят (2 новых теста зелёные)
- [x] Все тесты модуля `:app:byCompose:common` проходят
- [x] Все тесты модуля `:shared` проходят
- [x] Компиляция `:app:byCompose:common:compileKotlinJvm` — успех
- [x] Компиляция `:app:byCompose:desktop:compileKotlinDesktop` — успех

## Lessons Learned

- Сигнатура `() -> Unit` для колбэка, который обязан возвращать результат (путь к файлу),
  не позволяет корректно передать данные назад. Используй `suspend () -> T?` когда колбэк
  должен выполнить асинхронную работу и вернуть результат.
- Заглушка `{}` в production-коде точки входа (`main.kt`) без комментария или трекинга —
  прямой путь к потере функциональности. Незакрытые TODO/no-op в entry point должны
  фиксироваться в `.planning/DECISIONS.md`.
