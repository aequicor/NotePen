# План реализации и Definition of Done — pdf-back-button

> Спецификация: ./spec.md (ЗАМОРОЖЕНА при CONFIRM)
> Тест-кейсы (актуальные): ./test-cases.md
> Статус: PLANNING

## Slice budget

| Параметр | Лимит | Текущее |
|----------|-------|---------|
| max_steps | 6 | 1 |
| max_files_per_step | 5 | 1 |
| max_lines_per_step | 400 | ~15 |

## План реализации

- [x] Шаг 1: Добавить кнопку «Назад» в DetailsContent.kt

  **Цель:** Разместить `SmallFloatingActionButton` (Material3) с иконкой `Icons.AutoMirrored.Filled.ArrowBack` в левом верхнем углу `Box`-контейнера `DetailsContent`. По нажатию вызывать `component.onBack()`.

  **Owned ACs / ECs / TCs:** AC-1, AC-2, AC-3, AC-4, AC-5, EC-1, EC-2, EC-4, EC-5 / TC-1, TC-2, TC-3, TC-4

  **Files:**
  - `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt`

  **Публичные сигнатуры (без изменений):**
  - `fun DetailsContent(component: DetailsComponent, modifier: Modifier = Modifier): Unit`

  **Guidelines:**
  - Использовать `SmallFloatingActionButton` из `androidx.compose.material3`
  - Иконка: `Icons.AutoMirrored.Filled.ArrowBack` (требует `material-icons-extended` или аналог)
  - Позиционирование: `modifier = Modifier.align(Alignment.TopStart).padding(16.dp)` внутри `Box`
  - Кнопка размещается **после** `LazyColumn` в `Box` — гарантирует z-order поверх контента
  - Не вводить новые зависимости, если иконка уже доступна в проекте

  **Test strategy:** test_after (UI composable — тесты после реализации)

  **Runnable:** После шага пользователь видит круглую кнопку «←» в левом верхнем углу PDF-вьювера; нажатие возвращает на главный экран.

## Replan log

(Заполняется навыком replan-on-discovery при необходимости. До тех пор пусто.)

## Diff-review

(Заполняется @Main на шаге 5.10 — между EXECUTE и CLOSE. До тех пор пусто.)

## Definition of Done

> Запуск: 2026-05-08 | Агент: @Verifier MODE=DOD | Вердикт: ❌ BLOCK

### Чеклист

| # | Группа | Проверка | Свидетельство | Статус |
|---|--------|----------|---------------|--------|
| 1 | ACs | Каждый AC имеет ≥1 PASS TC | AC-1→TC-9 (PASS) ✓; AC-2→TC-2 (PASS) ✓; AC-3→нет PASS (TC-1=SKIP, TC-8=SKIP) ❌; AC-4→TC-10, TC-11 (PASS) ✓; AC-5→нет PASS (TC-3=SKIP) ❌ | ❌ BLOCK |
| 2 | Critical ECs | Каждый Critical EC имеет ≥1 PASS TC | Critical EC отсутствуют в spec.md (все EC: High/Medium/Low) | ✅ |
| 3 | Состояние TC | Нет PEND или FAIL TC | 0 PEND, 0 FAIL из 11 итого (5 PASS, 6 SKIP) | ✅ |
| 4 | Запуск тестов | Последний RECONCILE = ALL_GREEN | RECONCILE вернул PEND_REMAIN → SKIP по PO-решению; формальный ALL_GREEN не зафиксирован | ❌ BLOCK |
| 5 | Ревьювер | Последний вердикт REVIEW = CLEAN | CLEAN (все циклы) | ✅ |
| 6 | Сборка и линт | Build PASS + lint clean | jvmTest BUILD SUCCESSFUL, коммит 5f16c52 | ✅ |
| 7 | План выполнен | Все шаги plan.md помечены done | 1/1 шагов [x] в § План реализации | ✅ |

### Диагностика (не блокирующая)

- Технический долг: `common/compose-ui-test-infra` — Compose UI Test runtime недоступен в jvmTest/commonTest (TC-1, TC-3, TC-4, TC-8 = SKIP)
- WEAK_ASSERTION: нет
- Дефекты: DEF-1 (FIXED), DEF-2 (FIXED→VERF) — закрыты

### Причины BLOCK

| # | Проверка | Что не так | Требуемый следующий шаг |
|---|----------|------------|-------------------------|
| 1 | ACs | AC-3 не имеет PASS TC. TC-1 (AC-1, AC-3) = SKIP; TC-8 (AC-3, EC-4) = SKIP. TC-9 (PASS) проверяет только AC-1. | PO расширяет TC-9.Verifies → AC-1, AC-3 (если считает достаточным), или @CodeWriter добавляет тест для z-order |
| 2 | ACs | AC-5 не имеет PASS TC. TC-3 (AC-5) = SKIP. | PO прикладывает screenshot через /kit-attach (visual PASS для TC-3), или @CodeWriter добавляет programmatic-тест |
| 3 | Запуск тестов | Формальный ALL_GREEN от MODE=RECONCILE отсутствует | Повторный @Verifier MODE=RECONCILE после устранения BLOCK #1 и #2 |

**Следующий шаг:** BLOCK — @Main не переходит к шагу 5.10 / 6 CLOSE до устранения блоков выше.
