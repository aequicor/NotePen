<#
.SYNOPSIS
    Record an animation on an Android target as a GIF + filmstrip by bursting screencap frames.

.DESCRIPTION
    Captures full-screen PNGs via `adb exec-out screencap -p` in a tight loop for -DurationMs, then
    encodes them with Capture-Gif.ps1. screencap tops out around 5-8 fps, so this is for reviewing
    animation shape, not frame-perfect timing. (mp4 via screenrecord is avoided: no ffmpeg to make a GIF.)

    Binary stdout is redirected through cmd.exe `>` so PNG bytes aren't mangled by PowerShell.

    Trigger the animation you want JUST BEFORE running this (or pass -PreTap to tap a point first).

.PARAMETER Serial      adb serial (required).
.PARAMETER DurationMs  Capture length in ms (default 1800).
.PARAMETER Out         Output .gif path (default out/android/<serial>/<timestamp>/anim.gif).
.PARAMETER Scale       Downscale captured frames to this max width before encoding (default 720; 0 = full).
.PARAMETER PreTap      "x,y" to tap once right before capture starts (to trigger a transition).

.EXAMPLE
    ./Capture-AndroidAnim.ps1 -Serial emulator-5554 -DurationMs 2000
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [int]$DurationMs = 1800,
    [string]$Out,
    [int]$Scale = 720,
    [string]$PreTap
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_Android.ps1"
Add-Type -AssemblyName System.Drawing
$adb = Get-Adb

if (-not $Out) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $safe = $Serial -replace '[^A-Za-z0-9_-]', '_'
    $Out = Join-Path $PSScriptRoot "out\android\$safe\$stamp\anim.gif"
}
$framesDir = Join-Path (Split-Path -Parent $Out) 'frames'
$rawDir = Join-Path $framesDir 'raw'
New-Item -ItemType Directory -Force -Path $rawDir | Out-Null

if ($PreTap) {
    $xy = $PreTap -split ','
    & $adb -s $Serial shell input tap $xy[0].Trim() $xy[1].Trim() | Out-Null
}

Write-Host "Capturing screencap frames from $Serial for ${DurationMs}ms..."
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$i = 0
while ($sw.ElapsedMilliseconds -lt $DurationMs) {
    $raw = Join-Path $rawDir ("f{0:D4}.png" -f $i)
    # cmd `>` keeps the PNG byte stream intact (no PS text/CRLF translation).
    & cmd /c "`"$adb`" -s $Serial exec-out screencap -p > `"$raw`""
    if ((Test-Path $raw) -and (Get-Item $raw).Length -gt 0) { $i++ }
}
$sw.Stop()
if ($i -eq 0) { throw "No frames captured from $Serial." }
$actualFps = [math]::Round($i / ($sw.ElapsedMilliseconds / 1000.0), 1)
Write-Host "Captured $i frame(s) in $($sw.ElapsedMilliseconds)ms (~${actualFps} fps)."

# Optional downscale (tablet frames are large); also normalizes size for the encoder.
$encodeDir = $framesDir
if ($Scale -gt 0) {
    $encodeDir = Join-Path $framesDir 'scaled'
    New-Item -ItemType Directory -Force -Path $encodeDir | Out-Null
    Get-ChildItem $rawDir -Filter '*.png' | Sort-Object Name | ForEach-Object {
        $img = [System.Drawing.Image]::FromFile($_.FullName)
        try {
            if ($img.Width -le $Scale) {
                $img.Save((Join-Path $encodeDir $_.Name), [System.Drawing.Imaging.ImageFormat]::Png)
            } else {
                $r = $Scale / [double]$img.Width
                $nw = [int]($img.Width * $r); $nh = [int]($img.Height * $r)
                $bmp = New-Object System.Drawing.Bitmap $nw, $nh
                $g = [System.Drawing.Graphics]::FromImage($bmp)
                $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $g.DrawImage($img, 0, 0, $nw, $nh)
                $g.Dispose()
                $bmp.Save((Join-Path $encodeDir $_.Name), [System.Drawing.Imaging.ImageFormat]::Png)
                $bmp.Dispose()
            }
        } finally { $img.Dispose() }
    }
}

$delayMs = [int][math]::Max(40, $sw.ElapsedMilliseconds / [math]::Max(1, $i))
& "$PSScriptRoot\Capture-Gif.ps1" -FramesDir $encodeDir -Out $Out -DelayMs $delayMs
Write-Host "Android animation -> $Out"
