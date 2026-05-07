---
id: TD-common-drag-end-monitor-broadcast
module: common
category: smell
severity: low
status: open
discovered: 2026-05-07
discovered_by: CodeWriter
task: feat-drag-drop-pdf
---

# TD-common-drag-end-monitor-broadcast

## Problem

`RecentFileCard` attaches a monitoring `dragAndDropTarget` with `shouldStartDragAndDrop = { true }` to detect when a drag session ends via `onEnded → onDragCancelled()`. Because `shouldStartDragAndDrop` returns `true` unconditionally, **every** `RecentFileCard` on screen participates in every drag session — not just the card that initiated the drag. When card A is dragged, all other cards' `dragEndMonitor` also fire `onEnded` and invoke `DragCancelled`. The ViewModel handles this gracefully (`DragCancelled` is idempotent when `dragState` is `None`), but the redundant callbacks waste CPU cycles and add noise to the intent stream.

## Location

| File | Lines | Notes |
|------|-------|-------|
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/ui/component/RecentFileCard.kt` | 51–73 | `dragEndMonitor` with `shouldStartDragAndDrop = { true }` |

## Deferral rationale

The `dragAndDropSource` API (CMP 1.9.0) does not expose `onDragEnd`/`onDragCancel` callbacks to the source composable. The `onTransferCompleted` callback on Desktop's `DragAndDropTransferData` is `@ExperimentalComposeUiApi` and platform-specific. Implementing a proper fix requires either a platform `expect/actual` for `onTransferCompleted` or a shared `DragSessionTracker` abstraction — both are beyond the scope of the current HIGH #1 fix.

## Suggested fix

Option A (platform-specific): On Desktop, use `DragAndDropTransferData.onTransferCompleted` in `DragTransferData.jvm.kt` to notify the ViewModel via a callback. On Android, keep the monitoring target approach but use `shouldStartDragAndDrop = { event -> event.dragEventMimeTypes().contains("text/plain") && isBeingDragged }` to limit participation to the card that is actively dragging.

Option B (shared abstraction): Introduce a `DragSessionTracker` singleton scoped to the composable tree; the source card registers itself on drag start and unregisters on end. Other cards skip participation when a drag is active from a different card.

## References

- Originating task: feat-drag-drop-pdf
- Related feature: `vault/features/common/drag-drop-pdf/feature.md`
- HIGH #1 fix: Stages 3 & 4 review findings
