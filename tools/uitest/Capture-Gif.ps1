<#
.SYNOPSIS
    Assemble a directory of PNG frames into an animated, looping GIF plus a PNG filmstrip.

.DESCRIPTION
    Dependency-free animation encoder for the NotePen UI-testing harness (no ffmpeg required).

    WPF's GifBitmapEncoder handles palette quantization + LZW compression but emits a GIF with
    zero frame delay and no loop. This script post-patches the byte stream to:
      * insert a NETSCAPE2.0 application extension (so the GIF loops), and
      * set the delay in every Graphic Control Extension (so frames are paced).

    It also emits "<Out>.filmstrip.png": all frames laid left-to-right as one image. The filmstrip
    is the reliable, diffable artifact (animated-GIF playback varies by viewer); the GIF is for review.

.PARAMETER FramesDir
    Directory containing PNG frames. Frames are ordered by file name (use zero-padded names).

.PARAMETER Out
    Output .gif path. Parent directory is created if missing.

.PARAMETER DelayMs
    Per-frame delay in milliseconds (default 80 = ~12.5 fps).

.PARAMETER LoopCount
    0 = loop forever (default). N = play N times.

.PARAMETER FilmstripFrameWidth
    Max width (px) of each frame in the filmstrip (downscaled, aspect kept). Default 240. 0 = no filmstrip.

.EXAMPLE
    ./Capture-Gif.ps1 -FramesDir out/phone/run1/frames -Out out/phone/run1/anim.gif -DelayMs 120
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$FramesDir,
    [Parameter(Mandatory = $true)][string]$Out,
    [int]$DelayMs = 80,
    [int]$LoopCount = 0,
    [int]$FilmstripFrameWidth = 240
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore
Add-Type -AssemblyName System.Drawing

$frames = Get-ChildItem -Path $FramesDir -Filter '*.png' | Sort-Object Name
if ($frames.Count -eq 0) { throw "No PNG frames found in $FramesDir" }
Write-Host "Encoding $($frames.Count) frame(s) from $FramesDir"

$outDir = Split-Path -Parent $Out
if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }

# --- 1. Build a multi-frame GIF with WPF (palette + LZW), into memory ------------------------------
$encoder = New-Object System.Windows.Media.Imaging.GifBitmapEncoder
foreach ($f in $frames) {
    $stream = [System.IO.File]::OpenRead($f.FullName)
    try {
        $decoder = New-Object System.Windows.Media.Imaging.PngBitmapDecoder(
            $stream,
            [System.Windows.Media.Imaging.BitmapCreateOptions]::PreservePixelFormat,
            [System.Windows.Media.Imaging.BitmapCacheOption]::OnLoad)
        $encoder.Frames.Add($decoder.Frames[0])
    } finally {
        $stream.Close()
    }
}
$ms = New-Object System.IO.MemoryStream
$encoder.Save($ms)
$bytes = $ms.ToArray()
$ms.Dispose()

# --- 2. Patch: NETSCAPE2.0 loop extension + per-frame delays ---------------------------------------
# GIF layout: 6B header, 7B logical screen descriptor, optional global color table, then blocks.
$packed = $bytes[10]
$gctFlag = ($packed -band 0x80) -ne 0
$gctSize = if ($gctFlag) { 3 * [math]::Pow(2, ($packed -band 0x07) + 1) } else { 0 }
$afterGct = 13 + [int]$gctSize

$loopExt = [byte[]]@(
    0x21, 0xFF, 0x0B,
    0x4E, 0x45, 0x54, 0x53, 0x43, 0x41, 0x50, 0x45, 0x32, 0x2E, 0x30, # "NETSCAPE2.0"
    0x03, 0x01,
    ($LoopCount -band 0xFF), (($LoopCount -shr 8) -band 0xFF),
    0x00)

$buf = New-Object System.Collections.Generic.List[byte]
$buf.AddRange([byte[]]($bytes[0..($afterGct - 1)]))
$buf.AddRange($loopExt)
$buf.AddRange([byte[]]($bytes[$afterGct..($bytes.Length - 1)]))
$patched = $buf.ToArray()

# Set delay (in centiseconds) in every Graphic Control Extension (0x21 0xF9 0x04 ...).
$delayCs = [int][math]::Max(1, [math]::Round($DelayMs / 10.0))
for ($i = 0; $i -lt $patched.Length - 8; $i++) {
    if ($patched[$i] -eq 0x21 -and $patched[$i + 1] -eq 0xF9 -and $patched[$i + 2] -eq 0x04) {
        $patched[$i + 4] = ($delayCs -band 0xFF)
        $patched[$i + 5] = (($delayCs -shr 8) -band 0xFF)
    }
}
[System.IO.File]::WriteAllBytes($Out, $patched)
Write-Host "Wrote GIF: $Out  ($($patched.Length) bytes, delay ${delayCs}cs, loop $LoopCount)"

# --- 3. Filmstrip PNG (reliable diff artifact) -----------------------------------------------------
if ($FilmstripFrameWidth -gt 0) {
    $imgs = $frames | ForEach-Object { [System.Drawing.Image]::FromFile($_.FullName) }
    try {
        $scale = $FilmstripFrameWidth / [double]$imgs[0].Width
        $fw = [int]($imgs[0].Width * $scale)
        $fh = [int]($imgs[0].Height * $scale)
        $strip = New-Object System.Drawing.Bitmap (($fw * $imgs.Count), $fh)
        $g = [System.Drawing.Graphics]::FromImage($strip)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        for ($k = 0; $k -lt $imgs.Count; $k++) {
            $g.DrawImage($imgs[$k], ($k * $fw), 0, $fw, $fh)
        }
        $g.Dispose()
        $stripPath = "$Out.filmstrip.png"
        $strip.Save($stripPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $strip.Dispose()
        Write-Host "Wrote filmstrip: $stripPath"
    } finally {
        $imgs | ForEach-Object { $_.Dispose() }
    }
}
