# NotePen — Progress

_Last updated: 2026-05-15_

## Milestones

### M4 — PDF export ✅
- `PdfExporter` port в `:shared`
- `JvmPdfExporter` (PDFBox 3.x, Catmull-Rom Bézier, Y-flip, alpha через `PDExtendedGraphicsState`)
- `AndroidPdfExporter` (PdfRenderer + android.graphics.pdf.PdfDocument)
- Кнопка Export в `PdfFloatingToolbar` → `DetailsContent`

### M5 — сервер + mDNS + паринг ✅ (Desktop end-to-end)
- Модуль `:server` — `KtorPeerServer` (CIO WebSocket, `PairingManager`, 6-значные коды)
- `KtorSyncClient` в `:shared` — `connect()`, `send()` через `Channel<NetworkMessage>`
- `JmDnsDeviceDiscovery` + `JmDnsServiceRegistrar` (jvmMain `:app:byCompose:common`)
- `NsdDeviceDiscovery` для Android (только клиент; `NsdServiceRegistrar` не реализован)
- `HostViewModel` + `SyncViewModel` + `HostScreen` + `SyncScreen` в commonMain
- `App.kt` — FAB → Dialog с HostScreen/SyncScreen
- `main.kt` — сервер, mDNS-регистрация, ViewModels передаются в `App`

### M6 — синхронизация штрихов ✅ (Desktop end-to-end, PEN + MARKER)
- `StrokeDelta` (Added/Removed), `DrawingPathDto`, LWW `SyncEngine`
- `SyncBridge` применяет удалённые штрихи к `PdfDrawingState`
- `DrawablePdfPage.onStrokeFinished` → `engine.applyLocal(StrokeDelta.Added)`
- `DetailsContent` принимает `SyncEngine?`, создаёт и запускает `SyncBridge`
- `main.kt` подписывается на `incomingMessages` обоих транспортов → `engine.processPeer()`

### M7 — projection mode ⚠️ (код написан, не подключён)
- `ProjectionHostController`, `ProjectionViewerController` в `:shared`
- `ProjectionViewerOverlay`, `ProjectionHostBadge` composable написаны
- **Не подключено** к `DetailsContent` — нет UI-точки входа

### M8 — polish ✅
- `LruCache<K,V>` для кеша bitmap страниц (maxSize=8)
- `semantics { role = Button; selected }` на кнопках тулбара
- ProGuard keep-правила для serialization + sync domain models

---

## Известные ограничения / следующие задачи

| Задача | Файлы | Приоритет |
|---|---|---|
| **Дошить M7** — подключить projection к `DetailsContent` | `ProjectionHostController.kt`, `ProjectionViewerController.kt`, `DetailsContent.kt`, `main.kt` | Высокий |
| **Erase sync** — `eraseInZone` не эмитит `StrokeDelta.Removed` | `DrawablePdfPage.kt`, `DetailsContent.kt` | Средний |
| **Android как хост** — добавить `NsdServiceRegistrar` через `NsdManager.registerService()` | `androidMain/sync/infrastructure/` | Средний |
| **Undo/redo sync** — локальные операции не проходят через `SyncEngine` | `DetailsContent.kt`, `SyncEngine.kt` | Низкий |
| **File transfer receive()** — сборка чанков — заглушка | `WebSocketFileTransfer.kt` | Низкий |

---

## Архитектура sync (Desktop)

```
main.kt
  ├── KtorPeerServer  ──→  HostViewModel → HostScreen
  ├── KtorSyncClient  ──→  SyncViewModel → SyncScreen
  ├── JmDnsServiceRegistrar  (регистрируется при старте сервера)
  ├── JmDnsDeviceDiscovery   (передаётся в SyncViewModel)
  └── SyncEngine
        ← peerServer.incomingMessages → processPeer()
        ← syncClient.incomingMessages → processPeer()
        → App → RootContent → DetailsContent → SyncBridge → PdfDrawingState
```

### Как дошить M7 (следующая сессия)

1. Создать `ProjectionHostController(server=peerServer, scope=appScope)` в `main.kt`
2. Создать `ProjectionViewerController(client=syncClient, scope=appScope)` в `main.kt`
3. Подписать `peerServer.incomingMessages` → `viewerController.onFrame(msg)` (если роль — зритель)
4. Передать оба контроллера через `App` → `RootContent` → `DetailsContent`
5. В `DetailsContent`: обновлять `hostController` при скролле (`lazyListState`); показывать `ProjectionViewerOverlay` поверх PDF
