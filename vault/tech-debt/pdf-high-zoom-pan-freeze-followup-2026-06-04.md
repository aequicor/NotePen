---
genre: tech-debt
title: Follow-up по фризам high-zoom PDF pan
status: active
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

## Что проверено

- `.\gradlew.bat :rendering:impl:jvmTest :rendering:impl:ktlintCheck :rendering:impl:detekt`
- `.\gradlew.bat :app:byCompose:common:compileKotlinJvm :app:byCompose:common:compileAndroidMain :app:byCompose:common:detekt`
- `git diff --check`

Известное старое: `:app:byCompose:common:ktlintCheck` падает на нетронутом `DetailsContent.kt:168`.

## Что осталось проверить на другом ПК

- Запустить Desktop app на свежем commit и открыть PDF со страницами, где много рукописных штрихов.
- Проверить high zoom `>= 300%`:
  - pan мышью/тачпадом/средней кнопкой;
  - wheel-scroll при high zoom;
  - подгрузку соседних tiles;
  - zoom-out: страница не должна белеть, preview/ink должны оставаться видимыми;
  - annotations не должны появляться раньше PDF-подложки.
- Если фриз остаётся, снять новый JFR уже после этого commit. Старые JFR:
  - `C:/Users/kruz18/IdeaSnapshots/MainKt_2026_06_04_090354.jfr`
  - `C:/Users/kruz18/IdeaSnapshots/MainKt_2026_06_04_095332.jfr`
  были сняты до последних fixes и показывали проблему вокруг Skia bitmap/image draw path и страниц со штрихами.

## Следующие подозрения, если фриз сохранится

- Проверить в свежем JFR, остались ли samples на EDT/skiko вокруг `_nMakeFromBitmap`, `Canvas.drawPicture`, `drawImageRect` или vector stroke replay.
- Если hotspot сместился в Skia `drawPicture`, смотреть не только EDT, но и `skiko-dispatcher-to-block-on`: возможно, page composition всё ещё слишком тяжёлая после commit pan.
- Если фриз появляется только при догрузке tiles, проверить, не публикуются ли `cache.put(...)` / `tileCache.putAll(...)` сразу после коротких idle gaps во время реального движения.
- Если проблема только на страницах со штрихами, проверить размер completed ink bitmap, cache rebuild timing и оставшийся vector tail.
