# Glass backdrop source в UIKit sandbox

## Контекст

Compose Desktop падал с `NTSTATUS 0xC00000FD`, когда `glassSource()` стоял на том же контейнере, внутри которого рисовались `LiquidGlass*` поверхности. Во время записи `GraphicsLayer` эти поверхности снова сэмплировали тот же layer, что приводило к рекурсивному draw и переполнению стека нативного desktop-рантайма.

## Источник истины

- `app/byCompose/uikit-sandbox/src/commonMain/kotlin/ru/kyamshanov/notepen/uikitsandbox/presentation/ui/UikitSandboxAppContent.kt`
- Ключевой символ: `SandboxThemeSurface`
- Общий механизм: `GlassBackdropProvider`, `Modifier.glassSource()`, `GlassSurface`

## Инварианты

`glassSource()` должен записывать только фон/backdrop. Все `LiquidGlassCard`, `LiquidGlassTopBar` и другие `GlassSurface`-потомки должны быть отдельными sibling-узлами выше source-layer, а не находиться внутри узла, на котором стоит `glassSource()`.

Если нужно поменять layout sandbox-экрана, сохраняйте структуру: отдельный фоновый `Box(...liquidGlassHero().glassSource())`, затем UI-контент поверх него.

## Проверка

- `./gradlew :app:byCompose:uikit-sandbox:desktop:run --quiet` запускался в bounded harness; после 18 секунд процесс оставался живым, без `0xC00000FD`.
- `./gradlew :app:byCompose:uikit-sandbox:ktlintCheck :app:byCompose:uikit-sandbox:desktop:ktlintCheck :app:byCompose:uikit-sandbox:jvmTest :app:byCompose:uikit-sandbox:desktop:assemble`
