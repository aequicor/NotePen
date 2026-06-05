# Android low-latency ink

## Контекст

На Android штрих во время письма должен идти за пером без ожидания Compose recomposition. `CanvasFrontBufferedRenderer` уже даёт front-buffer слой, но если кормить его через `snapshotFlow` по `PdfDrawingState.livePoints`, пачка samples схлопывается до одного события за кадр и визуально отстаёт от пера.

## Источник правды

- `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/drawing/api/PdfDrawingState.kt` — `LiveStrokeSample`, `addLiveStrokeListener(...)`, `startDrawing(...)`, `addPoint(...)`.
- `rendering/impl/src/androidMain/kotlin/ru/kyamshanov/notepen/lowlatency/LowLatencyStrokeOverlay.android.kt` — Android actual overlay: listener immediately calls `CanvasFrontBufferedRenderer.renderFrontBufferedLayer(...)`; `snapshotFlow` is only for lift-off commit/clear.
- `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/DrawablePdfPage.kt` — mounts `LowLatencyStrokeOverlay` inside the same page box and suppresses duplicate Compose live-stroke rendering when overlay is active.

## Инварианты

- Не возвращать подачу samples в Android front buffer через `snapshotFlow { livePoints.size }`: это снова добавит frame-bound latency на планшете.
- Listener получает только samples, которые input pipeline уже принял в `PdfDrawingState`; он не должен менять geometry, smoothing, sync или undo.
- При подключении renderer-а во время уже начатого stroke нужно один раз drain'ить текущие `livePoints`, иначе первый DOWN-sample может быть пропущен, пока `SurfaceView` создавался.
- Lift-off остаётся state-driven: после `finishDrawing()` overlay делает `commit()`, ждёт появления completed path в Compose и только потом `clear()`, чтобы не было дырки между front-buffer и основным canvas.

## Проверка

- `./gradlew :drawing:api:jvmTest --tests "ru.kyamshanov.notepen.drawing.api.PdfDrawingStateUndoTest"` — listener получает только accepted samples и отключается через unregister.
- `./gradlew :app:byCompose:android:compileDebugKotlin`
- `./gradlew :drawing:api:ktlintCheck :app:byCompose:common:ktlintCheck :sync:ktlintCheck :rendering:impl:ktlintCheck`
