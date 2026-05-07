# Тест-кейсы — pdf-back-button

> Источник: spec.md § Тест-план
> Легенда статусов: PEND (не запускался) | PASS | FAIL | SKIP

| TC ID | Статус | Тип | Описание | Проверяет | Реализация теста | Примечания |
|-------|--------|-----|----------|-----------|------------------|------------|
| TC-1 | PEND | unit | `DetailsContent` рендерится с кнопкой «Назад» в левом верхнем углу | AC-1, AC-3 | (нет impl-ссылки) | Требует Compose UI Test runtime; не запускается в jvmTest без Compose окружения |
| TC-2 | PASS | unit | Нажатие кнопки «Назад» вызывает `component.onBack()` ровно один раз | AC-2 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/DetailsContentTest.kt:17 | |
| TC-3 | PEND | unit | Кнопка «Назад» и кнопки масштаба отображаются одновременно без перекрытия | AC-5 | (нет impl-ссылки) | Требует Compose UI Test runtime; не запускается в jvmTest без Compose окружения |
| TC-4 | PEND | unit | Кнопка имеет круглую форму (проверка семантики или `shape` в preview-тесте) | AC-4 | (нет impl-ссылки) | Требует Compose UI Test runtime; не запускается в jvmTest без Compose окружения |
| TC-5 | PEND | manual | Быстрое двойное нажатие кнопки «Назад» не приводит к крашу или двойному pop | EC-1 | (pending) | Требует ручной проверки PO |
| TC-6 | PEND | manual | Нажатие «Назад» во время рендеринга PDF не вызывает утечку ресурсов (`pdfManager.close()` вызывается) | EC-2 | (pending) | Требует ручной проверки PO |
| TC-7 | PASS | unit-edge | `onBack()` не вызывается при начальной composition `DetailsContent` (без нажатия кнопки) | AC-2 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/DetailsContentTest.kt:28 | |
| TC-8 | PEND | unit | Кнопка «Назад» является дочерним элементом `Box` и объявлена после `LazyColumn` (z-order поверх контента) | AC-3, EC-4 | (нет impl-ссылки) | Требует Compose UI Test runtime; не запускается в jvmTest без Compose окружения |
| TC-9 | PASS | integration | `DetailsContent` компонуется с минимальным фейком `DetailsComponent` и не бросает исключение при начальной отрисовке | AC-1 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/DetailsContentTest.kt:37 | |

## Журнал дефектов

| DEF ID | TC | Серьёзность | Статус | Открыт | Примечания |
|--------|----|-------------|--------|--------|------------|

(Заполняется по мере возникновения ошибок. Изначально пуст.)
