<#
    UserPromptSubmit hook: when the user's prompt mentions "ai-vision" (case-insensitive),
    inject context telling Claude to DO the visual work itself via the tools/uitest harness.

    Wired from .claude/settings.local.json. Reads the hook payload (JSON) on stdin, emits a JSON
    object with hookSpecificOutput.additionalContext on stdout when the keyword is present, else nothing.
#>
$ErrorActionPreference = 'Stop'

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { exit 0 }
try { $data = $raw | ConvertFrom-Json } catch { exit 0 }

$prompt = [string]$data.prompt
if (-not ($prompt -match '(?i)ai-vision')) { exit 0 }

$context = @'
The user's message contains the keyword "ai-vision". This is a STANDING INSTRUCTION: they want you to
autonomously DO the visual / UI work yourself and SHOW the result -- not just describe it. Take real
screenshots, record animations, and produce frame-by-frame filmstrips, then display the captured image
files in your reply (Read the PNG / .gif.filmstrip.png artifacts so they render for the user).

Use the Claude-Code UI-testing harness in tools/uitest/ (read tools/uitest/README.md first). Pick the
platform(s) relevant to the request:

- Desktop (this Windows machine): tools/uitest/Launch-Desktop.ps1 to launch/locate the "NotePen" app
  window (grant java.exe to drive it via computer-use); tools/uitest/Capture-DesktopAnim.ps1 to record
  an animation as GIF + filmstrip.
- Android phone/tablet (emulator or device): tools/uitest/Start-AndroidTarget.ps1 -Serial <serial> or
  -Avd <name> (phone AVD Medium_Phone_API_36.1; tablet AVD NotePen_Tablet_API_36_1, created by
  tools/uitest/New-TabletAvd.ps1). Drive via `adb -s <serial> shell input ...`; screenshot via
  `adb -s <serial> exec-out screencap -p`. Record with tools/uitest/Capture-AndroidAnim.ps1.
- tools/uitest/Capture-Gif.ps1 assembles any folder of PNG frames into a looping GIF + PNG filmstrip.

Actually run the scripts and capture artifacts; then Read/show them. Do not merely explain what could be done.
'@

$payload = @{
    hookSpecificOutput = @{
        hookEventName     = 'UserPromptSubmit'
        additionalContext = $context
    }
} | ConvertTo-Json -Depth 5 -Compress

[Console]::Out.Write($payload)
exit 0
