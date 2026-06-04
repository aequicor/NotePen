---
genre: kd
title: Тайловый PDF-рендер при высоком зуме
module: common
topic: pdf-high-zoom-tile-rendering
status: active
updated: 2026-06-04
source: codex
---

# Тайловый PDF-рендер при высоком зуме

## Контекст

На страницах с большим количеством текста/надписей full-page bitmap упирался в лимит размера и при высоком зуме либо становился мыльным, либо требовал дорогой перерисовки всего листа. Решение: при большом зуме viewer рисует low-res preview и догружает только видимые PDF-тайлы.

КД не описывает алгоритм построчно: источник правды — код и тесты ниже.

## Источник правды в коде

- `shared/src/commonMain/kotlin/ru/kyamshanov/notepen/pdf/domain/port/PdfPageRenderer.kt` — контракт `renderTile(...)` с совместимым fallback через `renderPage(...)`.
- `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfTileRendering.kt` — ключи тайлов, `PdfPageLayer`, LRU-кэш, геометрия видимых тайлов и `pdfTilePreviewSize(...)`.
- `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/DrawablePdfPage.kt` — рисует PDF layer в том же `Canvas`, что и ink/highlight/note overlay.
- `rendering/impl/src/commonMain/kotlin/ru/kyamshanov/notepen/NativeImageDrawCache.kt` и JVM/Android actual — desktop-обход дорогого Compose `drawImage` для PDF/ink bitmap и prewarm native Skia image до draw-фазы.
- `rendering/impl/src/jvmMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesViewer.jvm.kt` и `rendering/impl/src/androidMain/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesViewer.android.kt` — включение tile mode, приоритеты и прокидывание `PdfPageLayer`.
- `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/pdf/infrastructure/JvmPdfPageRenderer.kt` — прямой PDFBox tile render для `rotationQuarters == 0`; `Graphics2D.background` обязан быть белым перед `renderPageToGraphics(...)`.
- `app/byCompose/common/src/androidMain/kotlin/ru/kyamshanov/notepen/pdf/infrastructure/AndroidPdfPageRenderer.kt` — прямой Android `PdfRenderer` tile render для `rotationQuarters == 0`; full-page и tile bitmap обязательно инициализируются белым.
- `rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfTileRenderingTest.kt` — contract tests для выбора видимых тайлов и размера low-res preview.
- `rendering/impl/src/jvmTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfViewerStateHighZoomPanTest.kt` — regression tests для transient high-zoom pan/wheel-scroll до commit.
- `app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/pdf/infrastructure/JvmPdfPageRendererTest.kt` — regression tests для белого фона full render / direct tile / rotated fallback tile.

## Инварианты

- Полный page bitmap сохраняется для низкого зума, preview, magnifier fallback, thumbnails и любых renderer-ов, которые не переопределяют `renderTile(...)`.
- Tile mode включается при высоком масштабе или когда full-page target превышает platform render limit.
- В `PdfPageLayer.Tiles` viewer передаёт только cached tiles из текущих `visiblePdfTileRequests(...)`, а не все тайлы страницы. Иначе при pan на высоком зуме `DrawablePdfPage` заново рисует уже накопленный off-screen tile backlog и даёт микрофризы.
- Готовые тайлы публикуются в `PdfTileCache.putAll(...)` пачкой через `Snapshot.withMutableSnapshot`, а не отдельным `put(...)` на каждый tile. Иначе подгрузка соседних чанков создаёт серию Compose invalidation подряд и pan спотыкается даже при фоновой PDFBox-растеризации.
- `DrawablePdfPage` должен рисовать PDF-тайлы внутри основного ink `Canvas`; иначе marker/highlight/note `BlendMode.Multiply` перестанет смешиваться с пикселями PDF.
- Annotation overlay (strokes/highlights/notes/live layer/note badges) нельзя показывать раньше PDF-подложки. `PdfPageLayer.hasVisiblePdfBase()` разрешает overlay только когда есть full bitmap, low-res preview или все видимые тайлы готовы; иначе пользователь видит белый слот страницы без «надписей поверх пустоты».
- Low-res preview в `PdfPageLayer.Tiles` нельзя постоянно рисовать full-page под тайлами: JFR `MainKt_2026_06_04_090354.jfr` показал, что это уходит в `Skia Image.makeFromBitmap` на EDT и остаётся главным steady-state hotspot при pan. Но preview нужен как fallback от мигания: `DrawablePdfPage` рисует full-page preview только пока нет cached tiles для текущего viewport, а после появления первых тайлов подставляет preview только в `missingTiles` текущего viewport.
- Если high-zoom открывается без старого bitmap в кэше, viewer должен дать bounded first-paint preview, а не ждать только тайлы. На Android это делает `PdfPagesViewer.android.kt` через `pdfTilePreviewSize(..., maxDimensionPx = TILE_PREVIEW_MAX_DIM_PX)`: preview ограничен по длинной стороне и не должен превращаться в дорогой full-target render.
- На JVM/Desktop high-zoom pan (`renderScalePercent >= TILE_MODE_MIN_SCALE_PERCENT`) двигает страницу transient `graphicsLayer`-translation и коммитит настоящий `pan` только после idle или release. Нельзя возвращать per-event `state.pan` update или periodic commit во время непрерывного движения: это снова заставит `SubcomposeLayout`/tile layer пересчитывать viewport и может фризить pan при догрузке соседних чанков.
- На JVM/Desktop render loop не должен публиковать новые preview/tile/full bitmap entries в Compose snapshot-state, пока активен transient visual transform. На страницах со штрихами такая публикация invalidates `DrawablePdfPage`, и Canvas заново композит PDF + ink/marker слои прямо во время pan.
- `DrawablePdfPage`, magnifier PDF segment и cached ink/marker pass на Desktop не должны возвращаться к обычному Compose `drawImage` для PDF/ink bitmap. JFR `MainKt_2026_06_04_095332.jfr` показал, что на страницах со штрихами EDT упирается в `Skia Image.makeFromBitmap` из `drawFullBitmap(...)` и `drawCompletedPenInk(...)`; `NativeImageDrawCache.jvm.kt` кеширует `org.jetbrains.skia.Image` и рисует через `skiaCanvas.drawImageRect(...)`.
- `PdfPageData.toImageBitmap()` на JVM и builders completed ink/marker должны вызывать `prewarmNativeImage(...)` до публикации bitmap в UI. Иначе первый draw нового full bitmap/tile/ink cache снова создаст `org.jetbrains.skia.Image` на EDT, то есть JFR опять покажет `_nMakeFromBitmap` во время pan. App-level callers (`EditorPanel`, thumbnails) должны делать `renderPage(...).toImageBitmap()` внутри background dispatcher: `renderPage` main-safe сам по себе, но после `withContext` он возвращается в caller context, и conversion/prewarm на `LaunchedEffect` main снова фризит UI. Сам `NativeImageStore` не должен держать общий lock во время `Image.makeFromBitmap(...)`: background prewarm одного bitmap не должен блокировать EDT, который рисует уже готовый другой bitmap. Eviction должен пропускать entries с `activeUseCount > 0`, потому что Skia image нельзя `close()`-нуть между acquire и `drawImageRect(...)`.
- Cold или catching-up completed ink cache не должен векторно replay-ить большой tail штрихов на UI draw path. `InkRendering.shouldDrawCompletedInkTail(...)` ограничивает fallback по числу strokes/points; тяжёлые страницы ждут async bitmap cache, иначе high-zoom pan фризит именно на страницах со штрихами.
- Magnifier тоже обязан иметь оба completed bitmap cache — pen и marker. После ограничения тяжёлого vector tail нельзя рисовать marker strokes через uncached `drawCompletedMarkerInk(..., cached = null)`: на насыщенных страницах маркеры либо снова replay-ятся в UI draw path, либо ждут cache, которого для magnifier нет.
- Нельзя скрывать full-page preview или completed ink во время pan как оптимизацию: при zoom-out/смене scale bucket это даёт белые или мигающие кадры. Если pan снова фризит, проверять JFR на `_nMakeFromBitmap`; исправление должно оставлять fallback preview и ink видимыми.
- Прямой tile render сейчас намеренно включен только для `rotationQuarters == 0`; пользовательский поворот идёт через корректный fallback full-page render + crop.
- На JVM direct tile path использует `PDFRenderer.renderPageToGraphics(...)`; перед вызовом нужно не только `fillRect(...)`, но и `g.background = Color.WHITE`. PDFBox может очистить `Graphics2D` через `clearRect(...)`, и без белого background пустые/прозрачные участки тайла становятся чёрными.
- На Android все `PdfRenderer.openPage/render/close` остаются под `PdfiumRenderLock.lock`.
- На Android каждый `Bitmap`, передаваемый в `PdfRenderer.Page.render(...)`, должен быть предварительно залит белым (`eraseColor(Color.WHITE)`) и для full-page, и для tile path. Иначе страницы с прозрачной/неявной PDF-подложкой могут выглядеть чёрными, особенно когда full-page bitmap используется как low-res preview/fallback в high-zoom tile mode.

## Проверка

- `.\gradlew.bat :rendering:impl:jvmTest`
- `.\gradlew.bat :rendering:impl:compileAndroidMain`
- `.\gradlew.bat :app:byCompose:common:compileKotlinJvm :app:byCompose:common:compileAndroidMain`
- `.\gradlew.bat :shared:detekt :rendering:impl:detekt :app:byCompose:common:detekt`
- `.\gradlew.bat :shared:ktlintCheck :rendering:impl:ktlintCheck`
- `./gradlew :rendering:impl:jvmTest :rendering:impl:compileAndroidMain :app:byCompose:common:compileAndroidMain`
- `./gradlew :rendering:impl:ktlintCheck :rendering:impl:detekt :app:byCompose:common:detekt`
- `./gradlew :app:byCompose:common:jvmTest --tests "ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfPageRendererTest"`
- `./gradlew :rendering:impl:jvmTest --tests "ru.kyamshanov.notepen.pdfviewer.PdfViewerStateHighZoomPanTest"`
- Local Desktop smoke: generated `/private/tmp/notepen-highzoom-smoke/highzoom-strokes.pdf` + sidecars with 90 strokes and `scale=400`, launched `./gradlew :app:byCompose:desktop:run --args=...`, accessibility confirmed the editor opened the file at high zoom and the app exited cleanly. This was not a pixel/pan proof: macOS screen capture was blocked and the PDF canvas does not expose rendered pixels through accessibility.
- User manual check on the problem high-zoom PDF-with-strokes scenario: reported working.

`app:byCompose:common:ktlintCheck` на момент создания КД падает на существующем нетронутом нарушении `DetailsContent.kt:168`.
