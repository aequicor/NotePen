---
title: JBR title bar hit-test в полосе вкладок редактора
---

# JBR title bar hit-test в полосе вкладок редактора

На Desktop/JBR строка вкладок редактора одновременно является кастомным title bar. Если все вкладки пометить как `interactive`, чистые клики по неактивным вкладкам работают, но при одной активной вкладке она занимает всю ширину и у окна не остаётся drag-зоны.

Источник истины: `app/byCompose/common/src/commonMain/kotlin/ru/kyamshanov/notepen/tabs/TabBar.kt` (`TabBar`, `TabChip`, `TabChromeHitMode`) и desktop-адаптер `app/byCompose/desktop/src/desktopMain/kotlin/JbrTitleBar.kt` (`TitleBarInteraction.dragArea` / `interactive`).

Инварианты:

- неактивные вкладки, «Сессии» и `+` должны оставаться `interactive`, иначе регрессирует переключение вкладки/первый клик по меню;
- активная вкладка является `dragArea`, потому что клик по ней не переключает документ и это даёт пользователю место для перемещения окна;
- крестик закрытия активной вкладки обязательно остаётся `interactive`, иначе drag-зона родителя перехватит закрытие.

Проверено:

- `./gradlew ktlintFormatFile -PktlintFile=C:\Users\kruz18\IdeaProjects\NotePen2\app\byCompose\common\src\commonMain\kotlin\ru\kyamshanov\notepen\tabs\TabBar.kt`
- `./gradlew :app:byCompose:common:compileKotlinJvm`

Связанные регрессии: `testing/regression-cases.md` — `RC-SESSION-004`, `RC-TABS-004`; ручной desktop/JBR title bar чек — `testing/release-checklist.md` E1.
