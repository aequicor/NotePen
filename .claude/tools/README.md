# NotePen automated UX-testing tooling

Tooling that lets a Claude Code session test the **Desktop (macOS Compose)** and
**Android** apps on its own, without permission prompts. Everything here lives
under the gitignored `.claude/` tree — it is local to this machine, never
committed.

| Surface | How Claude drives it | Setup |
|---|---|---|
| Desktop GUI (exploratory) | `notepen-desktop` MCP server — screenshot + click/type | one-time macOS permission grant ↓ |
| Desktop (deterministic) | Roborazzi compose-desktop tests in `:reflow:impl` | none |
| Android (real device + emulator) | `notepen-android` helper (adb/emulator/gradle) | none |

The allowlist in [`.claude/settings.json`](../settings.json) pre-approves the MCP
server and the `adb` / `emulator` / `gradlew` / `screencapture` / `sips` /
`osascript` / `open` commands so an auto-mode session never stalls on a prompt.

---

## 1. Desktop GUI MCP — `notepen-desktop`

A self-contained Node MCP server ([desktop-mcp/server.js](desktop-mcp/server.js))
that uses **only built-in macOS tools** (`screencapture`, `sips`, `osascript`
JXA/CoreGraphics + System Events). No Homebrew, no Swift, no AI key, no
third-party native binaries — fully auditable.

Registered at **local scope** (`claude mcp list` → `notepen-desktop`). Tools:

| Tool | What it does |
|---|---|
| `screenshot` | Capture the screen. The preview is downsized to the display's **logical** size, so a pixel you read off it equals the `x,y` you pass to `click`. Optional `path` saves full-res PNG; optional `region {x,y,w,h}`. |
| `click` / `double_click` / `move` / `drag` | Mouse, in logical points (top-left origin). `drag` is good for strokes / page swipes. |
| `type_text` | Type a string (newlines → Return). |
| `press_key` | One key + modifiers — `return tab escape left right up down pageup pagedown home end space delete f1-f12` or a single char; modifiers `command control option shift`. |
| `activate_app` | Foreground an app by name — `"NotePen"` (release bundle) or `"java"` (gradle `runDesktop`). |
| `list_windows` | Window titles + position + size for a process — find click targets. |

### ⚠️ One-time permission grant (you must do this once — Claude cannot)

macOS gates screen capture and synthetic input behind per-app privacy
permissions. Grant them to **the app that runs Claude Code** (Terminal,
iTerm2, Ghostty, VS Code, …):

1. **System Settings ▸ Privacy & Security ▸ Screen Recording** → enable your
   terminal app. (needed for `screenshot`)
2. **System Settings ▸ Privacy & Security ▸ Accessibility** → enable your
   terminal app. (needed for `click` / `type_text` / `list_windows`)
3. **Quit and reopen the terminal** — macOS only applies the grant to newly
   launched processes.

If a tool returns a permission error it names which grant is missing.

### Launching the desktop app for testing

```
./gradlew runDesktop          # quick; process shows up as "java"
# or, closer to release (recommended by the autonomous-QA prompt):
./gradlew :app:byCompose:desktop:createDistributable \
  && open app/byCompose/desktop/build/compose/binaries/main/app/NotePen.app
```

Then: `screenshot` → read coordinates → `click` / `type_text` / `drag`.

---

## 2. Deterministic desktop tests — Roborazzi

Headless Compose-Desktop screenshot tests, no GUI window or OS permission
needed. The reliable regression net.

- Tests: `ReaderSnapshotTest`, `BaranovskayaSnapshotTest` (in
  `reflow/impl/src/jvmTest/.../ui` and `.../fixtures`).
- 9 committed goldens in `reflow/impl/snapshots/`.

```
# verify against committed goldens (CI-style)
./gradlew :reflow:impl:jvmTest --tests "*SnapshotTest" -Dorg.gradle.jvmargs="-Xmx4g"

# re-record goldens after an INTENTIONAL visual change, then commit the PNGs
./gradlew :reflow:impl:jvmTest --tests "*SnapshotTest" -Proborazzi.test.record=true --rerun-tasks
```

Any unexpected golden diff = a regression to investigate, not to re-record.

---

## 3. Android helper — `notepen-android`

[`bin/notepen-android`](bin/notepen-android) wraps adb/emulator/gradle. Targets
the sole connected device by default; override with `NOTEPEN_SERIAL=<serial>`.

```
.claude/tools/bin/notepen-android devices
.claude/tools/bin/notepen-android boot-emulator        # AVD Medium_Phone_API_36.1
.claude/tools/bin/notepen-android install              # :app:...:installDebug
.claude/tools/bin/notepen-android launch               # ru.kyamshanov.notepen.debug
.claude/tools/bin/notepen-android shot run/lib.png 1400
.claude/tools/bin/notepen-android tap 540 1600
.claude/tools/bin/notepen-android swipe 900 1200 200 1200 250
.claude/tools/bin/notepen-android rotate landscape
.claude/tools/bin/notepen-android push "/path/to/fixture.pdf"   # -> /sdcard/Download/
.claude/tools/bin/notepen-android backup .claude/ux-reports/<run>/huawei-backup
```

Known devices: Huawei `44RUN24B09G03494` (real stylus) and emulator
`emulator-5554`. The debug build installs as `ru.kyamshanov.notepen.debug`
(`.debug` suffix), so it sits next to any production install.

> Before reinstalling on the Huawei, run `backup` first — it holds real user
> annotations. See `.claude/prompts/reader-uiux-autonomous.md` for the
> ustar-repack restore dance.

---

## Putting it together

The existing playbooks in [`.claude/prompts/`](../prompts/) describe full UX
passes; they assumed a `mcp__computer-use__*` server that didn't exist. The
`notepen-desktop` MCP now fills that role — substitute `mcp__notepen-desktop__*`
for the old `mcp__computer-use__*` tool names.

---

## 4. `ai-vision` keyword hook

A `UserPromptSubmit` hook so that whenever your prompt mentions **`ai-vision`**
(case-insensitive), Claude is told to DO the visual work itself with this harness
— screenshot, record an animation, build a frame-by-frame filmstrip, and show the
artifacts — instead of just describing it.

- Hook script: [`ai-vision-hook.sh`](ai-vision-hook.sh) (POSIX/macOS). The Windows
  twin lives at `tools/uitest/ai-vision-hook.ps1`.
- Install once (wires it into the gitignored `.claude/settings.local.json`, so it
  survives AI-Kit regeneration of `settings.json`):

  ```
  node .claude/tools/install-ai-vision-hook.mjs
  ```

  Then open `/hooks` once (or restart Claude Code) so the new hook is picked up.
  Re-running the installer is a no-op.
