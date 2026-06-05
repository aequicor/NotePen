# Восстановление состояния вида редактора

Состояние PDF-редактора сохраняется в двух местах: лёгкий per-file sidecar
аннотаций для вида и основной annotation bundle для штрихов/настроек
инструментов. Workspace session autosave хранит открытые вкладки и позицию вида.
На большом зуме вертикальной позиции недостаточно: без горизонтального `pan.x`
документ открывается на той же странице и масштабе, но с другим участком страницы
в окне.

Источник истины:
- `AnnotationViewState.panXPx` в `drawing/api/.../AnnotationViewState.kt` и DTO
  `AnnotationViewStateDto` в `app/byCompose/common/.../AnnotationRepositoryJvmAndroid.kt`;
- `TabViewState.panXPx` и `TabSession.captureSession()` для session autosave;
- `PdfViewerState.applyInitialState(..., panXPx)` в обеих actual-реализациях
  `rendering/impl/.../PdfViewerState.*.kt`;
- финальный flush при disposal: `DetailsContent` пишет session autosave,
  annotation bundle с настройками инструментов и last page, `EditorPanel` пишет
  `.view` sidecar.

Инварианты:
- `panXPx = null` означает старый формат без горизонтального pan; restore должен
  вести себя как раньше и центрировать X по текущим правилам.
- Final `.view` flush нельзя делать до готового viewer layout (`pages` есть и
  `basePageWidthPx > 0`), иначе можно затереть восстановленное состояние дефолтом
  `100% / page 0`.
- `panXPx` применяется после восстановления zoom и вертикальной страницы/offset,
  затем проходит через текущий clamp, чтобы старые значения не уводили документ
  за допустимые границы.
- `DetailsContent.saveAllOpenTabs()` перед записью обязан checkpoint-ить текущие
  `pen/marker/eraser` в `documentToolStates`, а `saveTab()` должен брать настройки
  по `state.filePath`, иначе текущий инструмент перезапишет все фоновые вкладки.
- Annotation bundle save пишет весь bundle. При изменении save helper нельзя
  забывать `highlights` и `notes`, иначе финальный flush сотрёт заметки/выделения.

Проверено:
- `./gradlew.bat :rendering:impl:jvmTest --tests "ru.kyamshanov.notepen.pdfviewer.PdfViewerStateRestoreTest"`
- `./gradlew.bat :app:byCompose:common:jvmTest --tests "ru.kyamshanov.notepen.AnnotationRepositoryJvmTest" --tests "ru.kyamshanov.notepen.session.SessionSerializationTest"`
- `./gradlew.bat :app:byCompose:android:compileDebugKotlin`
- `./gradlew.bat :drawing:api:ktlintCheck :rendering:impl:ktlintCheck :app:byCompose:common:ktlintCheck`
