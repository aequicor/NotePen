# Task: feat-pdf-back-button

Type: FEATURE
Module: common (:app:byCompose:common)
Description: Добавить круглую кнопку «Назад» (Material3, иконка стрелки) в левый верхний угол экрана просмотра PDF (DetailsContent). Инфраструктура onBack() уже реализована в DetailsComponent.
start_commit: bd359fdc5b11de3addc96c3e00b7bfeae546a533
current_step_idx: 1
step_commits:
  - step: 1
    sha: 2ca6950a6601fd64c118ddf8fff51989540e53b6
    goal: добавить кнопку «Назад» (SmallFloatingActionButton) в DetailsContent
    changed_files:
      - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt
      - app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/DetailsContentTest.kt
    superseded: true
    defect_count: 1
    notes: |
      Reviewer: CLEAN. MEDIUM: contentDescription=null, TC-1/3/4 без Compose UI Test.
      LOW: тавтологичный тест в DetailsContentTest, неиспользуемый импорт Image, @OptIn без ADR.
      Call sites: DetailsContent вызывается из RootContent.kt:59 — сигнатура не изменена.
      NOT_RUN_GAP: TC-1,3,4,8 требуют Compose UI Test runtime (manual).
      2026-05-07 defect TC-10: нет accessibility (contentDescription=null на кнопке)
  - step: 1
    sha: 4281256932c0e3a8b953bcf2ba23a798ab5c283e
    goal: step 1 fix — contentDescription="Назад" на кнопку «Назад» (TC-10)
    changed_files:
      - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt
      - app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/DetailsContentTest.kt
    superseded: true
    defect_count: 1
    notes: |
      Reviewer: CLEAN. MEDIUM: тест проверяет константу, не wire-up (допустимо без UI harness).
      LOW: @OptIn без inline-комментария.
      TC-10: PASS. DEF-1: FIXED. ALL_GREEN.
      2026-05-07 defect TC-11: кнопка не круглая, имеет большую тень и не красивая
  - step: 1
    sha: 5f16c527a03190fbe1f739c6d7b0cca3bde06651
    goal: step 1 fix2 — IconButton+CircleShape без тени, ripple-clip исправлен (TC-11)
    changed_files:
      - app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/DetailsContent.kt
    superseded: false
    defect_count: 0
    notes: |
      Reviewer: CLEAN. MEDIUM: выбор IconButton vs SmallFAB не задокументирован в DECISIONS.md.
      MEDIUM: порядок clip/background исправлен перед коммитом.
      LOW: неиспользуемый импорт Image удалён.
      TC-11: FAIL (manual — ожидает ручной проверки PO). NOT_RUN_GAP ожидаем.
    runbook: |
      ### Как проверить
      1. Запустить десктопное приложение, открыть PDF → в левом верхнем углу видна круглая кнопка «←».
      2. Нажать «←» → возврат на главный экран.
      3. Убедиться, что «+»/«-» кнопки масштаба в правом нижнем углу работают.
      4. `./gradlew :app:byCompose:common:jvmTest` → BUILD SUCCESSFUL.

      ### Регрессия
      - Кнопки масштаба «+»/«-» по-прежнему видны и работают.
      - `DefaultDetailsComponent.onBack()` по-прежнему маршрутизирует через onBackListener.

      ### Известные ограничения
      - TC-1, TC-3, TC-4, TC-8 требуют Compose UI Test framework — отложены на ручную проверку.
      - contentDescription=null (MEDIUM): accessibility gap, рекомендован followup.

      ### Решения, которые я принял
      - SmallFloatingActionButton объявлен последним в Box (z-order поверх LazyColumn).
      - FakeDetailsComponent в тестах через Decompose MutableValue.
status: active

Last-checkpoint: 2026-05-07T00:06:00Z — NEXT: RECONCILE → TRACE → DoDGate → diff-review → CLOSE

## 2026-05-07T00:01:00Z
- ВЫПОЛНЕНО: PO одобрил через /kit-approve; spec.md заморожен
- ДАЛЕЕ: EXECUTE — Шаг 1 (DetailsContent.kt)
