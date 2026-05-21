---
id: TD-common-macos-dnd-disabled-metal-imagebitmap
module: common
category: deprecation
severity: medium
status: open
discovered: 2026-05-21
discovered_by: Claude
task: fix-desktop-thumbnails-not-rendering
---

# TD-common-macos-dnd-disabled-metal-imagebitmap

## Problem

На macOS Skiko рендерит через Metal. Регистрация AWT `DropTarget` — которую Compose
выполняет на единственном `SkiaLayer` окна при наличии **любого** drag-and-drop
модификатора (`Modifier.dragAndDropTarget` **или** `Modifier.dragAndDropSource`) —
ломает present `ImageBitmap`: только что появившиеся `Image`-узлы (миниатюры PDF/картинок)
не доходят до экрана до следующей непредвиденной перерисовки окна. На Windows тот же
конфликт (DirectX/ANGLE) обходится переключением `skiko.renderApi=OPENGL`; на macOS
Skiko OpenGL не поддерживает (`MacOS does not support OPENGL rendering API`), а бамп
Skiko до 0.9.37.4 (через CMP 1.10.3) проблему не устранил.

Чтобы миниатюры рендерились, drag-and-drop на macOS **полностью отключён** через флаг
[isDragAndDropSupported]. Следствие: на macOS нельзя перетащить файл из Finder в
библиотеку/папку и перетащить недавний файл в папку. Взамен добавлены меню «добавить в
папку» на карточке и диалог «добавить из недавних» в экране папки (работают на всех
платформах); импорт с диска — через «Открыть» / FAB.

## Location

| File | Lines | Notes |
|------|-------|-------|
| `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/mainscreen/platform/DragAndDropSupport.kt` | — | `expect val isDragAndDropSupported` |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/mainscreen/platform/DragAndDropSupport.jvm.kt` | — | `false` на macOS (`os.name` startsWith "Mac") |
| `app/byCompose/common/src/jvmMain/kotlin/ru/kyamshanov/notepen/mainscreen/platform/ThumbnailPainter.desktop.kt` | — | синхронный Skia-декод; именно его present теряется |
| `MainContent.kt`, `FolderContent.kt`, `FolderCard.kt`, `RecentFileCard.kt` | — | гейтинг DnD-модификаторов по флагу |

## Deferral rationale

Это архитектурное ограничение связки Compose Desktop + Skiko (Metal) на macOS, а не баг
прикладного кода. Надёжного обходного пути, сохраняющего и DnD, и аппаратное ускорение,
на текущих версиях нет (проверены: produceState, форс-перерисовка кадров, бамп Skiko —
не помогли; OpenGL недоступен; SOFTWARE-рендер недопустим для приложения с рисованием пером).

## Suggested fix

- Отслеживать апстрим Skiko/Compose Multiplatform: при появлении фикса present для Metal
  при активном AWT DropTarget — снять гейтинг (вернуть `isDragAndDropSupported = true` на macOS).
- Альтернатива: регистрировать DropTarget на отдельном AWT-компоненте вне Skia-surface
  (высокая сложность, уход от штатного Compose DnD) — рассматривать только если ограничение
  станет критичным.

## References

- Originating task: fix-desktop-thumbnails-not-rendering
- Related: `[[tech-debt/common/drag-end-monitor-broadcast]]`
- Windows-аналог фикса (OpenGL): `app/byCompose/desktop/src/desktopMain/kotlin/main.kt` (ветка для Windows)
