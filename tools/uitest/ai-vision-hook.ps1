<#
    UserPromptSubmit hook: when the user's prompt mentions "ai-vision" (case-insensitive),
    inject context telling Codex to DO the visual work itself via the tools/uitest harness.

    Wired from .codex/hooks.json and .claude/settings.local.json. Reads the hook payload (JSON), emits a JSON
    object with hookSpecificOutput.additionalContext on stdout when the keyword is present, else nothing.
#>
$ErrorActionPreference = 'Stop'

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }
try { $data = $raw | ConvertFrom-Json } catch { exit 0 }

$prompt = [string]$data.prompt
if (-not ($prompt -match '(?i)ai-vision')) { exit 0 }

$context = @'
The user's message contains the keyword "ai-vision". This is a standing instruction for Codex:
autonomously DO the visual / UI work yourself and SHOW the result. Do not only describe what could be
tested. Launch the live app, drive it, take real screenshots, record animations when motion matters,
produce frame-by-frame filmstrips, and display the captured PNG / .gif.filmstrip.png artifacts in the
reply using absolute local paths.

Use the NotePen UI-testing harness in tools/uitest/ (read tools/uitest/README.md first). Pick the
platform(s) relevant to the request:

- Desktop (Windows): tools/uitest/Launch-Desktop.ps1 launches/locates the "NotePen" window. Drive the
  window with tools/uitest/Drive-Desktop.ps1:
  -Action capture/click/doubleClick/drag/scroll/type/key/minimize/maximize/restore/hide/show.
  Coordinates are client-relative physical pixels; run -Action rect or capture first to orient.
  Use tools/uitest/Capture-DesktopAnim.ps1 to record animation GIF + filmstrip.
- Android phone/tablet (emulator or device): tools/uitest/Start-AndroidTarget.ps1 -Serial <serial> or
  -Avd <name> (phone AVD Medium_Phone_API_36.1; tablet AVD NotePen_Tablet_API_36_1, created by
  tools/uitest/New-TabletAvd.ps1). Drive via `adb -s <serial> shell input ...`; screenshot via
  `adb -s <serial> exec-out screencap -p`. Record with tools/uitest/Capture-AndroidAnim.ps1.
- tools/uitest/Capture-Gif.ps1 assembles any folder of PNG frames into a looping GIF + PNG filmstrip.

Write artifacts under .claude/ux-reports/<runid>/ or tools/uitest/out/ and include them in the final
answer. If a GUI target is unavailable, state the specific blocker and still capture any reachable
fallback signal.
'@

$payload = @{
    hookSpecificOutput = @{
        hookEventName     = 'UserPromptSubmit'
        additionalContext = $context
    }
} | ConvertTo-Json -Depth 5 -Compress

[Console]::Out.Write($payload)
exit 0
