# Тест-кейсы — pdf-back-button

> Источник: spec.md § Тест-план
> Легенда статусов: PEND (не запускался) | PASS | FAIL | SKIP

| TC ID | Статус | Тип | Описание | Проверяет | Реализация теста | Примечания |
|-------|--------|-----|----------|-----------|------------------|------------|
| TC-1 | SKIP | unit | `DetailsContent` рендерится с кнопкой «Назад» в левом верхнем углу | AC-1, AC-3 | (нет impl-ссылки) | Compose UI Test runtime недоступен в jvmTest/commonTest. Z-order гарантирован архитектурно (кнопка объявлена после LazyColumn в Box). Tech-debt: common/compose-ui-test-infra |
| TC-2 | PASS | unit | Нажатие кнопки «Назад» вызывает `component.onBack()` ровно один раз | AC-2 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/DetailsContentTest.kt:17 | |
| TC-3 | SKIP | unit | Кнопка «Назад» и кнопки масштаба отображаются одновременно без перекрытия | AC-5 | (нет impl-ссылки) | Compose UI Test runtime недоступен. AC-5 визуально подтверждён PO на 5.6 checkpoint. Tech-debt: common/compose-ui-test-infra |
| TC-4 | SKIP | unit | Кнопка имеет круглую форму (проверка семантики или `shape` в preview-тесте) | AC-4 | (нет impl-ссылки) | Compose UI Test runtime недоступен. AC-4 покрыт TC-10 (PASS) и TC-11 (PASS). Tech-debt: common/compose-ui-test-infra |
| TC-5 | SKIP | manual | Быстрое двойное нажатие кнопки «Назад» не приводит к крашу или двойному pop | EC-1 | (deferred — архитектурная гарантия) | EC-1 (High) архитектурно гарантирован: Decompose StackNavigation.pop() идемпотентен при пустом стеке. Принято PO как deferred. |
| TC-6 | SKIP | manual | Нажатие «Назад» во время рендеринга PDF не вызывает утечку ресурсов (`pdfManager.close()` вызывается) | EC-2 | (deferred — архитектурная гарантия) | EC-2 (High) архитектурно гарантирован: DisposableEffect(pdfManager) { onDispose { pdfManager.close() } }. Принято PO как deferred. |
| TC-7 | PASS | unit-edge | `onBack()` не вызывается при начальной composition `DetailsContent` (без нажатия кнопки) | AC-2 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/DetailsContentTest.kt:28 | |
| TC-8 | SKIP | unit | Кнопка «Назад» является дочерним элементом `Box` и объявлена после `LazyColumn` (z-order поверх контента) | AC-3, EC-4 | (нет impl-ссылки) | Compose UI Test runtime недоступен. EC-4 гарантирован z-order. Tech-debt: common/compose-ui-test-infra |
| TC-9 | PASS | integration | `DetailsContent` компонуется с минимальным фейком `DetailsComponent` и не бросает исключение при начальной отрисовке | AC-1 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/DetailsContentTest.kt:37 | |
| TC-10 | PASS | unit | Кнопка «Назад» имеет непустой `contentDescription` (accessibility) | AC-4 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/DetailsContentTest.kt:backButtonContentDescription_isNotEmpty | |
| TC-11 | PASS | manual | Кнопка «Назад» выглядит круглой, без избыточной тени, соответствует дизайну | AC-4 | (PO-reported) | PO одобрил фикс на 5.6 CHECKPOINT (/kit-approve, коммит 5f16c52): IconButton+CircleShape без избыточной тени |

## Журнал дефектов

| DEF ID | TC | Серьёзность | Статус | Открыт | Примечания |
|--------|----|-------------|--------|--------|------------|
| DEF-1 | TC-10 | medium | FIXED | 2026-05-07 | PO at 5.6 manual verification: нет accessibility (contentDescription=null на SmallFloatingActionButton). Исправлено: добавлен contentDescription в DetailsContent.kt |
| DEF-2 | TC-11 | medium | FIXED→VERF | 2026-05-07 | PO одобрил фикс на 5.6 CHECKPOINT (/kit-approve, коммит 5f16c52). IconButton+CircleShape без тени; ripple-clip исправлен. Закрыто 2026-05-08. |
