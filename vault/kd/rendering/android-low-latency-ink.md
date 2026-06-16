# Android low-latency ink

## Контекст

На Android штрих во время письма должен идти за пером без ожидания Compose recomposition. `CanvasFrontBufferedRenderer` уже даёт front-buffer слой, но если кормить его через `snapshotFlow` по `PdfDrawingState.livePoints`, пачка samples схлопывается до одного события за кадр и визуально отстаёт от пера. На максимальном zoom страница может быть больше безопасного размера front-buffer, поэтому live overlay рендерится в capped-буфер с сохранением пропорций и масштабируется вместе со страницей, а не отключается.

## Источник правды

- `drawing/api/src/commonMain/kotlin/ru/kyamshanov/notepen/drawing/api/PdfDrawingState.kt` — `LiveStrokeSample`, `addLiveStrokeListener(...)`, `startDrawing(...)`, `addPoint(...)`.
- `rendering/impl/src/androidMain/kotlin/ru/kyamshanov/notepen/lowlatency/LowLatencyStrokeOverlay.android.kt` — Android actual overlay: listener immediately calls `CanvasFrontBufferedRenderer.renderFrontBufferedLayer(...)`; `snapshotFlow` is only for lift-off commit/clear.
- `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/DrawablePdfPage.kt` — `cappedLowLatencyOverlaySize(...)`, mounts `LowLatencyStrokeOverlay` inside the same page box and suppresses duplicate Compose live-stroke rendering when overlay is active.
- `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/InkRendering.kt` — live Compose fallback does not paint a one-sample pen dot; down-only taps are still committed by `PdfDrawingState.finishDrawing()` as a short segment.

## Инварианты

- Не возвращать подачу samples в Android front buffer через `snapshotFlow { livePoints.size }`: это снова добавит frame-bound latency на планшете.
- Listener получает только samples, которые input pipeline уже принял в `PdfDrawingState`; он не должен менять geometry, smoothing, sync или undo.
- При подключении renderer-а во время уже начатого stroke нужно один раз drain'ить текущие `livePoints`, иначе первый DOWN-sample может быть пропущен, пока `SurfaceView` создавался.
- Первый DOWN-sample нужен как начало следующего segment, но его нельзя рисовать отдельной точкой во время письма. И Android front-buffer, и Compose fallback должны начинать видимую live-линию с первого настоящего segment; tap-dot после lift-off остаётся через committed short segment.
- Live overlay и основной Compose/bitmap renderer должны считать ширину пера через одну формулу `rendering:api` `computeSegmentWidth(...)`. Нельзя умножать live-width напрямую на raw `pressure`: первые Android samples часто имеют маленькое давление, из-за чего начало штриха во время письма становится почти невидимым и появляется только после commit.
- Если страница больше `LOW_LATENCY_OVERLAY_MAX_DIM_PX`, Android overlay должен оставаться активным в меньшем same-aspect `SurfaceView`, который масштабируется до размера страницы. Временная мягкость live-линии лучше, чем возврат к Compose live-render на max zoom: иначе handwriting заметно отстаёт от пера.
- Lift-off остаётся state-driven: после `finishDrawing()` overlay делает `commit()`, ждёт появления completed path в Compose и только потом `clear()`, чтобы не было дырки между front-buffer и основным canvas.

## Проверка

- `./gradlew :drawing:api:jvmTest --tests "ru.kyamshanov.notepen.drawing.api.PdfDrawingStateUndoTest"` — listener получает только accepted samples и отключается через unregister.
- `./gradlew :rendering:api:jvmTest --tests "ru.kyamshanov.notepen.rendering.api.StrokeWidthFormulaTest"` — ширина live/final stroke не проваливается к нулю при слабом pressure.
- `./gradlew :rendering:impl:jvmTest --tests "ru.kyamshanov.notepen.InkRenderingTest"` — capped overlay size остаётся non-zero выше лимита и сохраняет aspect ratio.
- `./gradlew :rendering:impl:compileAndroidMain :app:byCompose:android:compileDebugKotlin`
- `./gradlew :rendering:impl:ktlintCheck`
