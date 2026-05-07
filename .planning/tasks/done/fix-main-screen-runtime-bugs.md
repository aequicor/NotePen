# fix-main-screen-runtime-bugs

**Тип:** BUG
**Модули:** `:app:byCompose:common`, `:shared`, `:app:byCompose:desktop`
**Статус:** В работе

## Описание

Runtime-баги на главном экране Desktop:
1. Бесконечный лоадер — главный экран показывает индикатор загрузки, который никогда не исчезает
2. Кнопка «Открыть» не открывает нативный файловый диалог

## Артефакты фичи

- test cases: vault/reference/common/test-cases/main_screen_redesign-test-cases.md
- spec: vault/reference/common/spec/main_screen_redesign.md

## Контрольные точки

## 2026-05-08T02:30:00Z
- ДАЛЕЕ: Append TCs → BugFixer DEF-001 (loader) + DEF-002 (picker)

## 2026-05-08T03:15:00Z
- ВЫПОЛНЕНО: TC-126 (DEF-001) — добавлен LaunchedEffect(Unit) { onIntent(ScreenVisible) } в MainContent; CodeReviewer APPROVED
- ВЫПОЛНЕНО: TC-127 (DEF-002) — исправлены 3 точки поломки: main.kt no-op stub, RootContent FilePicker ветка, handleFilePickerResult не сбрасывал navigationTarget; 2 regression-теста добавлены; CodeReviewer APPROVED
- ВЫПОЛНЕНО: TestRunner RERUN — TC-126 PASS, TC-127 PASS, DEF-001 VERF, DEF-002 FIXED
- ДАЛЕЕ: CLOSE
