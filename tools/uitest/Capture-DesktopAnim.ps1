<#
.SYNOPSIS
    Record an animation of the running NotePen desktop window as a GIF + filmstrip.

.DESCRIPTION
    Captures the app window region with GDI BitBlt (Graphics.CopyFromScreen) in a timed loop, then
    hands the frames to Capture-Gif.ps1. Faster and steadier than computer-use screenshots, so the
    GIF framerate is tight.

    Trigger the animation you want to record JUST BEFORE (or right after) starting this script — the
    capture runs for -DurationMs from the moment it starts.

.PARAMETER DurationMs   Capture length in ms (default 1500).
.PARAMETER Fps          Target frames per second (default 12).
.PARAMETER Out          Output .gif path (default out/desktop/<timestamp>/anim.gif).
.PARAMETER TitleLike    Window title match (default '*NotePen*').

.EXAMPLE
    ./Capture-DesktopAnim.ps1 -DurationMs 2000 -Fps 15
#>
[CmdletBinding()]
param(
    [int]$DurationMs = 1500,
    [int]$Fps = 12,
    [string]$Out,
    [string]$TitleLike = 'NotePen'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
. "$PSScriptRoot\_Win32.ps1"

$win = Get-UiTestWindow -TitleLike $TitleLike
if (-not $win) { throw "No window matching '$TitleLike'. Launch the app first (Launch-Desktop.ps1)." }

# Restore (if minimized) + foreground so nothing overlaps the captured region, then re-read bounds.
$win = Show-UiTestWindow -Hwnd $win.Hwnd -TitleLike $TitleLike
if (-not $win) { throw "Window '$TitleLike' vanished after restore." }
Write-Host "Capturing window '$($win.Title)' [$($win.Width)x$($win.Height)] for ${DurationMs}ms @ ${Fps}fps"

if (-not $Out) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $Out = Join-Path $PSScriptRoot "out\desktop\$stamp\anim.gif"
}
$framesDir = Join-Path (Split-Path -Parent $Out) 'frames'
New-Item -ItemType Directory -Force -Path $framesDir | Out-Null

$intervalMs = [int](1000 / $Fps)
$bmp = New-Object System.Drawing.Bitmap $win.Width, $win.Height
$g = [System.Drawing.Graphics]::FromImage($bmp)
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$i = 0
try {
    while ($sw.ElapsedMilliseconds -lt $DurationMs) {
        $tick = [System.Diagnostics.Stopwatch]::StartNew()
        # Re-read bounds each frame in case the window moved.
        $cur = Get-UiTestWindow -TitleLike $TitleLike
        if ($cur) { $g.CopyFromScreen($cur.X, $cur.Y, 0, 0, $bmp.Size) }
        $bmp.Save((Join-Path $framesDir ("f{0:D4}.png" -f $i)), [System.Drawing.Imaging.ImageFormat]::Png)
        $i++
        $rest = $intervalMs - [int]$tick.ElapsedMilliseconds
        if ($rest -gt 0) { Start-Sleep -Milliseconds $rest }
    }
} finally {
    $g.Dispose(); $bmp.Dispose()
}
Write-Host "Captured $i frame(s)."

$delay = [int](1000 / $Fps)
& "$PSScriptRoot\Capture-Gif.ps1" -FramesDir $framesDir -Out $Out -DelayMs $delay
Write-Host "Desktop animation -> $Out"
