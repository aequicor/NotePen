---
genre: reference
title: "Regression Case Catalog (RC-*)"
topic: testing
triggers: ["regression", "regression case", "RC-"]
confidence: high
source: derived
updated: 2026-05-30T00:00:00Z
---

# NotePen — Regression Case Catalog (RC-*)

These regression guards are mined from shipped fixes; each row's field shape follows [TEST-CASE-STANDARD.md](TEST-CASE-STANDARD.md) and the three-tier model defined in [/TESTING.md](../TESTING.md). Status starts at PEND — a 🔴/🟠 case whose Coverage reads `needed` is a Tier-1 automation gap still to be closed.

## Status legend

Status: PEND · PASS · FAIL · SKIP — Severity: 🔴 critical · 🟠 regression · 🟡 polish

### ANDROID — Android actuals & gestures

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-ANDROID-001 | 🟠 | 1 | unit | Pinch не переверстывает layout: zoom/pan не меняются во время жеста, только gestureScale/gestureTranslation | During an active pinch only gestureScale/gestureTranslation change; zoom and pan stay at their pre-gesture committed values. | 8793f80 | needed | PEND |
| RC-ANDROID-002 | 🟠 | 1 | unit | commitPinchGesture атомарно впекает gesture-state в zoom/pan и сбрасывает scale в identity (без скачка) | Committed zoom = clamped(zoom*gestureScale), pan = clamped(pan*scale + translation); gesture state reset to identity; second call is idempotent. | 8793f80 | needed | PEND |
| RC-ANDROID-003 | 🟡 | 1 | unit-edge | Cursor-anchor инвариант пинча: точка под центроидом остаётся под пальцами, в т.ч. на упоре в MIN/MAX zoom | The document point under the centroid stays under the moving centroid for both in-range and clamped zoom; no drift at the zoom limit. | 8793f80 | needed | PEND |
| RC-ANDROID-004 | 🟠 | 2 | visual | Резкий двухпальцевый pinch остаётся плавным (нет переверстки/ре-растеризации каждый кадр) | Pinch zoom animates smoothly frame-to-frame on the GPU layer; on finger-up the page settles to the new zoom with no visible snap/jump. | 8793f80 | → AV-ANDROID-01 | PEND |
| RC-ANDROID-005 | 🟠 | 1 | unit | Tool-меню на Android закреплены сверху, на desktop остаются снизу | ToolMenusAtTop == true on Android, == false on desktop/JVM; toolbar/settings alignment follows the flag. | 9ebe08d | needed | PEND |
| RC-ANDROID-006 | 🟡 | 2 | visual | Панель настроек инструмента сверху раскрывается вниз (expandDownward) и сидит ниже PageIndicatorAirbar | Top-anchored settings panel: slides from top, expands downward, sits below the page indicator and under the status-bar inset, fully visible. | 9ebe08d | → AV-ANDROID-02 | PEND |
| RC-ANDROID-007 | 🟠 | 2 | gesture | Одно-пальцевый скролл PDF идёт в направлении пальца (drag вниз = контент вниз) | Content tracks the finger — downward drag moves the page down, upward drag moves it up; no inverted scroll. | c018b3a | → AV-ANDROID-03 | PEND |
| RC-ANDROID-008 | 🔴 | 2 | error | Конкурентный рендер/закрытие 4 PdfRenderer-инстансов не падает с нативным SIGSEGV (FT_Done_Face) | No native crash: all concurrent PdfRenderer open/render/close run serialized through PdfiumRenderLock. | d65511e | → AV-ANDROID-04 | PEND |
| RC-ANDROID-009 | 🔴 | 1 | unit | Все четыре пользователя PdfRenderer берут общий PdfiumRenderLock (per-page, без suspend под локом) | Every pdfium open/render/close in all four users is guarded by the single PdfiumRenderLock.lock, per page, with no suspension under the lock. | d65511e | needed | PEND |

### ANNOT — Annotation persistence

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-ANNOT-001 | 🔴 | 2 | e2e | Аннотации по content:// URI сохраняются и восстанавливаются в приватное хранилище | The strokes drawn before close are present again on page 1 after reopening; no FileNotFoundException in logcat during save. | 0e3d4e9 | → AV-ANNOT-01 | PEND |
| RC-ANNOT-002 | 🟠 | 1 | unit | Per-point pressure/tilt сохраняются в DrawingPointDto и переживают round-trip | Loaded pressure/tilt match the saved values; legacy JSON without these fields still loads with pressure=1f, tilt=0f. | 0e3d4e9 | needed | PEND |
| RC-ANNOT-003 | 🟠 | 2 | e2e | Имя документа для content:// URI берётся из ContentResolver, а не из сегмента URI | Tab/title shows the human-readable file name, not a URL-encoded opaque id. | 0e3d4e9 | → AV-ANNOT-02 | PEND |
| RC-ANNOT-004 | 🔴 | 2 | gesture | Системная кнопка/жест «назад» в редакторе сохраняет аннотации перед уходом | The stroke drawn right before pressing back is persisted and visible after reopening. | 0e3d4e9 | → AV-ANNOT-03 | PEND |
| RC-ANNOT-005 | 🟠 | 2 | e2e | Изменение настроек инструмента без рисования всё равно сохраняется автосейвом | The changed tool setting (thickness/color/eraser mode) is restored after reopening. | 0e3d4e9 | → AV-ANNOT-04 | PEND |
| RC-ANNOT-006 | 🟠 | 2 | visual | Завершённые штрихи не исчезают во время пинч-зума — масштабируются вместе со страницей | Strokes remain visible and scale together with the PDF page throughout the pinch; never disappear. | 0e3d4e9 | → AV-ANNOT-05 | PEND |
| RC-ANNOT-007 | 🔴 | 1 | unit-edge | Сохранение/загрузка документа с сотнями тысяч точек не падает с OOM (потоковый JSON) | Save and load complete successfully (streamed); a partial write never leaves corrupt JSON (temp-file + rename). | 9662d35 | needed | PEND |
| RC-ANNOT-008 | 🟠 | 1 | unit | StrokeSimplifier (RDP) прореживает прямые, но удерживает повороты и профиль нажатия | Collinear points collapse, geometric corners survive, pressure/tilt deviations keep their points, short lists returned as-is. | 9662d35 | drawing/api/src/commonTest/kotlin/ru/kyamshanov/notepen/annotation/domain/StrokeSimplifierTest.kt | PEND |
| RC-ANNOT-009 | 🟠 | 1 | unit | Лёгкий view-state сайдкар: зум/страница восстанавливаются отдельно от тяжёлых штрихов | loadViewState returns the saved scale/page/offset; null when absent; a distinct .view sidecar file is created on save. | 9662d35 | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvmTest.kt | PEND |
| RC-ANNOT-010 | 🟠 | 2 | visual | Открытие исписанной страницы: нет позднего скачка зума и нет длинного лага первого рендера | The page appears at the saved zoom immediately and the first render is smooth with no prolonged main-thread freeze. | 9662d35 | → AV-ANNOT-06 | PEND |

### COMMON — Shared UI & layout

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-COMMON-001 | 🟡 | 2 | visual | Портретный топ-бар: инструменты/делитель/сегмент B прижаты вправо, панель настроек выровнена вправо | Tools, divider and segment B pinned to the right; settings expansion panel right-aligned under segment B, not centered. | 47d194c | → AV-COMMON-01 | PEND |
| RC-COMMON-002 | 🟠 | 2 | gesture | Вход в режим чтения сразу даёт фокус: первый Right/Space листает страницу | Right after activating reading mode the first Right/Space turns the page — focus is already on the root handler. | bf14335 | → AV-COMMON-02 | PEND |
| RC-COMMON-003 | 🟡 | 1 | unit | TOC ebook→PDF не содержит плейсхолдер-заголовков (… / *** / * * *), но блок всё ещё рисуется | TOC excludes placeholder and empty headings, but the corresponding Heading block stays in the reading flow. | bf14335 | needed | PEND |
| RC-COMMON-004 | 🟠 | 3 | manual | Поворот на EMUI: раскладка редактора переключается сразу, без отставания на один поворот | Editor layout and adaptive library/catalog width switch immediately on rotation in both directions, no one-rotation lag. | fd0ce49 | release-checklist | PEND |

### DESKTOP — Desktop / JBR / packaging

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-DESKTOP-001 | 🟠 | 3 | manual | Окно открывается развёрнутым (MAXIMIZED_BOTH) после установки кастомного JBR-титлбара | Window opens maximized rather than at the default restored size. | 044978e | release-checklist | PEND |
| RC-DESKTOP-002 | 🟠 | 1 | unit | На Windows и macOS DnD-таргеты не регистрируются, на Linux — да | isDragAndDropSupported is false on Windows and macOS, true on Linux. | 044978e | needed | PEND |
| RC-DESKTOP-003 | 🟠 | 2 | visual | Миниатюры PDF/изображений видимы на Windows-десктопе после отключения DnD | Each PDF/image card shows its rendered thumbnail, not a blank/placeholder tile. | 044978e, c37bd01 | → AV-DESKTOP-01 | PEND |
| RC-DESKTOP-004 | 🟠 | 1 | unit | PDFBox-рендер миниатюры использует RenderDestination.EXPORT + ImageType.RGB | A valid TYPE_INT_ARGB bitmap of widthPx is produced from the export-path render. | c37bd01 | needed | PEND |
| RC-DESKTOP-005 | 🟠 | 1 | unit | Страница центрируется ровно один раз при первом open, даже если pan уже был сдвинут | Centering applies once on first validity even if pan was pre-moved; subsequent calls do not re-center. | 0e16f1c | needed | PEND |
| RC-DESKTOP-006 | 🟠 | 2 | visual | Страница визуально отцентрована по горизонтали при первом открытии документа | The page sheet sits horizontally centered with symmetric left/right gaps on first open. | 0e16f1c, 788a4fc | → AV-DESKTOP-02 | PEND |
| RC-DESKTOP-007 | 🟠 | 1 | unit | clampPan не затирает pan на оси, где контент помещается в viewport | On a fitting axis clampPan returns the input pan unchanged; on an overflowing axis it clamps into bounds. | 788a4fc | needed | PEND |
| RC-DESKTOP-008 | 🟡 | 1 | unit-edge | Начальное центрирование в целочисленных пикселях даёт симметричные зазоры (≤1px) | Left and right pixel gaps are within 1px of each other. | 788a4fc | needed | PEND |
| RC-DESKTOP-009 | 🟠 | 1 | unit | Вертикальный и горизонтальный wheel/trackpad-скролл двигает документ в правильную сторону | panBy is invoked with -delta.x and -delta.y for plain scroll, -delta.y on X for shift-scroll. | 121d478, be5aefa | needed | PEND |
| RC-DESKTOP-010 | 🟠 | 2 | gesture | Скроллбары перетаскиваются мышью (вынесены из перехватывающего pointer-input Box) | Dragging the scrollbar thumb scrolls the document; the press is not swallowed by drag-to-pan. | be5aefa | → AV-DESKTOP-03 | PEND |
| RC-DESKTOP-011 | 🟠 | 1 | unit | macOS trackpad pinch: pinch-out увеличивает (factor = 1 + delta.y) | Small-delta magnify uses 1+delta.y so pinch-out zooms in; discrete-wheel exponential path unchanged. | be5aefa | needed | PEND |
| RC-DESKTOP-012 | 🔴 | 3 | manual | Win-перо: первое движение не задерживается на ~400мс и нет 'линии из центра' (WM_POINTER bypass AWT) | Stroke begins immediately at the pen-down point with no stall and no straight line from center; pressure varies. | 1dd6365 | release-checklist | PEND |
| RC-DESKTOP-013 | 🟡 | 1 | unit-edge | penPointerEvents имеет no-op-дефолт: платформы без native-стрима публикуют пустой flow | Default penPointerEvents is an empty SharedFlow that never emits and never blocks collectors. | 1dd6365 | needed | PEND |
| RC-DESKTOP-014 | 🟠 | 2 | visual | Длинный pen-штрих не даёт lag→прямая-линия артефакт (переиспользование Path) | The curve renders smoothly with no momentary freeze followed by a straight chord across the stroke. | 93988e2 | → AV-DESKTOP-04 | PEND |
| RC-DESKTOP-015 | 🟠 | 3 | manual | Pen-штрих гладкий при быстром письме: WM_POINTER history дренажируется (250Гц поток) | Fast strokes render as a smooth dense curve, not a low-rate stepped polyline. | f907028 | release-checklist | PEND |
| RC-DESKTOP-016 | 🟡 | 1 | unit | Только Ctrl+wheel zoom-бёрст обновляет gestureScale/gestureTranslation; commit перед non-zoom событиями | Transient gesture state accumulates during burst; commitPinchGesture bakes it once and is a no-op at identity. | 7c17d8b | needed | PEND |
| RC-DESKTOP-017 | 🟠 | 1 | unit | PdfBitmapCache: эвикция по pixel-ceiling и evictStaleScale защищает видимые страницы | Both maxEntries and maxTotalPixels limits hold; evictStaleScale drops only stale off-screen entries. | 86b5265 | needed | PEND |
| RC-DESKTOP-018 | 🟠 | 2 | visual | Резкий zoom-out не лагает: стейл-рендеры отменяются (collectLatest), большие битмапы дропаются | Zoom-out stays responsive with no multi-second freeze; the page resolves to a sharp render after pause. | 86b5265 | → AV-DESKTOP-05 | PEND |
| RC-DESKTOP-019 | 🔴 | 3 | manual | Release-сборка: ProGuard сохраняет sqlite-драйвер и enum-члены (старт без NPE/краша) | Release app starts with no startup exception; sync DB opens and JBR title bar activates. | 6d9c3f0 | release-checklist | PEND |
| RC-DESKTOP-020 | 🟠 | 3 | manual | Release-сборка: ProGuard optimize включён без IncompleteClassHierarchyException | proguardReleaseJars builds with no IncompleteClassHierarchyException and optimization on. | 3e666e6 | release-checklist | PEND |
| RC-DESKTOP-021 | 🔴 | 3 | manual | Release-сборка: JBIG2 ImageIO SPI сохранён → сканированные (JBIG2) PDF рендерятся | The scanned PDF renders without an ImageIO 'Provider not found' crash. | c96592b | release-checklist | PEND |
| RC-DESKTOP-022 | 🟠 | 3 | manual | Run/package на JBR: кастомный титлбар активируется; Windows ставится per-user EXE (без MSI) | Custom JBR title bar renders in :run and packaged build; Windows installer is a per-user EXE requiring no admin. | c429ec5 | release-checklist | PEND |
| RC-DESKTOP-023 | 🟡 | 3 | manual | PDFBox font-fallback WARN-спам приглушён (логгер org.apache.pdfbox = ERROR) | No per-glyph PDFBox font-fallback WARN spam; genuine PDFBox ERROR-level messages still appear. | f01cead | release-checklist | PEND |

### EDITOR — Editor view-state

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-EDITOR-001 | 🟠 | 2 | visual | Верхний chrome редактора учитывает вырез камеры (notch), а не только статус-бар | In portrait with a top notch, the workspace grid and top-row tab strips are pushed down to clear the display cutout. | 35b1a30 | → AV-EDITOR-01 | PEND |
| RC-EDITOR-002 | 🟠 | 2 | visual | В ландшафте боковой вырез не перекрывает крайние tab-strip панелей | In landscape the editor is inset from the horizontal cutout so outer tab strips never slide under the side notch; rail inset once. | f2c261c | → AV-EDITOR-02 | PEND |
| RC-EDITOR-003 | 🔴 | 2 | gesture | После возврата фокуса (фон/шторка уведомлений) редактор снова принимает касания | After focus is regained, touches on the editor are accepted and drawing works; the stale touch layer is torn down. | faa10c5 | → AV-EDITOR-03 | PEND |
| RC-EDITOR-004 | 🟠 | 1 | unit | saveViewState/loadViewState сохраняют readingMode (round-trip) | The .view sidecar round-trips all four fields including readingMode. | b505080 | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvmTest.kt:saveViewState_roundTrip_returnsReadingMode | PEND |
| RC-EDITOR-005 | 🟠 | 1 | unit-edge | Сейв штрихов не затирает ранее сохранённый readingMode | A subsequent stroke save() preserves the previously-persisted readingMode instead of resetting it to false. | b505080 | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/AnnotationRepositoryJvmTest.kt:save_afterSaveViewState_preservesReadingMode | PEND |
| RC-EDITOR-006 | 🟠 | 2 | visual | При открытии PDF на сохранённой странице (page>0) на Android он центрируется по X | Restoring a saved page>0 on Android shows the page centered along X with the scrolled Y preserved; not left-pinned. | b505080 | → AV-EDITOR-04 | PEND |

### INPUT — Stylus / palm input

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-INPUT-001 | 🟠 | 2 | gesture | После касания пером палец снова рисует на странице, если Pencil Mode выключен вручную | After manually disabling Pencil Mode the finger again leaves a stroke on the page. | 2f2658a | → AV-INPUT-01 | PEND |
| RC-INPUT-002 | 🟠 | 2 | gesture | Внутри лупы палец снова пишет после касания пером при выключенном Pencil Mode | After disabling Pencil Mode the finger again draws inside the loupe panel. | 2f2658a | → AV-INPUT-02 | PEND |

### LIBRARY — Library / open

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-LIBRARY-001 | 🟠 | 1 | unit | Кросс-библиотечная дедупликация книг по canonical identity; null-identity не сливается | Same-identity books merge into one entry tagged with all LibraryIds; null-identity never merges; order is stable first-appearance. | 9840296 | library/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/library/impl/DefaultLibraryRegistryTest.kt | PEND |
| RC-LIBRARY-002 | 🟠 | 1 | unit | LocalFolderLibrary заполняет canonical identity асинхронно и переиспользует кэш при ре-скане | Identities resolved asynchronously and patched in; per-uri cache prevents re-hashing; null provider leaves identity null. | 9840296 | library/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/library/impl/LocalFolderLibraryIdentityTest.kt | PEND |
| RC-LIBRARY-003 | 🔴 | 1 | unit | CacheEvictor: LRU-вытеснение до cap, пропуск открытых документов, учёт пропущенных байт | Under cap is a no-op; over cap deletes LRU-first, never an open/pinned file while still counting its bytes; spans all dirs. | 9840296 | sync/src/commonTest/kotlin/ru/kyamshanov/notepen/sync/domain/CacheEvictorTest.kt | PEND |
| RC-LIBRARY-004 | 🟠 | 1 | unit | GitHubLibrary.refresh инвалидирует кэш при изменении blob-sha, иначе сохраняет | A changed blob sha deletes the stale cached file; an unchanged sha keeps the cache intact. | 9840296 | library/impl/src/jvmTest/kotlin/ru/kyamshanov/notepen/library/impl/GitHubLibraryTest.kt | PEND |

### MAG — Magnifier / loupe

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-MAG-001 | 🟠 | 1 | unit | Авто-прокрутка рамки лупы выключена по умолчанию | MagnifierState().autoScrollEnabled == false on a fresh instance. | 965bfe6 | needed | PEND |
| RC-MAG-002 | 🟠 | 2 | gesture | Lift-off авто-прокрутка сдвигает рамку в одну из 4 edge-зон на отрыве пера | On pen-up the target frame advances ~35% of its size toward the matching edge; center end does not move; diagonals compose. | 0681700 | → AV-MAG-01 | PEND |
| RC-MAG-003 | 🟠 | 2 | gesture | Лупа держит per-frame бюджет: кончик пера не отстаёт на длинном штрихе | Tip-to-pen gap stays constant and small across a long stroke over a heavily-inked page; baked ink stays crisp. | 0791657 | → AV-MAG-02 | PEND |
| RC-MAG-004 | 🟡 | 2 | visual | Тонкий штрих не исчезает в лупе (минимум 1px) | The thinnest minimum-pressure stroke renders as a continuous visible line at least ~1px wide. | 0791657 | → AV-MAG-03 | PEND |
| RC-MAG-005 | 🟠 | 2 | visual | Лупа сохраняет aspect-ratio страницы (без растяжения относительно рамки) | Panel content aspect == dashed-frame region aspect on a non-square page and after resize. | 2959eb6 | → AV-MAG-04 | PEND |
| RC-MAG-006 | 🔴 | 2 | gesture | В pencil mode одиночный палец панорамирует рамку, стилус продолжает писать | Stylus single-pointer writes without moving the frame; single finger pans the loupe frame in pencil mode. | 3128f49 | → AV-MAG-05 | PEND |
| RC-MAG-007 | 🟠 | 2 | gesture | При выключенном pencil mode палец рисует внутри панели лупы | Finger draws when pencil mode off and pans when pencil mode on. | 3fa547f | → AV-MAG-06 | PEND |
| RC-MAG-008 | 🔴 | 2 | gesture | При активной лупе draw/erase/select на странице вне панели работают | Draw, erase, and select all function on the page outside the loupe panel while the loupe is enabled. | 4d79c8e | → AV-MAG-07 | PEND |
| RC-MAG-009 | 🟠 | 2 | gesture | Hit-тест панели лупы учитывает window-vs-viewer origin (перо и рамка не расходятся) | Pen input inside the panel maps 1:1 to the drawn panel region with no vertical offset. | 4d79c8e | → AV-MAG-08 | PEND |
| RC-MAG-010 | 🟠 | 2 | gesture | Повторное выделение области лупы достижимо при уже открытой лупе | A fresh region can be selected while the loupe is already open (trigger held) and a plain drag draws. | 4d79c8e | → AV-MAG-09 | PEND |
| RC-MAG-011 | 🟠 | 2 | gesture | При активной лупе прямой PDF-ввод полностью заблокирован (skipPage = enabled) | On Android direct PDF strokes do not appear while the loupe is active; ink only registers inside the panel. | 965bfe6 | → AV-MAG-10 | PEND |
| RC-MAG-012 | 🟡 | 2 | visual | Hover-кружок кончика пера скрыт во время активного штриха в лупе | The hover indicator shows while hovering but disappears the moment a stroke is active; no ghost dot. | 965bfe6 | → AV-MAG-11 | PEND |
| RC-MAG-013 | 🔴 | 1 | unit | В развороте hit-тест рамки лупы выбирает правую страницу по docX | resolvePageForDocSpace returns the right page when docX is past the column split, left otherwise; SINGLE ignores docX. | d7725f7 | rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/magnifier/MagnifierGeometryTest.kt | PEND |
| RC-MAG-014 | 🟠 | 1 | unit-edge | Висячая последняя левая страница в развороте не имеет правой колонки | For the hanging last left page, docX on the right side still resolves to that page (no phantom right page). | d7725f7 | rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/magnifier/MagnifierGeometryTest.kt | PEND |
| RC-MAG-015 | 🔴 | 2 | gesture | В развороте viewport-rect рамки правой страницы смещён на pageLeftsPx | Writing on the left half draws ink on the left page; it does not grab or move the right-page loupe frame. | d7725f7 | → AV-MAG-12 | PEND |

### MARKER — Marker / highlighter

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-MARKER-001 | 🟠 | 2 | visual | Маркер мультиплай-композитится с PDF: текст читается сквозь подсветку, чернила пера сверху | Dark text remains legible through the highlight (multiply); pen ink renders on top of the marker band. | 35a7f31 | → AV-MARKER-01 | PEND |
| RC-MARKER-002 | 🟠 | 2 | visual | Live-маркер в low-latency overlay рисуется chisel-лентой с multiply, а не круглым пером | The live marker is a chisel-edge ribbon with multiply matching the committed stroke; no round-nib line. | 73d98bf | → AV-MARKER-02 | PEND |
| RC-MARKER-003 | 🟡 | 2 | visual | Live-маркер в лупе рисуется полной chisel-лентой каждый кадр (инкрементальный bake пропущен) | In the magnifier the live marker is one continuous chisel multiply ribbon every frame; no seam/double-darkening. | 73d98bf | → AV-MARKER-03 | PEND |

### PDF — PDF render & gestures

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-PDF-001 | 🟡 | 1 | unit | Колесо мыши: фактор зума = ZOOM_BASE^(-delta.y) (плавно на тачпаде, ~±7% на дискретной мыши) | Factor depends monotonically/continuously on delta.y; no fixed 10% step. | 0148630 | needed | PEND |
| RC-PDF-002 | 🟠 | 2 | gesture | Android pinch: точка под пальцами остаётся на месте (atomic pinchUpdate, без edge-clamp pan) | The document point under the centroid stays put each tick; the page does not snap to the viewport edge. | 0148630 | → AV-PDF-01 | PEND |
| RC-PDF-003 | 🔴 | 2 | e2e | Android высокий зум: нет OOM 'createGraphicBuffer failed' (gate low-latency overlay, MAX_RENDER_DIM 2400) | Above 2400px the live-stroke renders via Compose Canvas without the overlay; no OOM; app does not crash. | 0148630 | → AV-PDF-02 | PEND |
| RC-PDF-004 | 🟠 | 2 | visual | Live-stroke: рост давления к концу не утолщает уже нарисованную часть (per-segment ширина) | Stroke start stays thin during the whole gesture; width varies per segment; live render matches committed. | b851504 | → AV-PDF-03 | PEND |
| RC-PDF-005 | 🔴 | 1 | unit-edge | Ластик: отменённый pointerInput-restart'ом жест, уже изменивший currentPaths, всё равно шлёт onEraseFinished | cancel() after a real erase emits onEraseFinished(before, after); a no-op cancel emits nothing. | b851504 | needed | PEND |
| RC-PDF-006 | 🟠 | 2 | gesture | Pencil Mode: переключатель форсирует palm-rejection без отмены активного жеста | With Pencil Mode the finger only pans/zooms, only stylus draws; toggling the mode does not cancel the active gesture. | b851504 | → AV-PDF-04 | PEND |
| RC-PDF-007 | 🟠 | 1 | unit-edge | JvmPdfDocument: рендер после close() кидает CancellationException; close идемпотентен и сериализован | useRenderer runs under the monitor while open; after close it throws CancellationException; double-close is safe. | b851504 | needed | PEND |
| RC-PDF-008 | 🔴 | 1 | error | JBIG2-картинки в PDF рендерятся (jbig2-imageio на classpath), нет MissingImageReaderException | A JBIG2-image PDF renders without MissingImageReaderException and produces non-blank output. | c9bba3a | needed | PEND |

### READER — Reader chrome & reflow

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-READER-001 | 🟡 | 2 | visual | В режиме чтения зум-кластер (zoom in/label/out) скрыт из колеса настроек | Editor mode shows the zoom cluster; reading mode shows the wheel with it removed. | 064fc87 | → AV-READER-01 | PEND |
| RC-READER-002 | 🟠 | 2 | visual | Airbar ридера не растягивается на всю ширину окна на десктопе (cap по AIRBAR_MAX_WIDTH) | Collapsed airbar pill is constrained to ~AIRBAR_MAX_WIDTH and centered, not spanning the full window width. | 2dcef23 | → AV-READER-02 | PEND |
| RC-READER-003 | 🟡 | 2 | visual | Zoom-label занимает в колесе свою настоящую ширину (40dp), без фантомного хвостового затухания | With ample room the wheel sits at full size with no phantom trailing fade and no scroll; label sized to 40dp. | 4607c5c | → AV-READER-03 | PEND |
| RC-READER-004 | 🟠 | 1 | unit | Reflow: межстрочный шаг сгенерированного PDF (1.25 кегля) не дробит один абзац на отдельные | blocks.single() is a Paragraph joining the three lines. | 518ccbe | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/ReflowAssemblerTest.kt:44 | PEND |
| RC-READER-005 | 🟠 | 1 | unit-edge | Reflow: реальный разрыв абзаца с дополнительной выноской всё ещё делит текст на два абзаца | Two Paragraphs split at the extra-leading boundary. | 518ccbe | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/ReflowAssemblerTest.kt:62 | PEND |
| RC-READER-006 | 🟠 | 1 | integration | Reflow JVM: межсловный разрыв PDFBox даёт пробел между словами | kind == TEXT_BASED; body contains 'важный и курсивный' and 'текст абзаца' with spaces. | 518ccbe | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/book/JvmEbookToPdfConverterTest.kt:rendered vector text is extractable for reflow with spaces between words | PEND |
| RC-READER-007 | 🟠 | 1 | integration | Desktop: JvmBookPdfRenderer кладёт настоящий векторный текстовый слой, FB2/EPUB извлекаемы | Extraction returns TEXT_BASED with the heading text present in extracted Heading blocks. | 518ccbe | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/book/JvmEbookToPdfConverterTest.kt:rendered vector text is extractable for reflow with spaces between words | PEND |
| RC-READER-008 | 🟡 | 1 | integration | Desktop: тело FB2-абзаца имеет красную строку, заголовок — прижат влево | Paragraph's leftmost ink is indented (>20px @150dpi) versus the flush heading. | 518ccbe | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/book/JvmEbookToPdfConverterTest.kt:body paragraph is first-line indented but heading is flush left | PEND |
| RC-READER-009 | 🔴 | 1 | error | Desktop: открытие FB2 из списка файлов (генерация миниатюры) не падает | Thumbnail generation completes for an ebook input without exception. | 518ccbe | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/infrastructure/PdfThumbnailGeneratorDesktopTest.kt | PEND |
| RC-READER-010 | 🟠 | 2 | visual | В режиме чтения скрыты перо/лупа/экспорт, маркер-выделитель и ластик остаются | Reading mode tool set excludes pen/magnifier/export and includes marker+eraser; editor mode shows all. | 518ccbe | → AV-READER-04 | PEND |
| RC-READER-011 | 🟠 | 2 | visual | Хром ридера перекрашивается под тему ридера; focus mode прячет всё вместе | Chrome adopts reader-theme colors; focus tap hides back button + page counter + chrome together; second tap restores. | 518ccbe | → AV-READER-05 | PEND |
| RC-READER-012 | 🟡 | 2 | visual | Индикатор выбранного инструмента в режиме чтения перекрашен под тему ридера | Selected-tool indicator fill and glyph match the chosen reader theme. | 5f68c6d | → AV-READER-06 | PEND |
| RC-READER-013 | 🟡 | 1 | unit-edge | Reflow: перенесённая строка '3),' не принимается за маркер нумерованного списка | Single Paragraph; not split into a list item at '3)'. | 77c4de3 | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/ReflowAssemblerTest.kt:321 | PEND |
| RC-READER-014 | 🟡 | 1 | unit-edge | Reflow: нумерованный маркер, за которым идёт буква, всё ещё распознаётся как пункт списка | Single ReflowBlock.ListItem. | 77c4de3 | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/ReflowAssemblerTest.kt:333 | PEND |
| RC-READER-015 | 🟠 | 1 | unit | Reflow: умеренный межабзацный зазор делит абзацы, не дробя перенос строк | Two Paragraphs split at the 1.6-em boundary; wrapped 1.2-em lines stay merged. | 77c4de3 | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/ReflowAssemblerTest.kt:90 | PEND |
| RC-READER-016 | 🟡 | 1 | unit | Кастомные пресеты ридера: два форка одной базы получают последовательные суффиксы | First fork is '<Base>-1', second is '<Base>-2'. | 77c4de3 | reflow/api/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/api/ReaderSettingsReducerTest.kt:85 | PEND |
| RC-READER-017 | 🟡 | 1 | unit | Кастомные пресеты ридера: renamePreset меняет только имя (тримминг, пустое=no-op, коллизия=суффикс) | Each renamePreset invariant holds per the added ReaderSettingsReducerTest cases. | 77c4de3 | reflow/api/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/api/ReaderSettingsReducerTest.kt:146 | PEND |
| RC-READER-018 | 🟠 | 2 | visual | Кнопка режима чтения скрыта для документов без текста; PNG не зависает на 'Готовим режим чтения…' | Reading-mode button hidden for image-only PDF and PNG; appears for text-based docs after probe; no entry hang. | 77c4de3 | → AV-READER-07 | PEND |
| RC-READER-019 | 🟡 | 2 | visual | Кромочный fade колеса пресетов подавлен у достигнутого упора прокрутки | The edge at its scroll stop renders unfaded; only the side with remaining scroll fades. | 77c4de3 | → AV-READER-08 | PEND |
| RC-READER-020 | 🟠 | 2 | visual | Focus mode: одиночная полоса вкладок прячется вместе с хромом, фокус сохраняется | Single-tab strip hides with chrome and reserve collapses; on returning focus the chrome state tracks with no flicker. | 77c4de3 | → AV-READER-09 | PEND |
| RC-READER-021 | 🟠 | 2 | gesture | Хардварные клавиши перелистывания работают после скрытия панели тапом по полотну | After hiding the panel by tap, hardware PageDown/ArrowDown/Space still advances the page. | b558aa6 | → AV-READER-10 | PEND |
| RC-READER-022 | 🟡 | 2 | visual | Постоянная ширина процентной метки ('100%') — колесо пресетов не дёргается при смене цифр | Percent label slot stays at the '100%' width; the preset wheel does not re-center as digit count changes. | c462369 | → AV-READER-11 | PEND |
| RC-READER-023 | 🟡 | 2 | visual | Края колеса пресетов используют барабанный эффект (minAlpha), а не chevron-указатели | No chevrons; edge chips dimmed via the drum minAlpha and remain readable; thin 14dp fade only. | c462369 | → AV-READER-12 | PEND |
| RC-READER-024 | 🟡 | 2 | visual | Переключатель режима скролла (BOTH/VERTICAL/NONE) скрыт в режиме чтения | Scroll-mode toggle present in editor mode, absent in reading mode. | d2e04d7 | → AV-READER-13 | PEND |
| RC-READER-025 | 🟡 | 2 | visual | Портретный счётчик страниц резервирует ширину под макс. число цифр — тулбар не дёргается | Page counter reserves max-digit width; the tool wheel does not jump when the page number gains a digit. | 8c1839b | → AV-READER-14 | PEND |
| RC-READER-026 | 🟡 | 2 | visual | Портретное колесо инструментов прижато к правому краю и не дёргает крайние иконки при скролле | Tool wheel is pinned to the right edge with stable width; edge icons stay steady during PDF scroll. | 8caf0ad | → AV-READER-15 | PEND |
| RC-READER-027 | 🟠 | 2 | visual | Крайние плашки airbar не дрожат при хвостовом переполнении (затухание от натуральной геометрии) | Trailing and leading edge chips stay steady; no per-frame jitter while the mouse moves over the content. | 9bffd19 | → AV-READER-16 | PEND |
| RC-READER-028 | 🟡 | 2 | visual | Нет зазора между меткой прогресса и колесом пресетов (гаттер убран) | Progress text is right-aligned in its slot and the preset chips start flush against it; no large gutter gap. | a208704 | → AV-READER-17 | PEND |

### REFLOW — Reflow reader extraction

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-REFLOW-001 | 🟠 | 1 | unit | Stream-детектор бракует таблицу-кандидат, если средняя непустая ячейка короче 2 символов | doc.blocks contains zero Table blocks and >=1 Paragraph (the noise text flows as a paragraph). | 93ba0bd | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/AssemblerNoiseGuardTest.kt | PEND |
| RC-REFLOW-002 | 🟠 | 1 | unit-edge | Легитимная короткая таблица (Name/Age, avg≈4) НЕ убивается порогом F-1 | A real short-cell table survives as a Table block. | 93ba0bd | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/AssemblerNoiseGuardTest.kt | PEND |
| RC-REFLOW-003 | 🟡 | 1 | unit | Заголовок из расставленных пробелами букв ('В х о д н а я') демотируется в Paragraph | The spaced-out candidate is demoted to a Paragraph; no Heading 'Входная' / no TOC entry. | 93ba0bd | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/AssemblerNoiseGuardTest.kt | PEND |
| RC-REFLOW-004 | 🟠 | 1 | unit | Широкая ячейка делится по позиции глифа относительно границ колонок, а не по зазору | The wide row splits into two cells [wide,'Z'] using the learned column boundary. | ee3e1af | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/ReflowAssemblerTest.kt | PEND |
| RC-REFLOW-005 | 🟠 | 1 | unit | Общий TableNoiseGuard отбраковывает фантомные OCR-таблицы | isOcrNoiseTable returns true for the three OCR-noise shapes and false for the three legit tables. | 89fbcce | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/TableNoiseGuardTest.kt | PEND |
| RC-REFLOW-006 | 🟠 | 1 | unit | Lattice-рефайнер: фантомные rulings между глифами остаются Figure, не становятся Table | The phantom-ruling single-char grid stays a Figure crop, not a reconstructed Table. | 89fbcce | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/lattice/LatticeTableRefinerTest.kt | PEND |
| RC-REFLOW-007 | 🟠 | 1 | integration | Реальная фикстура Барановской: после F-7/F-8 число таблиц падает, текст в параграфы | Block-kind counts stay within the post-F8 ranges; phantom tables do not creep back above 300. | 89fbcce, 93ba0bd | reflow/impl/src/jvmTest/kotlin/ru/kyamshanov/notepen/reflow/fixtures/RealPdfInvariantsTest.kt | PEND |
| RC-REFLOW-008 | 🟠 | 1 | unit | ReflowBinaryFormat v5: parserVersion пишется в заголовок и round-trip'ится | parserVersion is serialized into the v5 header and restored exactly (default and explicit values). | 64ea785 | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/reflow/ReflowBinaryFormatTest.kt | PEND |
| RC-REFLOW-009 | 🔴 | 1 | unit | Дисковый кэш reflow инвалидируется при смене версии парсера даже при неизменных size/mtime | A cache entry parsed by a prior parser version is treated stale: read returns null and the file is deleted. | 64ea785 | app/byCompose/common/src/jvmTest/kotlin/ru/kyamshanov/notepen/reflow/FileSystemReflowDocumentDiskCacheTest.kt | PEND |
| RC-REFLOW-010 | 🟡 | 2 | visual | TableView красит фон по row.isHeader (а не rowIndex==0) | Header shading follows row.isHeader: the merged header row is shaded; a non-header row 0 is transparent. | 0f4579d | → AV-REFLOW-01 | PEND |
| RC-REFLOW-011 | 🟠 | 2 | gesture | Reading-позиция сохраняется при смене ориентации (тот же ПАССАЖ, а не индекс страницы) | The anchored passage stays on screen across portrait↔landscape; only the page number adapts. | aca2211 | → AV-REFLOW-02 | PEND |

### RENDER — Rendering pipeline

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-RENDER-001 | 🟠 | 1 | unit | clampPanFree: помещающийся лист уводится почти за край без возврата к центру | Far pan clamps to keep >=25% visible (375/-75), and a pan inside the free band is returned untouched. | 39376da | rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesLayoutTest.kt:189 | PEND |
| RC-RENDER-002 | 🟠 | 1 | unit-edge | clampPanFree: переполняющая ось сохраняет overscroll clampPan и кастомный буфер | Overflowing axis keeps clampPan edge+buffer semantics and forwards the custom overscroll buffer. | 39376da | rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesLayoutTest.kt:206 | PEND |
| RC-RENDER-003 | 🟠 | 1 | unit-edge | Центрирование/свободный ход считаются по листу, игнорируя правый вылет скана | Both centring and free-pan use the sheet column, ignoring the 1.4x right ink overflow. | 39376da | rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesLayoutTest.kt:240 | PEND |
| RC-RENDER-004 | 🔴 | 2 | e2e | Разделение разворота (#4) не роняет окно редактора при переключении (stale-closure IOOB) | Editor window survives repeated split toggling; no crash; pages re-render correctly. | 39376da | → AV-RENDER-01 | PEND |
| RC-RENDER-005 | 🟠 | 2 | visual | Половинки повёрнутых страниц (/Rotate 90/270) не выходят широкими короткими полосами | Each half is a tall half-width portrait slice with correct aspect; no wide short strips or empty pages. | 39376da | → AV-RENDER-02 | PEND |
| RC-RENDER-006 | 🟠 | 2 | gesture | Завершённые штрихи не пропадают при прерывистом письме (async ребилд ink-кэша) | All previously finished strokes remain visible while the next stroke is being drawn; nothing flickers out. | 446ded7 | → AV-RENDER-03 | PEND |
| RC-RENDER-007 | 🟡 | 2 | visual | PDF-текст резкий при открытии (суперсэмплинг 2x + FilterQuality.High) | Vector text edges are crisp/anti-aliased at the default open zoom, not soft/blurry. | 4ea2a97 | → AV-RENDER-04 | PEND |
| RC-RENDER-008 | 🟠 | 2 | visual | requestRecenter сохраняет зум и применяет точный множитель компенсации (0.5/2/1) | Mode switches preserve reading scale: split halves the visual size, spread keeps it; never snaps to fill-screen. | 5b019c4 | → AV-RENDER-05 | PEND |
| RC-RENDER-009 | 🔴 | 1 | unit | layoutZoomCap ограничивает слот по MAX_LAYOUT_DIM_PX и не превышает MAX_ZOOM | Cap = MAX_LAYOUT_DIM_PX/maxSlotDim clamped to [1, MAX_ZOOM]; large page caps below MAX_ZOOM, small at MAX_ZOOM. | a894f82 | rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesLayoutTest.kt:180 | PEND |
| RC-RENDER-010 | 🟠 | 1 | unit | residualScale: identity ниже cap, остаток выше cap; layoutZoom*residualScale == visual zoom | Below cap residual is identity; above cap layoutZoom pins to cap and residual carries the excess; product reconstructs zoom. | a894f82 | rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesLayoutTest.kt:200 | PEND |
| RC-RENDER-011 | 🟠 | 2 | gesture | Лупа: растеризация завершённого штриха off-main без per-pen-up фриза | The loupe stays responsive on each pen-up (no freeze) and the finished stroke remains visible. | a894f82 | → AV-RENDER-06 | PEND |
| RC-RENDER-012 | 🔴 | 3 | manual | Android-сборка компилируется: Offset импортирован в PdfPagesViewer.android | Android module compiles with the expect/actual match satisfied; no unresolved Offset reference. | b158971 | release-checklist | PEND |

### SESSION — Sessions / restore

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-SESSION-001 | 🔴 | 2 | e2e | «Восстановить последнюю» возвращает рабочий стол ДО открытия редактора, а не no-op | Restore-last restores the pre-session snapshot (state A at editor mount), not the current live workspace; never a no-op. | 0e91c39 | → AV-SESSION-01 | PEND |
| RC-SESSION-002 | 🟠 | 2 | visual | Сессии открываются как dropdown, привязанный к кнопке, а не как модальное окно | A DropdownMenu anchored to the «Сессии» button opens promptly with no separate native window; tool-rail entry removed. | e05f6fa | → AV-SESSION-02 | PEND |
| RC-SESSION-003 | 🟡 | 2 | visual | Сессии-dropdown открывается мгновенно: поле имени показывается лениво | Menu opens without the text field present; the field appears only after «Сохранить текущую…» and collapses on «Отмена». | 401850e | → AV-SESSION-03 | PEND |
| RC-SESSION-004 | 🟠 | 2 | gesture | Кнопка «Сессии» на тайтл-баре срабатывает с первого нажатия | A single press on the «Сессии» button opens the dropdown immediately; the press does not start a window drag. | 401850e | → AV-SESSION-04 | PEND |

### SIDEBAR — Page-thumbnails sidebar

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-SIDEBAR-001 | 🟡 | 2 | visual | Чип фильтра в боковой панели: иконка и подпись не перекрываются (длинный лейбл «Надписи») | For every filter mode the icon sits at the left, a gap, then the label — icon and label never overlap. | 985ff88 | → AV-SIDEBAR-01 | PEND |

### STARTUP — Cold-start / perf

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-STARTUP-001 | 🟠 | 1 | unit | Конструктор SqlDelightPendingDeltaQueue не открывает БД — databaseProvider вызывается лениво | databaseProvider is not invoked at construction; it is invoked exactly once on the first query. | d8f5443 | needed | PEND |
| RC-STARTUP-002 | 🟠 | 1 | unit | pendingCounts() не форсит открытие БД на потоке вызывающего — открытие при коллекции на ioDispatcher | Obtaining the pendingCounts() Flow opens nothing; the DB opens only when collected, on the IO dispatcher. | d8f5443 | needed | PEND |
| RC-STARTUP-003 | 🔴 | 1 | unit | App и фабрики компонентов корректно работают с null sync-параметрами (первый кадр до готовности sync) | App composes its first frame with null sync params and recomposes cleanly when the heavy deps become non-null. | f90b9e7 | needed | PEND |
| RC-STARTUP-004 | 🟠 | 2 | visual | Главное окно появляется до завершения инициализации sync/network стека | First frame paints quickly with no sync stack; the peer/online indicators populate slightly later; no blank pre-paint hang. | f90b9e7 | → AV-STARTUP-01 | PEND |

### SYNC — LAN / host sync

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-SYNC-001 | 🔴 | 1 | unit | Явный Disconnect от хоста терминален — клиент не переподключается | A received Disconnect yields RemoteClosed and terminates the client without reconnecting; only an unsignalled drop reconnects. | 22d5e7b | needed | PEND |
| RC-SYNC-002 | 🟠 | 1 | unit | disconnect(peerId) на хосте отзывает auto-approve грант | After a deliberate disconnect(peerId), the peer's auto-approve grant is revoked and a reconnect re-prompts for approval. | 22d5e7b | needed | PEND |
| RC-SYNC-003 | 🟠 | 1 | unit | toolType (PEN/MARKER) переживает round-trip StrokeDelta через сеть | toolType round-trips through DTO+serialization unchanged; legacy payloads without the field default to PEN. | 8165606 | needed | PEND |
| RC-SYNC-004 | 🔴 | 1 | unit | Реконнект пира заменяет stale-сессию вместо отказа already-connected | A reconnect from an already-known peer replaces the stale session, auto-approves, and old teardown does not wipe the new registration. | 8d07493 | needed | PEND |
| RC-SYNC-005 | 🟠 | 1 | unit | reject(peerId) закрывает диалог одобрения через approvalResolutions | Resolving the pending peer's id via approvalResolutions clears the pending-approval state; an unrelated id does not. | 8d07493 | needed | PEND |
| RC-SYNC-006 | 🟠 | 2 | manual | Реконнект к исчезнувшему хосту падает быстро (connect-timeout), индикатор не виснет жёлтым | On a vanished host, reconnect fails fast within the bounded connect timeout and the sync indicator clears. | 8d07493 | → AV-SYNC-01 | PEND |
| RC-SYNC-007 | 🟠 | 1 | unit | Host не помечает свои открытые документы OrphanedOnHost из-за пустого каталога клиента | Empty peer catalogs are excluded from the orphan judgement; only non-empty catalogs decide presence. | 8fd672b | needed | PEND |
| RC-SYNC-008 | 🔴 | 1 | unit | Peer-дельты сбрасываются на диск через debounce-коалесинг (один flush на всплеск) | Multiple rapid peer deltas coalesce into a single debounced disk flush persisting the accumulated state; off-catalog docs no-op. | 8fd672b | needed | PEND |
| RC-SYNC-009 | 🟡 | 1 | unit | favoritePageIndices переживают проекцию (load → projection → flush) | favoritePageIndices loaded into the projection are preserved and written back on flush. | 8fd672b | needed | PEND |
| RC-SYNC-010 | 🟡 | 1 | unit | Всплеск RemoteCatalogChanged схлопывается в один broadcast (debounce) | A burst of catalog-changed signals collapses into a single debounced RemoteCatalogChanged broadcast. | 8fd672b | needed | PEND |
| RC-SYNC-011 | 🔴 | 2 | e2e | Штрих на планшете не теряется при немедленном открытии документа на ПК (проекция как источник истины) | A stroke drawn on the tablet appears on the PC when the document is opened moments later. | 8fd672b | → AV-SYNC-02 | PEND |
| RC-SYNC-012 | 🟠 | 2 | visual | Рисование на хосте не лагает: дисковый IO и растеризация чернил вне main-thread | Drawing stays smooth (no dropped frames) and the finished stroke stays visible while the cache rebuilds off-main. | 8fd672b | → AV-SYNC-03 | PEND |

### TABS — Tabs / workspace

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-TABS-001 | 🟡 | 2 | visual | Кнопка «Сессии» рисуется только на панели у левого края окна, а не в каждой панели сплита | Exactly one «Сессии» button is visible, in the left-edge panel's strip; the inner/right panel has none. | 17c87cc | → AV-TABS-01 | PEND |
| RC-TABS-002 | 🟠 | 2 | visual | Фантомный отступ под кнопки окна остаётся только у крайней панели сплита, внутренние без зазора | Only the edge strips reserve OS window-control insets; inner panels start their tabs flush with no reserved gap. | 1b295cf | → AV-TABS-02 | PEND |
| RC-TABS-003 | 🔴 | 2 | e2e | Открытие файла через «+» добавляет новую вкладку поверх восстановленного сплита, а не затирает | Both A and B are present as separate tabs; B is added on top of the restored layout, not replacing A. | c27db4f | → AV-TABS-03 | PEND |
| RC-TABS-004 | 🔴 | 2 | gesture | Тап по вкладке в строке заголовка переключает её, а не трактуется ОС как перетаскивание окна | A clean tap on an inactive tab chip activates it and shows its document; the tap is not consumed as a window drag. | c27db4f | → AV-TABS-04 | PEND |

### TOOLRAIL — Tool rail

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-TOOLRAIL-001 | 🟠 | 2 | visual | Кнопки undo/redo остаются видимы при переполнении списка инструментов в альбомной ориентации | Undo/redo are pinned at the top of the landscape rail island and remain fully visible regardless of list length or scroll. | 62bea5e | → AV-TOOLRAIL-01 | PEND |

### UI — Glass / theme / chrome

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-UI-001 | 🟠 | 1 | unit-edge | Крайние элементы колеса (idx 0 и последний) не ужимают footprint — нет автоколебания у краёв | wheelItemFalloff returns exactly 0f for index 0 and the last index; non-zero falloff for a middle item. | 4537b9d | needed | PEND |
| RC-UI-002 | 🟠 | 2 | gesture | Стрелка доезда до конца ленты колеса пресетов больше не дёргается | The wheel decelerates and stops cleanly on the final preset; no oscillation at the docking edge. | 4537b9d | → AV-UI-01 | PEND |
| RC-UI-003 | 🟠 | 2 | visual | Хром/фон редактора не мигает дефолтной темой ридера до асинхронной загрузки настроек | Chrome stays neutral until settings load, then switches to the saved reader theme in one frame; no default-theme flash. | 46599eb | → AV-UI-02 | PEND |
| RC-UI-004 | 🟠 | 2 | visual | В режиме чтения корневой фон редактора красится фоном ридера — нет «полоски» дефолтной палитры | The status-bar inset and reserved tab-strip area are filled with the reader background; no contrasting default strip. | 46599eb | → AV-UI-03 | PEND |
| RC-UI-005 | 🟠 | 2 | visual | Стеклянные панели над меньшим источником не имеют односторонней асимметрии | Both rims of the glass panel show the same tint density; no one-sided transparent vs dense-tint split. | 55967ff | → AV-UI-04 | PEND |
| RC-UI-006 | 🟡 | 2 | visual | PDF-текст под airbar размывается до рефракции — нет узнаваемых глифов-призраков под ободком | Under the rim the backdrop reads as a soft smudge; no recognisable glyphs are surfaced by the refraction lens. | 55967ff | → AV-UI-05 | PEND |
| RC-UI-007 | 🟠 | 2 | visual | LiquidGlass верхний бар тинтует область за статус-баром, а не показывает скролл-контент насквозь | The status-bar area shows the glass top-bar tint; the scrolling content does not bleed through behind the status bar. | 6cce59b | → AV-UI-06 | PEND |
| RC-UI-008 | 🟡 | 2 | visual | Превью миниатюр сайдбара не мигают при автоскролле (кэш bitmap поднят на уровень сайдбара) | Already-rendered thumbnails remain stable; no preview goes blank/re-renders when the sidebar window slides. | c24bc4a | → AV-UI-07 | PEND |
| RC-UI-009 | 🟠 | 2 | gesture | Скролл основного PDF плавный — рендер миниатюр сериализован mutex и гейтится по pdfIdle/дебаунсу | Main PDF scroll stays smooth during the fling; sidebar auto-scroll settles only after the scroll pauses. | c24bc4a | → AV-UI-08 | PEND |
| RC-UI-010 | 🟡 | 2 | visual | Активная страница в сайдбаре выровнена по нижнему краю вьюпорта — следующая миниатюра не торчит | The active thumbnail sits flush at the bottom edge of the sidebar viewport; no next-thumbnail stub. | c24bc4a | → AV-UI-09 | PEND |
| RC-UI-011 | 🟡 | 2 | visual | Quick-actions airbar (лупа/режим скролла) и плейсхолдер загрузки следуют теме ридера | The quick-actions airbar tint, icon colours, and the loading placeholder bg/text all match the active reader theme. | ca64c87 | → AV-UI-10 | PEND |
| RC-UI-012 | 🟠 | 1 | unit | isRefractionSupported: JVM=true, Android только при API>=33 (TIRAMISU) | isRefractionSupported() returns true on JVM and Android API>=33, false on Android API<33. | cd359a2 | needed | PEND |
| RC-UI-013 | 🟠 | 2 | visual | RectangleShape верхний бар не пробивает размытым контентом список под ним (на Android<13) | The top bar's glass is clipped to its bounds; no blurred halo bleeds into the list or overlaps the header. | cd359a2 | → AV-UI-11 | PEND |
| RC-UI-014 | 🟠 | 2 | visual | Скруглённые pill-airbar'ы на Android<13 не имеют квадратного гало вокруг формы | The blur is clipped to the rounded pill shape; no square halo around it. | cd359a2 | → AV-UI-12 | PEND |
| RC-UI-015 | 🟡 | 2 | visual | Кнопка восстановления последней сессии — primary Button (терракот), читается как CTA | The restore button renders as a filled primary (terracotta) button that stands out clearly as the menu's CTA. | cf7f90a | → AV-UI-13 | PEND |
| RC-UI-016 | 🟠 | 1 | unit | Vibrancy-фильтр сжимает диапазон яркости фона (чёрный=светлая тема, белый=тёмная тема) | ColorMatrix scale equals 1-GLASS_BACKDROP_CONTRAST on RGB diagonals; light-theme offset = contrast*255, dark = 0; alpha untouched. | dd0c483 | needed | PEND |
| RC-UI-017 | 🟠 | 2 | visual | Текст стеклянной панели остаётся читаемым над фоном того же цвета (чёрный текст над чёрным) | The bled-through backdrop text is compressed to mid-grey haze; the panel's own dark text reads cleanly. | dd0c483 | → AV-UI-14 | PEND |

### UIKIT — Reusable components

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-UIKIT-001 | 🟠 | 2 | visual | Тул-рейл/wheel на планшете в ландшафте не мерцает (footprint слотов не зависит от прокрутки) | Edge rail slots stay still; no per-frame jitter/flicker; the drum effect (scale+fade) is preserved. | 3d18b89 | → AV-UIKIT-01 | PEND |
| RC-UIKIT-002 | 🟠 | 1 | unit | wheelItem: лейаут-след слота равен натуральному размеру независимо от falloff/прокрутки | Slot main size and total content length are constant across scroll positions; only graphicsLayer scale/alpha vary. | 3d18b89 | needed | PEND |
| RC-UIKIT-003 | 🟡 | 2 | visual | Стрелки колеса прокручивают ровно на один элемент, а не на страницу (70% вьюпорта) | Each chevron press shifts the strip by exactly one item, not ~70% of the visible band. | 7d11a78 | → AV-UIKIT-02 | PEND |
| RC-UIKIT-004 | 🟡 | 2 | visual | После доводки стрелкой правый край ленты не дёргается (animateScrollBy дельтой) | After reaching the trailing stop via chevron the right edge is stable; no post-motion jitter. | 7d11a78 | → AV-UIKIT-03 | PEND |

### UX — Cross-cutting UX

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-UX-001 | 🔴 | 1 | unit | Undo/redo на PdfDocumentState восстанавливает штрихи и подсветки, canUndo/canRedo синхронны | undo()/redo() pop their stacks, restore the page snapshot (strokes + highlights), pushUndoSnapshot clears redo. | 1623c81 | app/byCompose/common/src/commonTest/kotlin/ru/kyamshanov/notepen/tabs/PdfDocumentStateUndoTest.kt | PEND |
| RC-UX-002 | 🟠 | 2 | gesture | Пинённые кнопки Undo/Redo всегда видны в тулбаре при активном инструменте | Undo/Redo are always-visible pinned buttons; enabled state mirrors canUndo/canRedo and they drive undo/redo. | 1623c81 | → AV-UX-01 | PEND |
| RC-UX-003 | 🟠 | 2 | gesture | Системные кнопки (чтение/миниатюры) остаются в колесе при активном инструменте | The system control group stays in the wheel even while a drawing tool with settings is active; overflow scrolls. | 1623c81 | → AV-UX-02 | PEND |
| RC-UX-004 | 🟠 | 3 | manual | Picker: FB2/CBZ/CBR (octet-stream от провайдера) выбираемы и определяются по имени | FB2/CBZ/CBR files surfaced as octet-stream are pickable and the chosen file is format-detected by display name. | 1623c81 | release-checklist | PEND |
| RC-UX-005 | 🟠 | 2 | visual | Режим чтения резервирует место под плавающий хром — первая строка/заголовок не перекрыты | Reader content is inset by a static top+start reserve so the first line/heading is fully visible; no re-pagination on toggle. | 1623c81 | → AV-UX-03 | PEND |
| RC-UX-006 | 🟠 | 1 | unit | Вход в чтение со страницы N приземляет ридер у страницы N, а не на титул | Seeding the durable anchor lands the pager within ±1 of the target page and never at page 0 for N>0. | 1623c81 | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/ReflowPageLocatorTest.kt | PEND |
| RC-UX-007 | 🟡 | 1 | unit | Recents: реальный размер файла включает SAF fuzzy-дедуп — повторное открытие не дублирует | A re-picked file with a non-null size triggers SafFuzzyMatchDetected and is not duplicated in recents. | 1623c81 | shared/src/commonTest/kotlin/ru/kyamshanov/notepen/mainscreen/domain/usecase/AddToHistoryUseCaseTest.kt | PEND |
| RC-UX-008 | 🟡 | 1 | unit | Таблицы reflow: ширины колонок пропорциональны содержимому — слова не рвутся по символам | Column weight tracks the longest cell's char length, never collapses below the minimum, spans the widest row's columns. | 1623c81 | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/ui/TableColumnWeightsTest.kt | PEND |
| RC-UX-009 | 🟡 | 2 | visual | Открытие документа: спиннер «Открываем книгу…» и подавление счётчика «1 / 0» | While loading: spinner + label and no page counter; once page count is known: counter appears with the real total. | 1623c81 | → AV-UX-04 | PEND |
| RC-UX-010 | 🟡 | 2 | visual | Эскизы recents для EPUB/FB2 рендерят обложку, а не битую картинку | EPUB/FB2 recents render a real cover thumbnail produced by converting the book to PDF and rasterising page 0. | 1623c81 | → AV-UX-05 | PEND |

### VIEWER — Multi-page viewer

| ID | Sev | Tier | Type | Scenario (RU) | Expected | Source | Coverage | Status |
|---|---|---|---|---|---|---|---|---|
| RC-VIEWER-001 | 🟠 | 1 | unit | Лупа: round-trip panel↔page точен при любом размере панели (нет дрейфа на ресайзе) | Forward and inverse mappings are exact inverses for every panel size; segTop preserved so multi-segment strips stay aligned. | 10af41b | drawing/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/magnifier/MagnifierSegmentMappingTest.kt | PEND |
| RC-VIEWER-002 | 🟠 | 2 | visual | Лупа на десктопе: ввод пера не уезжает от содержимого при ресайзе окна | After resize the freshly drawn ink stays exactly under the pen cursor inside the loupe; no vertical offset. | 10af41b | → AV-VIEWER-01 | PEND |
| RC-VIEWER-003 | 🟠 | 1 | unit | Поворот страницы +90° CW: углы и штрихи отображаются по контракту растра | Rotation math is the exact CW raster contract; stroke width and extent transform correctly. | 10af41b | drawing/api/src/commonTest/kotlin/ru/kyamshanov/notepen/annotation/domain/model/PageRotationTest.kt | PEND |
| RC-VIEWER-004 | 🟠 | 2 | visual | Поворот страницы в редакторе: штрихи остаются поверх того же содержимого | After each +90° the drawn stroke remains over the same content; at 360° page and ink return to the original orientation. | 10af41b | → AV-VIEWER-02 | PEND |
| RC-VIEWER-005 | 🟠 | 1 | unit | Разделение разворотов: индексы, кропы и штрихи раскидываются по половинам и обратимы | Logical/source index math, per-half crop, point/path remap, and rotation map all round-trip exactly. | 10af41b | drawing/api/src/commonTest/kotlin/ru/kyamshanov/notepen/annotation/domain/model/SpreadSplitTest.kt | PEND |
| RC-VIEWER-006 | 🟠 | 1 | unit | Книжный разворот: пары делят Y-ряд, левая/правая колонки, fit и центрирование по всей паре | Spread layout, navigation index snapping, fit-width zoom, and centring all account for the full two-column pair. | 10af41b | rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesLayoutTest.kt | PEND |
| RC-VIEWER-007 | 🟡 | 1 | unit | Выделение текста в reflow: selectedText извлекает корректный диапазон по блокам | Clipboard text extraction returns the exact selected substrings, newline-joined across blocks, range-clamped. | 10af41b | reflow/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/reflow/ui/SelectedTextTest.kt | PEND |
| RC-VIEWER-008 | 🟠 | 2 | gesture | Выделение текста в reflow на эмуляторе: тап выделяет именно ту строку, по которой тапнули | The highlighted selection covers exactly the line the finger touched, not the line above/below. | 10af41b | → AV-VIEWER-03 | PEND |
| RC-VIEWER-009 | 🟡 | 3 | manual | Поворот не оставляет stale-инсеты хрома при повороте экрана (sticky orientation) | Chrome insets re-key on orientation each rotation; no stale toolbar width / top-bar height carried over. | 10af41b | release-checklist | PEND |
| RC-VIEWER-010 | 🟠 | 1 | unit | clampPan: страница-в-экран держится в границах, при переполнении ширины — горизонтальный буфер | Per-axis clamp: fitting axis bounded on-screen; overflowing horizontal axis gets the overscroll buffer; vertical never does. | 235516d | rendering/impl/src/commonTest/kotlin/ru/kyamshanov/notepen/pdfviewer/PdfPagesLayoutTest.kt | PEND |
| RC-VIEWER-011 | 🟡 | 2 | visual | Сайдбар миниатюр поверх тул-рейла и привязан к левому краю активной панели; airbar к центру | Thumbnail sidebar is on top at the panel's left edge; airbar springs to the focused panel centre on focus change. | 235516d | → AV-VIEWER-04 | PEND |
| RC-VIEWER-012 | 🟠 | 2 | gesture | Android: одним пальцем скроллим ЗА рамкой страницы при активном инструменте, рисуем ВНУТРИ | With a tool active: drag inside the page draws ink; drag outside the page frame scrolls the document. | cd9640a | → AV-VIEWER-05 | PEND |
| RC-VIEWER-013 | 🟠 | 2 | visual | Android: страницы перерисовываются (резкость) ВО ВРЕМЯ непрерывного скролла, а не после остановки | Incoming pages sharpen within ~100ms while still scrolling; they do not stay blurry/blank until the scroll stops. | d0c45b7 | → AV-VIEWER-06 | PEND |

## Coverage gaps

Tier-1 regression tests that SHOULD exist but are not yet automated (Coverage = `needed`), grouped by area:

- **ANDROID:** RC-ANDROID-001, RC-ANDROID-002, RC-ANDROID-003, RC-ANDROID-005, RC-ANDROID-009
- **ANNOT:** RC-ANNOT-002, RC-ANNOT-007
- **COMMON:** RC-COMMON-003
- **DESKTOP:** RC-DESKTOP-002, RC-DESKTOP-004, RC-DESKTOP-005, RC-DESKTOP-007, RC-DESKTOP-008, RC-DESKTOP-009, RC-DESKTOP-011, RC-DESKTOP-013, RC-DESKTOP-016, RC-DESKTOP-017
- **MAG:** RC-MAG-001
- **PDF:** RC-PDF-001, RC-PDF-005, RC-PDF-007, RC-PDF-008
- **STARTUP:** RC-STARTUP-001, RC-STARTUP-002, RC-STARTUP-003
- **SYNC:** RC-SYNC-001, RC-SYNC-002, RC-SYNC-003, RC-SYNC-004, RC-SYNC-005, RC-SYNC-007, RC-SYNC-008, RC-SYNC-009, RC-SYNC-010
- **UI:** RC-UI-001, RC-UI-012, RC-UI-016
- **UIKIT:** RC-UIKIT-002

## Changelog

- v1 (2026-05-30) — initial catalog, 205 cases mined from 99 fix commits.
