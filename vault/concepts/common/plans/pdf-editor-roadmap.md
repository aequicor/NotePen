---
genre: concept
title: "Roadmap: PDF Editor M1–M8"
topic: roadmap
module: common
triggers:
  - "roadmap"
  - "milestones"
  - "m1 m2 m3 m4 m5 m6 m7 m8"
  - "план этапы"
  - "pdf editor plan"
confidence: high
source: ai
updated: 2026-05-15T00:00:00Z
---

# Roadmap: PDF Editor M1–M8

**Продукт:** мультиплатформенный редактор PDF с рукописными аннотациями, синхронизацией и режимом проекции.  
**Платформы:** Android (minSdk 24) · Desktop JVM (macOS/Windows/Linux) · iOS 16+ · Web (Kotlin/Wasm)  
**Сервер:** Ktor (координация синхронизации)

---

## Контекст и архитектурные инварианты

- Архитектура: Clean Architecture (domain → application → presentation/data). Нарушения фиксировать как tech-debt.
- DI: Koin на всех таргетах.
- Async: Coroutines + Flow. `Dispatchers.*` только через инжектируемый `CoroutineDispatcher`.
- Хранение аннотаций: отдельно от PDF (JSON/Protobuf), оригинал не перезаписывается.
- PDF-рендеринг абстрагирован через `expect`/`actual` — платформенные библиотеки изолированы за портами в `:shared`.

## Definition of Done (общий для каждого M)

1. Код собирается на всех целевых таргетах данного этапа без warning'ов в `:shared`.
2. Новые публичные API в `:shared` покрыты unit-тестами (цель ≥80% для domain-слоя).
3. Smoke-test проведён на ≥2 платформах данного этапа (отчёт с шагами в `guidelines/<module>/reports/`).
4. ADR обновлён (или создан) если принято нетривиальное архитектурное решение.
5. Нет регрессий в предыдущих milestone (regression-чеклист в конце каждого smoke-отчёта).
6. `./gradlew check` (ktlint + detekt + тесты) зелёный.

---

## M1 — Каркас и просмотр PDF

**Цель:** открыть локальный PDF, плавно скроллить и зумировать на Android и Desktop.  
**Таргеты в M1:** Android + Desktop (iOS и Web — в M1b после настройки окружения).  
**Статус:** PLANNING

### Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Порты `PdfDocumentLoader`, `PdfPageRenderer` в `:shared/domain/pdf/port` | Clean Architecture: domain без Compose/Android зависимостей |
| `PdfPageData(widthPx, heightPx, argbPixels: IntArray)` вместо `ImageBitmap` в domain | `:shared` остаётся без зависимости на Compose |
| Desktop: Apache PDFBox 3.0.5 | Уже в проекте, pure Java, хорошее качество |
| Android: `android.graphics.pdf.PdfRenderer` (built-in, API 21+) | Нулевой overhead, безопасно при minSdk 24 |
| iOS (M1b): PDFKit через Kotlin/Native cinterop | Нативное качество, без сторонних зависимостей |
| Web (M1b): PDF.js через Kotlin/Wasm `@JsModule` interop | De facto стандарт для PDF в браузере |

### Коммиты

```
Commit 1  feat(shared/pdf): domain ports
          — PdfDocument (interface), PdfPageInfo, PdfPageData
          — PdfDocumentLoader (port), PdfPageRenderer (port)
          — Unit-тесты в commonTest (мок-реализации)

Commit 2  feat(common/jvm): PDFBox implementation
          — JvmPdfDocumentLoader, JvmPdfPageRenderer
          — Рефакторинг PdfLoader / DrawablePdfPage → реализуют порты

Commit 3  feat(common/android): PdfRenderer implementation
          — AndroidPdfDocumentLoader, AndroidPdfPageRenderer

Commit 4  refactor(common): PdfManager + ScrollablePdfColumn на портах
          — PdfManager принимает loader/renderer через DI (Koin)

Commit 5  feat(common): zoom/pan + page virtualization
          — Pinch-to-zoom 25%–800%, Ctrl+wheel Desktop
          — Рендерим только visible ± 1 страниц

Commit 6  docs: ADR-001 — PDF rendering strategy per platform
```

### Риски

| # | Риск | Митигация |
|---|------|-----------|
| R1 | `PdfPageData.argbPixels` как `IntArray` → большое allocation на высокодpi | Сначала реализовать, профилировать в M8 |
| R2 | PDFBox: многостраничные документы держат весь PDF в памяти | `PDDocument.load` + `close()` per page; LRU кеш страниц |
| R3 | Android `PdfRenderer` не thread-safe | Защитить вызовы `Mutex` внутри renderer |

---

## M1b — iOS и Web просмотр PDF

**Цель:** добавить iOS и Web таргеты; PDF открывается на всех 4 платформах.  
**Зависимость:** M1 завершён.

### Коммиты

```
Commit 1  chore: iOS и Wasm таргеты в :shared и :common
          — iosArm64, iosSimulatorArm64, iosX64 в shared/build.gradle.kts
          — wasmJs в shared/build.gradle.kts
          — expect/actual UuidGenerator, UnicodeNormalization для новых таргетов

Commit 2  feat(iosApp): SwiftUI shell + ComposeUIViewController
          — Новый Gradle-модуль :app:byCompose:ios
          — Entry point, Xcode project skeleton

Commit 3  feat(common/ios): PDFKit cinterop implementation
          — IosPdfDocumentLoader, IosPdfPageRenderer

Commit 4  feat(webApp): Kotlin/Wasm модуль + HTML entry point
          — Новый Gradle-модуль :app:byCompose:web
          — PDF.js interop через @JsModule

Commit 5  feat(common/wasm): PDF.js rendering implementation
          — WasmPdfDocumentLoader, WasmPdfPageRenderer
```

---

## M2 — Рукописный ввод

**Цель:** рисование поверх PDF; штрихи сохраняются локально. Сначала Android + Desktop, затем iOS, затем Web.  
**Зависимость:** M1 завершён.  
**Статус:** PLANNING

### Ключевые решения

| Решение | Обоснование |
|---------|-------------|
| Штрих = `Stroke(tool, color, width, points: List<StrokePoint>)`, `StrokePoint(x, y, pressure, tiltX, tiltY, timestampMs)` | Векторное хранение, возможность replay и re-render |
| Предиктивный overlay-слой | Перерисовываем только последний сегмент; остальные страницы не трогаем |
| Сглаживание: Catmull-Rom spline | Хороший баланс качество/простота; Bézier — как опция в M8 |
| Palm rejection Android: фильтр `TOOL_TYPE_FINGER` при активном `TOOL_TYPE_STYLUS` | Стандартная техника; Apple Pencil на iOS через `UITouch.type` |
| Ластик: объектный (удаляет весь штрих при пересечении) | Достаточно для M2; пиксельный — M3 |
| Undo/redo: `ArrayDeque<StrokeAction>`, лимит 100 | Простейшая реализация без CRDT |
| Хранение: JSON-файл per document (`<docId>/annotations.json`) через `AnnotationRepository` | Уже частично реализовано; привести к новой модели `Stroke` |

### Коммиты

```
Commit 1  feat(shared/annotation): доменная модель Stroke + StrokePoint
          — Stroke, StrokePoint, AnnotationLayer (per page)
          — Unit-тесты сериализации

Commit 2  feat(shared/annotation): порты AnnotationRepository
          — interface AnnotationLoader, AnnotationSaver

Commit 3  feat(common): drawing canvas overlay
          — DrawingCanvas Composable поверх PDF-страницы
          — Catmull-Rom spline rendering

Commit 4  feat(common/android): stylus input + palm rejection
          — Pressure, tilt из MotionEvent
          — TOOL_TYPE_FINGER фильтрация

Commit 5  feat(common/jvm): graphics tablet support
          — Pointer events с axis pressure/tilt на Desktop

Commit 6  feat(common): предиктивный overlay + undo/redo
          — Separate overlay Canvas для in-progress stroke
          — UndoRedoManager (ArrayDeque)

Commit 7  feat(common): persist annotations (JSON)
          — AnnotationRepositoryImpl (jvmMain + androidMain)
          — Автосохранение по завершении штриха

Commit 8  feat(common/ios): Apple Pencil input (M2-ios)
          — UITouch.type фильтрация через cinterop
          — PencilKit как опция (решение в M2-ios spec)
```

### Риски

| # | Риск | Митигация |
|---|------|-----------|
| R1 | Задержка ввода > 16ms на слабых устройствах | Overlay-слой рисует только последний сегмент; полный перерендер по завершении |
| R2 | Catmull-Rom может давать артефакты при резких движениях | Velocity-threshold: при высокой скорости — линейная интерполяция |
| R3 | Большие JSON-файлы аннотаций (тысячи штрихов) | Профилировать в M8; как опция — Protobuf в M6 |

---

## M3 — Палитра инструментов

**Цель:** маркер, ластик (объектный + пиксельный), выбор цвета и толщины, навигация по страницам, миниатюры.  
**Зависимость:** M2 завершён.  
**Статус:** PLANNING

### Что добавляем

| Инструмент | Особенности |
|-----------|-------------|
| Маркер | Полупрозрачный (alpha ~50%), широкий штрих |
| Ластик объектный | Удаляет весь `Stroke` при пересечении (уже в M2) |
| Ластик пиксельный | Вырезает по маске из `Stroke.points`; сложнее, но ожидается |
| Выбор цвета | Предустановленная палитра + custom color picker |
| Выбор толщины | Слайдер 0.5px–20px с live preview |
| Миниатюры страниц | `LazyRow` / боковая панель; рендер in background |
| Jump-to-page | Поле ввода номера + навигация |
| Поиск по тексту | `PDFDocument` text extraction → highlight результатов |

### Коммиты

```
Commit 1  feat(shared): ToolMode расширение (Marker, PixelEraser)
          — Marker: доменная модель с opacity
          — PixelEraser: маска по точкам пересечения

Commit 2  feat(common): UI маркера и пиксельного ластика

Commit 3  feat(common): color picker + толщина в ToolSettingsPanel

Commit 4  feat(common): миниатюры страниц (PageThumbnailsPanel)
          — Рендер ThumbnailRepository в background dispatcher

Commit 5  feat(common): поиск по тексту
          — PDFBox TextStripper (jvmMain), PDFKit search (iosMain)
          — Highlight результатов overlay
```

---

## M4 — Экспорт аннотаций в PDF

**Цель:** «вжечь» рукописные аннотации в PDF (flatten) и сохранить копию.  
**Зависимость:** M3 завершён.  
**Статус:** PLANNING

### Библиотеки per platform

| Платформа | Библиотека | Примечание |
|-----------|-----------|------------|
| Desktop (JVM) | Apache PDFBox — `PDPageContentStream` | Рисуем кривые через PDF path operators |
| Android | iText7 Community или PDFBox-Android | PDFBox не портирован; iText7 — AGPL |
| iOS | PDFKit `PDFPage.draw(with:)` + CoreGraphics | Рендерим аннотации в CGContext, сохраняем через `PDFDocument.write(to:)` |
| Web | pdf-lib.js через Kotlin/Wasm interop | Зрелая библиотека, хорошая документация |

### Коммиты

```
Commit 1  feat(shared/export): порт PdfExporter
          — interface PdfExporter { suspend fun export(doc, annotations, outputPath) }

Commit 2  feat(common/jvm): PDFBox flatten implementation

Commit 3  feat(common/android): iText7 flatten implementation

Commit 4  feat(common/ios): PDFKit + CoreGraphics flatten

Commit 5  feat(common/wasm): pdf-lib.js flatten

Commit 6  feat(common): Export UI (кнопка, progress, file picker)
```

### Риски

| # | Риск | Митигация |
|---|------|-----------|
| R1 | iText7 AGPL лицензия на Android | Использовать iText Community; проверить совместимость с лицензией проекта |
| R2 | Catmull-Rom кривые → PDF path: точность аппроксимации | Бézier cubic достаточен; тест визуального сравнения |

---

## M5 — Сервер и Discovery

**Цель:** Ktor-сервер, mDNS-обнаружение в LAN, парное подключение, передача документов.  
**Зависимость:** M1b завершён.  
**Статус:** PLANNING

### Архитектура

```
Host device
  └─ KtorServer (`:server` Gradle module)
       ├─ mDNS registration (JmDNS / NsdManager / NetService)
       ├─ WebSocket endpoint /ws
       ├─ REST endpoint /documents (chunked upload/download)
       └─ TLS (self-signed, fingerprint pinning)

Client devices
  └─ KtorClient (`:shared` commonMain)
       ├─ mDNS discovery
       ├─ WebSocket connection
       └─ Pairing via 6-digit code / QR
```

### mDNS per platform

| Платформа | Библиотека |
|-----------|-----------|
| Desktop / Server | JmDNS |
| Android | `android.net.nsd.NsdManager` |
| iOS | `Network.framework` (via cinterop) |
| Web | Fallback через HTTP-реестр на host |

### Коммиты

```
Commit 1  chore: новый Gradle-модуль :server (Ktor server JVM)

Commit 2  feat(server): Ktor WebSocket + REST endpoints
          — /ws (WebSocket, typed messages)
          — /documents (chunked transfer)
          — TLS self-signed cert generation

Commit 3  feat(shared): NetworkMessage sealed hierarchy
          — PairRequest, PairAccepted, DocumentChunk, Ack, ...

Commit 4  feat(shared): KtorSyncClient (WebSocket client commonMain)

Commit 5  feat(common/jvm): JmDNS discovery + registration

Commit 6  feat(common/android): NsdManager discovery

Commit 7  feat(common/ios): Network.framework discovery (cinterop)

Commit 8  feat(common): Pairing UI (6-digit code + QR display)

Commit 9  feat(common): Document transfer UI (progress)
```

### Риски

| # | Риск | Митигация |
|---|------|-----------|
| R1 | mDNS не работает в некоторых корпоративных сетях | Fallback: ручной ввод IP:port |
| R2 | Self-signed TLS: Android требует network security config | `network_security_config.xml` с fingerprint whitelist |
| R3 | Большой PDF (>100 MB) — timeout при передаче | Chunked transfer с resume; прогресс-бар |

---

## M6 — Синхронизация штрихов

**Цель:** real-time трансляция штрихов между устройствами; конфликты, offline-очередь.  
**Зависимость:** M5 завершён.  
**Статус:** PLANNING

### Стратегия конфликтов

- Штрих — атомарная единица (`Stroke` с UUID и `authorDeviceId`).
- Создание: append-only, конфликтов нет.
- Удаление: tombstone с `deletedAt` (logical clock) + `deletedBy`.
- Last-writer-wins по штриху (достаточно для non-collaborative рисования).
- Offline-очередь: `Flow<PendingOperation>` сохраняется в SQLDelight, replay при reconnect.

### Коммиты

```
Commit 1  feat(shared): StrokeSync доменная модель
          — StrokeDelta (Added, Removed), LogicalClock, Tombstone

Commit 2  feat(shared): SyncQueue (SQLDelight, commonMain)
          — PendingOperation persist + replay

Commit 3  feat(server): WebSocket broadcast stroke deltas
          — Fan-out to all connected clients

Commit 4  feat(shared): SyncEngine (OT-lite)
          — Merge incoming deltas с локальным состоянием
          — Conflict: tombstone wins over add with earlier clock

Commit 5  feat(common): real-time stroke появление у viewer
          — Incoming StrokeDelta → immediate render на canvas

Commit 6  feat(common): offline-queue UI indicator
          — Иконка «не синхронизировано» + retry
```

### Риски

| # | Риск | Митигация |
|---|------|-----------|
| R1 | Очередь растёт при длительном offline | Лимит 10 000 операций; warn user |
| R2 | Logical clock drift при многих устройствах | Hybrid Logical Clock (HLC) — рассмотреть в M8 если нужно |

---

## M7 — Режим проекции (Mirroring)

**Цель:** ведущее устройство транслирует текущую страницу, viewport, указку и штрихи в реальном времени; ведомые могут «отстегнуться».  
**Зависимость:** M6 завершён.  
**Статус:** PLANNING

### Протокол

```kotlin
// Новый тип WebSocket сообщения (добавляется в NetworkMessage)
@Serializable
data class ProjectionFrame(
    val page: Int,
    val viewportX: Float,
    val viewportY: Float,
    val viewportScale: Float,
    val pointerX: Float?,
    val pointerY: Float?,
    val strokeDelta: StrokeDelta?,
)
```

- Частота кадров viewport: 30 fps (throttle `conflate()`).
- Штрихи: каждая точка сразу, без throttle (latency goal ≤100ms).
- Ведомый в режиме «следования» блокирует собственный scroll/zoom.
- Кнопка «отстегнуться» / «прицепиться» — toggle без разрыва WebSocket.

### Коммиты

```
Commit 1  feat(shared): ProjectionFrame в NetworkMessage
          — Сериализация, версионирование

Commit 2  feat(server): ProjectionBroadcastService
          — Fan-out ProjectionFrame всем viewer'ам
          — Отдельный поток от SyncEngine (разные каналы)

Commit 3  feat(common): ProjectionHostController
          — Emit ProjectionFrame при scroll/zoom/pointer/stroke
          — conflate() для viewport, pass-through для stroke

Commit 4  feat(common): ProjectionViewerController
          — Apply incoming ProjectionFrame к локальному viewport
          — «Follow»/«Free» state machine

Commit 5  feat(common): Projection UI
          — Host: индикатор «транслируется»
          — Viewer: индикатор «следую за <device>» + кнопка отстегнуться
```

### Риски

| # | Риск | Митигация |
|---|------|-----------|
| R1 | WiFi jitter → viewport скачет у viewer | Exponential smoothing входящего viewport |
| R2 | Латентность > 100ms на загруженной сети | Separate WebSocket frame приоритет для stroke vs viewport |

---

## M8 — Полировка

**Цель:** производительность, accessibility, релизные сборки, bagfix-sprint.  
**Зависимость:** M7 завершён.  
**Статус:** PLANNING

### Чеклист

#### Производительность
- [ ] Профиль рендеринга: Systrace (Android), Instruments (iOS), JFR (Desktop).
- [ ] Целевой FPS 60 при скролле/зуме на Pixel 6, iPad Air, среднем ноутбуке.
- [ ] Замена `IntArray` в `PdfPageData` на `ByteArray` если даёт выигрыш по GC.
- [ ] Input latency measurement: stylus point → pixel, цель < 16ms.
- [ ] Bézier vs Catmull-Rom: A/B качество штриха, финальный выбор.
- [ ] LRU кеш страниц PDF: профилировать размер, подобрать capacity.

#### Accessibility
- [ ] Минимальные tap-targets 44pt на всех платформах.
- [ ] Контрастность палитры по WCAG AA.
- [ ] TalkBack (Android) и VoiceOver (iOS) — базовые labels на toolbar.
- [ ] Keyboard navigation на Desktop (Tab, Enter, Escape).
- [ ] Системные шрифты / Dynamic Type на iOS.

#### Релизные сборки
- [ ] ProGuard/R8 Android: проверить shrinking для iText7 / kotlinx.serialization.
- [ ] Desktop: DMG (macOS), MSI (Windows), Deb (Linux) через Compose Desktop packaging.
- [ ] iOS: Archive + распространение через TestFlight.
- [ ] Web: production bundle минификация (Kotlin/Wasm + webpack).

#### Документация
- [ ] README: быстрый старт, сборка per platform.
- [ ] ADR-001 до ADR-N: финальный пересмотр.
- [ ] CHANGELOG.md: M1–M8 summary.

### Риски

| # | Риск | Митигация |
|---|------|-----------|
| R1 | ProGuard удаляет kotlinx.serialization reflective adapter | `@Keep` / proguard rules для serialization |
| R2 | Kotlin/Wasm бинарник слишком большой (> 20MB) | Проверить size budget, lazy loading PDF.js |

---

## Сводная таблица milestone'ов

| Milestone | Таргеты | Ключевые фичи | Зависимость |
|-----------|---------|--------------|-------------|
| **M1** | Android, Desktop | PDF open/zoom/scroll, CA-рефакторинг, domain ports | — |
| **M1b** | iOS, Web | Те же фичи + новые таргеты | M1 |
| **M2** | Android, Desktop, iOS, Web | Кисть, давление, undo/redo, локальное сохранение | M1b |
| **M3** | All | Маркер, ластик, палитра, миниатюры, поиск | M2 |
| **M4** | All | Экспорт (flatten в PDF) | M3 |
| **M5** | All + Server | Ktor, mDNS, pairing, document transfer | M1b |
| **M6** | All + Server | Real-time sync штрихов, offline queue | M5 |
| **M7** | All + Server | Projection mode (host/viewer) | M6 |
| **M8** | All | Производительность, accessibility, релизы | M7 |
