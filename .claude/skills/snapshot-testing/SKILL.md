---
name: snapshot-testing
description: How to verify UI in NotePen with a parallel-friendly layer first — JVM screenshot/snapshot tests that need no emulator and run concurrently across worktrees (each has its own build/), falling back to a real device only for final E2E, one device per session. Use when asked to test the UI, check a visual change, add a snapshot, investigate a screenshot diff, or verify a Compose screen.
---

# snapshot-testing

Слой UI-проверки для **NotePen**, который не дерётся за эмулятор в параллельных
сессиях. Принцип: основную массу регрессий ловим JVM-снапшотами (без устройства), на реальном
устройстве — только финальное E2E одной фичи.

## Почему снапшоты первыми

JVM-снапшоты рендерят Compose на хосте, без эмулятора, и пишут в `build/` своего worktree. Поскольку
`build/` у каждой сессии свой, несколько сессий гоняют снапшоты **параллельно** без коллизий — в
отличие от эмулятора, который общий. Поэтому при параллельной работе снапшоты — слой по умолчанию.

## Roborazzi (выбран в этом проекте)

JVM-рендер Compose через Robolectric Native Graphics (`@GraphicsMode(GraphicsMode.Mode.NATIVE)`),
эмулятор не нужен. Кросс-платформенный (поддерживает Compose Multiplatform — desktop, iOS).

| Действие | Команда |
|---|---|
| Записать/обновить эталоны | `./gradlew recordRoborazzi<Variant>` (напр. `recordRoborazziDebug`) |
| Проверить против эталонов | `./gradlew verifyRoborazzi<Variant>` (напр. `verifyRoborazziDebug`) |
| Сравнить и собрать диффы | `./gradlew compareRoborazzi<Variant>` |

Вывод и диффы — в `<module>/build/outputs/roborazzi/` (картинка `golden | actual | diff`).

Точные имена тасок и поддержку конкретного KMP-плагина (включая
`com.android.kotlin.multiplatform.library`) сверь с версией Roborazzi, закреплённой в проекте, через
`./gradlew tasks --all` — не считай по памяти.

## Разбор диффа

При расхождении прочитай PNG-дифф тулом `Read` — встроенное зрение позволяет классифицировать тип
регрессии (сдвиг элемента, смена цвета, исчезновение текста) и решить: это баг (чинить код) или
намеренное изменение (перезаписать эталон record/update-таской). Эталоны коммить вместе с фичей.

## E2E на устройстве — только финал, по устройству на сессию

Когда снапшотов мало (жесты, реальная клавиатура, системные диалоги) — переходи на устройство, строго
своего лейна (см. скил **parallel-sessions**).

- **claude-in-mobile MCP:** перед действиями выбери устройство своего лейна (`$ANDROID_SERIAL` /
  `$IOS_SIM_UDID`), не «первое в списке». Запусти приложение, обойди экраны, проверь элементы и
  доступность.

Никогда не запускай установку/прогон на устройстве без серийника/UDID своего лейна — попадёшь в чужую
сессию.
