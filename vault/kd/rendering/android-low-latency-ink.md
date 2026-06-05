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
- Live overlay и основной Compose/bitmap renderer должны считать ширину пера через одну формулу `rendering:api` `computeSegmentWidth(...)`. Нельзя умножать live-width напрямую на raw `pressure`: первые Android samples часто имеют маленькое давление, из-за чего начало штриха во время письма становится почти невидимым и появляется только после commit.
- Если страница больше `LOW_LATENCY_OVERLAY_MAX_DIM_PX`, Android overlay должен отключаться, а не рендериться в меньший `SurfaceView` с последующим `graphicsLayer` up-scale. Иначе при zoom live-линия выглядит размытой, а после lift-off резко заменяется чётким основным рендером.
- Lift-off остаётся state-driven: после `finishDrawing()` overlay делает `commit()`, ждёт появления completed path в Compose и только потом `clear()`, чтобы не было дырки между front-buffer и основным canvas.

## Проверка

- `./gradlew :drawing:api:jvmTest --tests "ru.kyamshanov.notepen.drawing.api.PdfDrawingStateUndoTest"` — listener получает только accepted samples и отключается через unregister.
- `./gradlew :rendering:api:jvmTest --tests "ru.kyamshanov.notepen.rendering.api.StrokeWidthFormulaTest"` — ширина live/final stroke не проваливается к нулю при слабом pressure.
- `./gradlew :app:byCompose:android:compileDebugKotlin`
- `./gradlew :drawing:api:ktlintCheck :app:byCompose:common:ktlintCheck :sync:ktlintCheck :rendering:impl:ktlintCheck`
