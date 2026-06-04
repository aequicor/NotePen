---
genre: tech-debt
title: Follow-up по фризам high-zoom PDF pan
status: resolved
updated: 2026-06-04
source: codex
---

# Follow-up по фризам high-zoom PDF pan

## Что уже сделано

- Добавлен tile render PDF при высоком зуме: `PdfPageRenderer.renderTile(...)`, общий `PdfTileRendering.kt`, JVM/Android implementations, viewer-layer `PdfPageLayer`.
- Desktop/Android viewer теперь передают в страницу `PdfPageLayer`: full bitmap для low zoom, либо low-res preview + видимые tiles для high zoom.
- В `DrawablePdfPage` PDF рисуется в том же Canvas, что и ink, чтобы marker/highlight `BlendMode.Multiply` смешивался с пикселями PDF.
- Annotation overlay gated через `PdfPageLayer.hasVisiblePdfBase()`: strokes/highlights/notes/live layer/note badges не показываются раньше PDF-подложки.
- На Desktop добавлен `NativeImageDrawCache`: PDF/ink bitmap рисуются через cached Skia `Image`, с prewarm вне draw-фазы.
- `renderPage(...).toImageBitmap()` для reflow figures, magnifier high-res и thumbnails вынесен в background dispatcher.
- Tile publication идёт пачкой через `PdfTileCache.putAll(...)`, чтобы не делать invalidation на каждый tile.
- High-zoom pan/scroll на Desktop идёт через transient `graphicsLayer` translation и коммитится после idle/release, чтобы не пересчитывать SubcomposeLayout на каждый input event.
- Completed pen/marker ink raster cache строится асинхронно; тяжёлый cold/catching-up vector tail ограничен `shouldDrawCompletedInkTail(...)`.
- Magnifier получил отдельный completed marker bitmap cache, чтобы marker ink не возвращался на uncached vector path.
- Добавлены/обновлены КД:
  - `vault/kd/common/pdf-high-zoom-tile-rendering.md`
  - `vault/_templates/kd.md`
  - `vault/_INDEX.md`

## Что доделано 2026-06-04

- Найдены причины чёрной PDF-подложки при high zoom:
  - Android: `AndroidPdfPageRenderer.renderPage(...)` создавал full-page `Bitmap` без белой инициализации, тогда как direct tile path уже делал `eraseColor(Color.WHITE)`. Full-page bitmap используется как low-res preview/fallback для tile mode, поэтому прозрачный/пустой фон `PdfRenderer` мог выглядеть чёрным.
  - Desktop/JVM: `JvmPdfPageRenderer.renderTileDirect(...)` заливал `BufferedImage` белым, но не выставлял `Graphics2D.background`; `PDFRenderer.renderPageToGraphics(...)` может делать `clearRect(...)`, который при дефолтном чёрном background возвращал чёрные тайлы на пустой/прозрачной подложке.
- `AndroidPdfPageRenderer.renderPage(...)` теперь заполняет full-page bitmap белым перед `PdfRenderer.Page.render(...)`.
- `JvmPdfPageRenderer.renderTileDirect(...)` теперь выставляет `g.background = Color.WHITE` перед `renderPageToGraphics(...)`.
- Android tile mode теперь строит first-paint low-res preview, если страница попала в high zoom без старого bitmap в кэше. Preview ограничен через `pdfTilePreviewSize(...)`, чтобы не вернуть дорогой full-page render перед тайлами.
- Добавлены common-тесты `PdfTileRenderingTest` для preview-size helper и JVM-тесты `JvmPdfPageRendererTest` на белый фон full render / direct tile / rotated fallback tile.
- Добавлен JVM-тест `PdfViewerStateHighZoomPanTest`: high-zoom drag/wheel-scroll меняют только transient `gestureTranslation` до `commitPinchGesture()`, а обычный low-zoom drag продолжает менять committed `pan` сразу.
- Обновлена КД `vault/kd/common/pdf-high-zoom-tile-rendering.md` с инвариантами белой подложки и bounded preview.

## Что проверено

- `.\gradlew.bat :rendering:impl:jvmTest :rendering:impl:ktlintCheck :rendering:impl:detekt`
- `.\gradlew.bat :app:byCompose:common:compileKotlinJvm :app:byCompose:common:compileAndroidMain :app:byCompose:common:detekt`
- `git diff --check`
- `./gradlew :rendering:impl:jvmTest :rendering:impl:compileAndroidMain :app:byCompose:common:compileAndroidMain`
- `./gradlew :rendering:impl:ktlintCheck :rendering:impl:detekt :app:byCompose:common:detekt`
- `./gradlew :app:byCompose:common:jvmTest --tests "ru.kyamshanov.notepen.pdf.infrastructure.JvmPdfPageRendererTest"`
- `./gradlew :rendering:impl:jvmTest --tests "ru.kyamshanov.notepen.pdfviewer.PdfViewerStateHighZoomPanTest"`
- Local Desktop smoke on macOS:
  - generated `/private/tmp/notepen-highzoom-smoke/highzoom-strokes.pdf` with `.notepen.json` sidecar containing 90 saved strokes and `.notepen.json.view` with `scale=400`;
  - launched `./gradlew :app:byCompose:desktop:run --args=/private/tmp/notepen-highzoom-smoke/highzoom-strokes.pdf`;
  - accessibility tree confirmed editor opened `highzoom-strokes.pdf`, restored high zoom (`400%`, then `800%` after toolbar-focused key input), page `1 / 1`;
  - app exited cleanly, `BUILD SUCCESSFUL`.

Известное старое: `:app:byCompose:common:ktlintCheck` падает на нетронутом `DetailsContent.kt:168`.

## Ручная проверка

- Пользователь проверил свежие изменения на проблемном сценарии и сообщил, что high-zoom PDF со штрихами работает.
- Локальный smoke выше не был pixel-proof: в macOS-сандбоксе screen capture был заблокирован (`screencapture` не смог создать изображение), а Compose Canvas не отдавал PDF pixels/gesture state через accessibility. Финальная confidence по визуальному поведению основана на проверке пользователя на реальном сценарии.
- Если фриз остаётся, снять новый JFR уже после этого commit. Старые JFR:
  - `C:/Users/kruz18/IdeaSnapshots/MainKt_2026_06_04_090354.jfr`
  - `C:/Users/kruz18/IdeaSnapshots/MainKt_2026_06_04_095332.jfr`
  были сняты до последних fixes и показывали проблему вокруг Skia bitmap/image draw path и страниц со штрихами.

## Следующие подозрения, если фриз сохранится

- Проверить в свежем JFR, остались ли samples на EDT/skiko вокруг `_nMakeFromBitmap`, `Canvas.drawPicture`, `drawImageRect` или vector stroke replay.
- Если hotspot сместился в Skia `drawPicture`, смотреть не только EDT, но и `skiko-dispatcher-to-block-on`: возможно, page composition всё ещё слишком тяжёлая после commit pan.
- Если фриз появляется только при догрузке tiles, проверить, не публикуются ли `cache.put(...)` / `tileCache.putAll(...)` сразу после коротких idle gaps во время реального движения.
- Если проблема только на страницах со штрихами, проверить размер completed ink bitmap, cache rebuild timing и оставшийся vector tail.
