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

- [ ] Шаг 1: Добавить кнопку «Назад» в DetailsContent.kt

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

(Заполняется @DoDGate при CLOSE. До тех пор пусто.)
